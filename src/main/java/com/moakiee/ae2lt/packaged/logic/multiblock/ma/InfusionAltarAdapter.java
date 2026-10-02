package com.moakiee.ae2lt.packaged.logic.multiblock.ma;

import net.neoforged.neoforge.items.IItemHandler;
import java.util.function.Consumer;
import com.moakiee.ae2lt.packaged.logic.multiblock.AcceptedInsertion;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime adapter for Mystical Agriculture's Infusion Altar (seed crafting).
 * Pure reflection: MA is not a compile dependency.
 *
 * Layout: 1 altar + 8 pedestals at fixed offsets
 *   (+/-3,0,0), (0,0,+/-3), (+/-2,0,+/-2).
 * Two sets need at least 7 blocks apart to both assemble, so the hardcoded
 * offsets cannot pull from a neighboring set.
 */
public final class InfusionAltarAdapter implements MultiblockAdapter {

    private static final String MOD_ID = "mysticalagriculture";
    private static final ResourceLocation ALTAR_BLOCK = maId("infusion_altar");
    private static final ResourceLocation PEDESTAL_BLOCK = maId("infusion_pedestal");
    private static final ResourceLocation RECIPE_TYPE_ID = maId("infusion");
    private static final int MAX_INPUT_UNITS = 128;

