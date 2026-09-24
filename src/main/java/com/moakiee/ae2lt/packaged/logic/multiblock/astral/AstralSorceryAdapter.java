package com.moakiee.ae2lt.packaged.logic.multiblock.astral;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Physical K=1 dispatch. Astral owns progression, environment checks and all resource consumption. */
public class AstralSorceryAdapter implements MultiblockAdapter {
    private static final String OWNED = "astralsorcery:paid_craft";
    private static final String SLOT = "astralsorcery:paid_slot_";
    private static final String INPUT_ENTITY = "ae2ltpp_astral_input";
    private static final UUID AUTOMATION = new UUID(0, 0);
    private final boolean altar;

    public AstralSorceryAdapter(boolean altar) { this.altar = altar; }

    // Distinct registry identities reuse the registry's static, allocation-free adapter list.
    public static final class AstralAltarAdapter extends AstralSorceryAdapter {
        public AstralAltarAdapter() { super(true); }
    }

    public static final class AstralInfuserAdapter extends AstralSorceryAdapter {
        public AstralInfuserAdapter() { super(false); }
    }

    @Override public int priority() { return 110; }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity tile) {
        if (tile == null || !ModList.get().isLoaded("astralsorcery")) return false;
        return AstralAccess.is(tile, altar ? "TileAltar" : "TileInfuser");
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        if (!altar) return AdapterIds.ASTRAL_INFUSER;
        return switch (BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath()) {
            case "altar_resonance" -> AdapterIds.ASTRAL_RESONANCE;
            case "altar_luminance" -> AdapterIds.ASTRAL_LUMINANCE;
            case "altar_radiance" -> AdapterIds.ASTRAL_RADIANCE;
            default -> AdapterIds.ASTRAL_ILLUMINATION;
        };
    }

    @Override
    public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        try {
            var tile = level.getBlockEntity(pos);
            if (!recognizesMain(level, pos, tile)) return null;
            var inputs = AstralRecipeBinding.patternInputs(pattern);
            var candidates = new ArrayList<AstralRecipeBinding.Candidate>();
            for (var holder : recipes(level)) {
                if (!tierMatches(tile, holder.value())) continue;
                var candidate = AstralRecipeBinding.candidate(holder, altar);
                if (candidate != null && AstralRecipeBinding.match(candidate, inputs, pattern, level) != null) {
                    candidates.add(candidate);
                }
            }
            return candidates.isEmpty() ? null : new BindingResult(new Handle(List.copyOf(candidates)), BindingMode.REAL);
        } catch (RuntimeException | LinkageError failure) { return failed("bind", failure, null); }
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) {
        try {
            if (!(handle instanceof Handle binding)) return false;
            var tile = level.getBlockEntity(pos);
            if (!recognizesMain(level, pos, tile) || AstralAccess.active(tile).isPresent()
                    || !Boolean.TRUE.equals(AstralAccess.get(tile, "hasStructure"))) return false;
            for (var candidate : binding.candidates()) {
                if (current(level, candidate) && environment(level, tile, candidate)
                        && emptyLayout(level, tile, candidate)) return true;
            }
            return false;
        } catch (RuntimeException | LinkageError failure) { return failed("canDispatch", failure, false); }
    }

    @Override
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) {
        return planWithBinding(level, pos, pattern, inputs, handle, source, AdapterPersistentScope.NOOP);
    }

    @Override
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source, AdapterPersistentScope scope) {
        try {
            if (scope.hasFlag(pos, OWNED) || !canDispatch(level, pos, handle)) return null;
            var tile = level.getBlockEntity(pos);
            for (var candidate : ((Handle) handle).candidates()) {
                if (!current(level, candidate) || !environment(level, tile, candidate)
                        || !emptyLayout(level, tile, candidate)) continue;
                var match = AstralRecipeBinding.match(candidate, inputs, pattern, level);
                if (match == null) continue;
                var targets = new ArrayList<TargetSlot>();
                for (int i = 0; i < match.keys().size(); i++) {
                    int slot = candidate.slots().get(i).gridIndex();
                    var key = match.keys().get(i);
                    var stack = new GenericStack(key, 1);
                    targets.add(new TargetSlot(level, slotPos(pos, slot), null, List.of(stack),
                            InsertionStrategy.CUSTOM, (offered, mode) -> {
                        if (!offered.equals(stack) || level.getBlockEntity(pos) != tile
                                || AstralAccess.active(tile).isPresent()) return 0L;
                        if (slot < 0) {
                            return mode == Actionable.SIMULATE || DroppedItemDispatch.spawnInput(level,
                                    Vec3.atCenterOf(pos.above()), key.toStack(1), INPUT_ENTITY) ? 1L : 0L;
                        }
                        var inventory = slotInventory(level, pos, slot);
                        int index = slot < 9 ? slot : 0;
                        if (inventory == null || !inventory.getStackInSlot(index).isEmpty()) return 0L;
                        var remainder = inventory.insertItem(index, key.toStack(1), mode == Actionable.SIMULATE);
                        return 1L - remainder.getCount();
                    }));
                }
                return new DispatchPlan(List.copyOf(targets), () -> {
                    clearOwnership(scope, pos);
                    scope.setFlag(pos, OWNED);
                    for (var slot : candidate.slots()) {
                        if (slot.gridIndex() >= 0) scope.setFlag(pos, SLOT + slot.gridIndex());
                    }
                    // All inputs have transferred to the machine. Starting must never mint an output or refund them.
                    if (level.getBlockEntity(pos) != tile || AstralAccess.active(tile).isPresent()) return;
                    if (altar) {
                        UUID owner = source == null ? AUTOMATION : source.player().map(p -> p.getUUID()).orElse(AUTOMATION);
                        AstralAccess.call(tile, "startCrafting", new Class<?>[]{RecipeHolder.class, UUID.class},
                                candidate.holder(), owner);
                    } else {
                        AstralAccess.call(tile, "startCrafting", new Class<?>[]{Level.class, RecipeHolder.class},
                                level, candidate.holder());
                    }
                });
            }
            return null;
        } catch (RuntimeException | LinkageError failure) { return failed("plan", failure, null); }
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos, AllowedOutputFilter filter, IActionSource source) {
        return extractOutputs(level, pos, filter, source, AdapterPersistentScope.NOOP);
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos, AllowedOutputFilter filter,
            IActionSource source, AdapterPersistentScope scope) {
        var outputs = new ArrayList<GenericStack>();
        try {
            var tile = level.getBlockEntity(pos);
            if (!scope.hasFlag(pos, OWNED) || !recognizesMain(level, pos, tile)
                    || AstralAccess.active(tile).isPresent()) return List.of();
            outputs.addAll(DroppedItemDispatch.collectOutputs(level,
                    // Higher altars can push their native drops sideways out of the central block.
                    // Use the altar's seven-block capture footprint, still gated by paid ownership.
                    new AABB(-3, 0, -3, 4, 3, 4).move(pos), filter, entity ->
                            !entity.getPersistentData().getBoolean(INPUT_ENTITY)
                            && entity.getClass() == ItemEntity.class));
            if (outputs.isEmpty()) {
                // An interrupted recipe retains its physical inputs for manual recovery/restart.
                // If the player emptied it, release ownership instead of scavenging the next manual craft.
                if (allEmpty(level, pos) && level.getEntitiesOfClass(ItemEntity.class,
                        new AABB(pos).inflate(4, 2, 4), e -> e.getPersistentData().getBoolean(INPUT_ENTITY)).isEmpty()) {
                    clearOwnership(scope, pos);
                }
                return List.of();
            }
            // Native crafting leaves drained containers/crafting remainders in the paid slots.
            for (int slot = 0; slot < (altar ? 34 : 1); slot++) {
                if (!scope.hasFlag(pos, SLOT + slot)) continue;
                var inventory = slotInventory(level, pos, slot);
                int index = slot < 9 ? slot : 0;
                if (inventory != null) {
                    var remainder = inventory.extractItem(index, Integer.MAX_VALUE, false);
                    if (!remainder.isEmpty()) outputs.add(new GenericStack(AEItemKey.of(remainder), remainder.getCount()));
                }
            }
            clearOwnership(scope, pos);
            return List.copyOf(outputs);
        } catch (RuntimeException | LinkageError failure) { return failed("extract", failure, List.copyOf(outputs)); }
    }

    private void clearOwnership(AdapterPersistentScope scope, BlockPos pos) {
        scope.clearFlag(pos, OWNED);
        for (int slot = 0; slot < (altar ? 34 : 1); slot++) scope.clearFlag(pos, SLOT + slot);
    }

    private boolean current(ServerLevel level, AstralRecipeBinding.Candidate candidate) {
        return level.getRecipeManager().byKey(candidate.holder().id()).orElse(null) == candidate.holder();
    }

    private boolean tierMatches(BlockEntity tile, Object recipe) {
        if (!altar) return true;
        var tier = AstralAccess.get(AstralAccess.data(tile), "getAltarType");
        var required = AstralAccess.get(recipe, "getRequiredType");
        return Boolean.TRUE.equals(AstralAccess.call(tier, "isThisLaterOrEqual", new Class<?>[]{tier.getClass()}, required));
    }

    private boolean environment(ServerLevel level, BlockEntity tile, AstralRecipeBinding.Candidate candidate) {
        var recipe = candidate.holder().value();
        if (!tierMatches(tile, recipe)) return false;
        if (altar) {
            if (Boolean.TRUE.equals(AstralAccess.get(recipe, "isOnlyNight"))
                    && !Boolean.TRUE.equals(AstralAccess.call(AstralAccess.type("util.level.DayTimeHelper"),
                            "isNight", new Class<?>[]{Level.class}, level))) return false;
            var focus = (Optional<?>) AstralAccess.get(recipe, "getFocusConstellation");
            return focus.isEmpty() || focus.equals(AstralAccess.get(AstralAccess.data(tile), "getFocusedConstellation"));
        }
        Object fluid = AstralAccess.get(recipe, "getFluidInput");
        for (var offset : AstralAccess.liquidOffsets()) {
            var liquidPos = tile.getBlockPos().offset(offset);
            if (!level.isLoaded(liquidPos) || level.getFluidState(liquidPos).getType() != fluid) return false;
        }
        return true;
    }

    private boolean emptyLayout(ServerLevel level, BlockEntity tile, AstralRecipeBinding.Candidate candidate) {
        if (!allEmpty(level, tile.getBlockPos())) return false;
        for (var slot : candidate.slots()) {
            if (slot.gridIndex() >= 9 && slotInventory(level, tile.getBlockPos(), slot.gridIndex()) == null) return false;
        }
        return true;
    }

    private boolean allEmpty(ServerLevel level, BlockPos pos) {
        var inventory = AstralAccess.inventory(level.getBlockEntity(pos), altar);
        for (int i = 0; i < inventory.getSlots(); i++) if (!inventory.getStackInSlot(i).isEmpty()) return false;
        if (altar) for (var offset : AstralAccess.relayOffsets().values()) {
            var relayPos = pos.offset(offset);
            if (!level.isLoaded(relayPos)) return false;
            var relay = level.getBlockEntity(relayPos);
            if (AstralAccess.is(relay, "TileFocusRelay")
                    && !AstralAccess.inventory(relay, false).getStackInSlot(0).isEmpty()) return false;
        }
        return true;
    }

    private BlockPos slotPos(BlockPos pos, int slot) {
        return slot < 9 ? pos : pos.offset(AstralAccess.relayOffsets().get(slot - 9));
    }

    private IItemHandlerModifiable slotInventory(ServerLevel level, BlockPos pos, int slot) {
        var target = slotPos(pos, slot);
        if (!level.isLoaded(target)) return null;
        var tile = level.getBlockEntity(target);
        if (slot >= 9) return AstralAccess.is(tile, "TileFocusRelay") ? AstralAccess.inventory(tile, false) : null;
        return recognizesMain(level, pos, tile) ? AstralAccess.inventory(tile, altar) : null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<RecipeHolder<?>> recipes(ServerLevel level) {
        var id = ResourceLocation.fromNamespaceAndPath("astralsorcery", altar ? "altar_crafting" : "infusion");
        return BuiltInRegistries.RECIPE_TYPE.getOptional(id)
                .map(type -> (List<RecipeHolder<?>>) (List<?>) level.getRecipeManager().getAllRecipesFor((RecipeType) type))
                .orElse(List.of());
    }

    private static <T> T failed(String operation, Throwable failure, T result) {
        CompatibilityDiagnostics.failure(AstralSorceryAdapter.class, operation, failure);
        return result;
    }

    private record Handle(List<AstralRecipeBinding.Candidate> candidates) {}
}