    private static final BlockPos[] PEDESTAL_OFFSETS = {
            new BlockPos(3, 0, 0),
            new BlockPos(0, 0, 3),
            new BlockPos(-3, 0, 0),
            new BlockPos(0, 0, -3),
            new BlockPos(2, 0, 2),
            new BlockPos(2, 0, -2),
            new BlockPos(-2, 0, 2),
            new BlockPos(-2, 0, -2),
    };

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null
                && isMaLoaded()
                && blockId(be.getBlockState()).equals(ALTAR_BLOCK)
                && InfusionAltarAccess.isInfusionAltar(be);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return com.moakiee.ae2lt.packaged.item.AdapterIds.MA_INFUSION;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return null;
        }
        if (!hasSingleItemOutput(pattern)) {
            return null;
        }
        var recipe = findCandidateRecipe(level, pattern);
        if (recipe == null) {
            return null;
        }
        return new BindingResult(new InfusionBindHandle(recipe), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        if (!(handle instanceof InfusionBindHandle)) {
            return false;
        }
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return false;
        }
        if (InfusionAltarAccess.getProgress(be) > 0) {
            return false;
        }
        var altarInv = CucumberInventoryAccess.get(be);
        if (altarInv == null || altarInv.getSlots() < 2) {
            return false;
        }
        if (!altarInv.getStackInSlot(0).isEmpty() || !altarInv.getStackInSlot(1).isEmpty()) {
            return false;
        }
        return findEmptyPedestals(level, mainPos) != null;
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        if (!(handle instanceof InfusionBindHandle bind)) {
            return null;
        }
        var pedestals = findEmptyPedestals(level, mainPos);
        if (pedestals == null) {
            return null;
        }

        var units = expandInputUnits(inputs);
        if (units == null || units.size() != 9) {
            return null;
        }

        var match = assignInputsToRecipe(bind.recipe(), units, level);
        if (match == null) {
            return null;
        }

        var targets = new ArrayList<TargetSlot>(9);
        targets.add(new TargetSlot(
                level, mainPos, null,
                List.of(match.altar().toGenericStack()),
                InsertionStrategy.CUSTOM,
                altarInserter(level, mainPos, match.altar())));

        for (int i = 0; i < match.pedestalUnits().size(); i++) {
            var unit = match.pedestalUnits().get(i);
            var pedestalPos = pedestals.get(i);
            targets.add(new TargetSlot(
                    level, pedestalPos, null,
                    List.of(unit.toGenericStack()),
                    InsertionStrategy.CUSTOM,
                    pedestalInserter(level, pedestalPos, unit)));
        }

        return new DispatchPlan(List.copyOf(targets), () -> InfusionAltarAccess.activateOrThrow(level, mainPos),
                (accepted, recovered) -> recoverUnstartedDispatch(level, mainPos, targets, accepted, recovered));
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return List.of();
        }
        if (InfusionAltarAccess.getProgress(be) > 0) {
            return List.of();
        }

        var handler = CucumberInventoryAccess.get(be);
        if (handler == null || handler.getSlots() < 2) {
            return List.of();
        }

        var stack = handler.getStackInSlot(1);
        if (stack.isEmpty()) {
            return List.of();
        }

        var key = AEItemKey.of(stack);
        if (!allowsAutoReturn(level, mainPos, filter, key)) {
            return List.of();
        }

        var extracted = handler.extractItem(1, stack.getCount(), false);
        if (extracted.isEmpty()) {
            return List.of();
        }
        return List.of(new GenericStack(AEItemKey.of(extracted), extracted.getCount()));
    }

    /**
     * Bind-time recipe search: matches solely on pattern.outputs and recipe
     * shape (existence of an altar ingredient + sensible ingredient list).
     */
    @Nullable
    private static Object findCandidateRecipe(ServerLevel level, IPatternDetails pattern) {
        for (var holder : recipes(level)) {
            var recipe = holder.value();
            var result = resultItem(recipe, level);
            if (result == null || result.isEmpty() || !outputMatches(pattern, result)) {
                continue;
            }
            if (InfusionAltarAccess.getAltarIngredient(recipe) == null) {
                continue;
            }
            return recipe;
        }
        return null;
    }

    /**
     * Per-push input assignment against the recipe locked in by {@link #bind}.
     */
    @Nullable
    private static RecipeMatch assignInputsToRecipe(Object recipe, List<PlannedUnit> units,
                                                    ServerLevel level) {
        var altarIngredient = InfusionAltarAccess.getAltarIngredient(recipe);
        if (altarIngredient == null) {
            return null;
        }

        for (int altarIdx = 0; altarIdx < units.size(); altarIdx++) {
            var altarUnit = units.get(altarIdx);
            if (!altarIngredient.test(altarUnit.stack())) {
                continue;
            }

            var pedestalUnits = new ArrayList<PlannedUnit>(units.size() - 1);
            for (int i = 0; i < units.size(); i++) {
                if (i != altarIdx) {
                    pedestalUnits.add(units.get(i));
                }
            }

            var craftingInput = buildCraftingInput(altarUnit, pedestalUnits);
            if (recipeMatches(recipe, craftingInput, level)) {
                return new RecipeMatch(altarUnit, List.copyOf(pedestalUnits));
            }
        }
        return null;
    }

    private static CraftingInput buildCraftingInput(PlannedUnit altar, List<PlannedUnit> pedestals) {
        var stacks = NonNullList.withSize(9, ItemStack.EMPTY);
        stacks.set(0, altar.stack());
        for (int i = 0; i < pedestals.size() && i + 1 < 9; i++) {
            stacks.set(i + 1, pedestals.get(i).stack());
        }
        return CraftingInput.of(3, 3, stacks);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean recipeMatches(Object recipe, CraftingInput input, ServerLevel level) {
        try {
            return recipe instanceof Recipe<?> r && ((Recipe) r).matches(input, level);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    private static ItemStack resultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> r)) {
            return null;
        }
        try {
            return r.getResultItem(level.registryAccess());
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static List<BlockPos> findEmptyPedestals(ServerLevel level, BlockPos mainPos) {
        var positions = new ArrayList<BlockPos>(PEDESTAL_OFFSETS.length);
        for (var offset : PEDESTAL_OFFSETS) {
            var pos = mainPos.offset(offset.getX(), offset.getY(), offset.getZ());
            if (!level.isLoaded(pos)) {
                return null;
            }
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(PEDESTAL_BLOCK)
                    || !InfusionAltarAccess.isInfusionPedestal(be)) {
                return null;
            }
            var handler = CucumberInventoryAccess.get(be);
            if (handler == null || handler.getSlots() < 1) {
                return null;
            }
            if (!handler.getStackInSlot(0).isEmpty()) {
                return null;
            }
            positions.add(pos);
        }
        return positions;
    }

    private static BiFunction<GenericStack, Actionable, Long> altarInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (!matchesPlannedStack(stack, unit)) {
                return 0L;
            }
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(ALTAR_BLOCK)
                    || !InfusionAltarAccess.isInfusionAltar(be)) {
                return 0L;
            }
            var handler = CucumberInventoryAccess.get(be);
            if (!(handler instanceof IItemHandlerModifiable modifiable)
                    || modifiable.getSlots() < 2
                    || !modifiable.getStackInSlot(0).isEmpty()
                    || !modifiable.getStackInSlot(1).isEmpty()) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                modifiable.setStackInSlot(0, unit.stack());
            }
            return 1L;
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> pedestalInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (!matchesPlannedStack(stack, unit)) {
                return 0L;
            }
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(PEDESTAL_BLOCK)
                    || !InfusionAltarAccess.isInfusionPedestal(be)) {
                return 0L;
            }
            var handler = CucumberInventoryAccess.get(be);
            if (!(handler instanceof IItemHandlerModifiable modifiable)
                    || modifiable.getSlots() < 1
                    || !modifiable.getStackInSlot(0).isEmpty()) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                modifiable.setStackInSlot(0, unit.stack());
            }
            return 1L;
        };
    }

    @Nullable
    private static List<PlannedUnit> expandInputUnits(KeyCounter[] inputs) {
        var units = new ArrayList<PlannedUnit>();
        long total = 0;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    return null;
                }
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                total += amount;
                if (total > MAX_INPUT_UNITS) {
                    return null;
                }
                for (long i = 0; i < amount; i++) {
                    units.add(new PlannedUnit(itemKey));
                }
            }
        }
        return units;
    }

    private static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.size() == 1 && outputs.getFirst().what() instanceof AEItemKey;
    }

    private static boolean outputMatches(IPatternDetails pattern, ItemStack result) {
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1) {
            return false;
        }
        var expected = outputs.getFirst();
        if (!(expected.what() instanceof AEItemKey expectedKey) || expected.amount() != result.getCount()) {
            return false;
        }
        var actual = AEItemKey.of(result);
        return OverloadPatternSemantics.isIdOnlyOutput(pattern, 0)
                ? expectedKey.dropSecondary().equals(actual.dropSecondary())
                : expectedKey.equals(actual);
    }

    private static boolean matchesPlannedStack(GenericStack stack, PlannedUnit unit) {
        return stack.amount() == 1 && unit.key().equals(stack.what());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipes(ServerLevel level) {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(RECIPE_TYPE_ID)
                .map(type -> (List<RecipeHolder<?>>) (List<?>) level.getRecipeManager()
                        .getAllRecipesFor((RecipeType) type))
                .orElse(List.of());
    }

    private static boolean isMaLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static ResourceLocation maId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private record PlannedUnit(AEItemKey key) {
        PlannedUnit {
            Objects.requireNonNull(key, "key");
        }

        ItemStack stack() {
            return key.toStack(1);
        }

        GenericStack toGenericStack() {
            return new GenericStack(key, 1);
        }
    }

    private record RecipeMatch(PlannedUnit altar, List<PlannedUnit> pedestalUnits) {
    }

    /** Opaque binding handle returned from {@link #bind}. */
    private record InfusionBindHandle(Object recipe) {
    }

    private static void recoverUnstartedDispatch(
            ServerLevel level,
            BlockPos mainPos,
            List<TargetSlot> targets,
            List<AcceptedInsertion> acceptedInsertions,
            Consumer<GenericStack> recovered) {
        var altar = level.getBlockEntity(mainPos);
        if (altar == null || !InfusionAltarAccess.isDefinitelyInactive(altar)) {
            return;
        }
        for (var accepted : acceptedInsertions) {
            int targetIndex = targets.indexOf(accepted.target());
            if (targetIndex < 0) {
                continue;
            }
            var be = level.getBlockEntity(accepted.target().pos());
            boolean expectedTarget = be != null && (targetIndex == 0
                    ? blockId(be.getBlockState()).equals(ALTAR_BLOCK)
                            && InfusionAltarAccess.isInfusionAltar(be)
                    : blockId(be.getBlockState()).equals(PEDESTAL_BLOCK)
                            && InfusionAltarAccess.isInfusionPedestal(be));
            if (!expectedTarget) {
                continue;
            }
            var stack = recoverExactInsertion(
                    CucumberInventoryAccess.get(be), 0, accepted.stack());
            if (stack != null) {
                recovered.accept(stack);
            }
        }
    }

    @Nullable
    private static GenericStack recoverExactInsertion(
            @Nullable IItemHandler inventory, int slot, GenericStack accepted) {
        if (inventory == null
                || slot < 0
                || slot >= inventory.getSlots()
                || !(accepted.what() instanceof AEItemKey expectedKey)
                || accepted.amount() <= 0
                || accepted.amount() > Integer.MAX_VALUE) {
            return null;
        }
        var current = inventory.getStackInSlot(slot);
        var expected = expectedKey.toStack((int) accepted.amount());
        if (current.isEmpty()
                || current.getCount() != expected.getCount()
                || !ItemStack.isSameItemSameComponents(current, expected)) {
            return null;
        }
        var simulated = inventory.extractItem(slot, current.getCount(), true);
        if (simulated.isEmpty()
                || simulated.getCount() != current.getCount()
                || !ItemStack.isSameItemSameComponents(simulated, current)) {
            return null;
        }
        var extracted = inventory.extractItem(slot, current.getCount(), false);
        if (extracted.isEmpty() || !ItemStack.isSameItemSameComponents(extracted, current)) {
            return null;
        }
        return new GenericStack(AEItemKey.of(extracted), extracted.getCount());
    }
}
