package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.*;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** One tablet and the exact native augments; output declarations do not constrain rituals. */
public final class ArsRitualBrazierAdapter implements MultiblockAdapter {
    private static final int MAX_UNITS = 4096;
    private static final ResourceLocation BRAZIER = ResourceLocation.parse("ars_nouveau:ritual_brazier");
    private record Match(ResourceLocation ritual, AEItemKey tablet, List<ItemStack> augments,
                         Map<AEItemKey, Long> inputs) {}

    @Override public int priority() { return 100; }
    @Override public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        // BrazierRelayTile subclasses RitualBrazierTile, but cannot run a ritual itself.
        return ArsRitualAccess.isTile(be) && BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).equals(BRAZIER);
    }
    @Override public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) { return AdapterIds.ARS_RITUAL; }

    @Override public BindingResult bind(ServerLevel level, BlockPos pos, IPatternDetails pattern) {
        var be = level.getBlockEntity(pos);
        if (!recognizesMain(level, pos, be)) return null;
        try {
            var amounts = new LinkedHashMap<AEItemKey, Long>();
            for (var input : pattern.getInputs()) {
                if (input.getPossibleInputs().length != 1) return null;
                var stack = input.getPossibleInputs()[0];
                if (!(stack.what() instanceof AEItemKey key)) return null;
                amounts.merge(key, Math.multiplyExact(stack.amount(), input.getMultiplier()), Math::addExact);
            }
            var match = match(be, amounts);
            return match == null ? null : new BindingResult(match, BindingMode.REAL);
        } catch (RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(getClass(), "bind ritual", e);
            return null;
        }
    }

    private static Match match(BlockEntity be, Map<AEItemKey, Long> inputs) {
        AEItemKey tablet = null;
        ResourceLocation id = null;
        var augments = new ArrayList<ItemStack>();
        long total = 0;
        for (var entry : inputs.entrySet()) {
            long count = entry.getValue();
            if (count <= 0 || count > MAX_UNITS || (total += count) > MAX_UNITS) return null;
            var stack = entry.getKey().toStack();
            if (ArsRitualAccess.isTablet(stack)) {
                if (tablet != null || count != 1) return null;
                tablet = entry.getKey();
                id = ArsRitualAccess.tabletId(stack);
            } else for (int i = 0; i < count; i++) augments.add(stack.copy());
        }
        if (id == null || ArsRitualAccess.preview(be, id, augments) == null) return null;
        return new Match(id, tablet, List.copyOf(augments), Map.copyOf(inputs));
    }

    @Override public boolean canDispatch(ServerLevel level, BlockPos pos, Object handle) {
        var tile = level.getBlockEntity(pos);
        return handle instanceof Match && recognizesMain(level, pos, tile) && ArsRitualAccess.empty(tile);
    }

    @Override public DispatchPlan planWithBinding(ServerLevel level, BlockPos pos, IPatternDetails pattern,
            KeyCounter[] inputs, Object handle, IActionSource source) {
        if (!(handle instanceof Match match) || !canDispatch(level, pos, handle)) return null;
        var actual = new LinkedHashMap<AEItemKey, Long>();
        try {
            for (var counter : inputs) for (var entry : counter) {
                if (entry.getLongValue() == 0) continue;
                if (!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() < 0) return null;
                actual.merge(key, entry.getLongValue(), Math::addExact);
            }
            if (!actual.equals(match.inputs())) return null;
            var tile = level.getBlockEntity(pos);
            if (ArsRitualAccess.preview(tile, match.ritual(), match.augments()) == null) return null;
            var escrow = new KeyCounter();
            var stacks = match.inputs().entrySet().stream().map(e -> new GenericStack(e.getKey(), e.getValue())).toList();
            var target = new TargetSlot(level, pos, null, stacks, InsertionStrategy.CUSTOM, (stack, mode) -> {
                if (level.getBlockEntity(pos) != tile || !ArsRitualAccess.empty(tile)) return 0L;
                long accepted = Math.min(stack.amount(), match.inputs().getOrDefault(stack.what(), 0L) - escrow.get(stack.what()));
                if (accepted > 0 && mode == Actionable.MODULATE) escrow.add(stack.what(), accepted);
                return Math.max(0, accepted);
            });
            return new DispatchPlan(List.of(target), () -> {
                if (level.getBlockEntity(pos) != tile || !ArsRitualAccess.empty(tile)
                        || ArsRitualAccess.preview(tile, match.ritual(), match.augments()) == null)
                    throw new IllegalStateException("Ritual brazier changed before commit");
                // Remove ownership before invoking native mutations: an exception after a
                // mutation must never synthesize another tablet or augment in compensation.
                escrow.remove(match.tablet(), 1);
                ArsRitualAccess.call(tile, "setRitual", new Class<?>[]{ResourceLocation.class}, match.ritual());
                if (ArsRitualAccess.field(tile, "ritual") == null) throw new IllegalStateException("Ritual was not installed");
                ArsRitualCraftState.begin(tile);
                for (var augment : match.augments()) {
                    var key = AEItemKey.of(augment);
                    escrow.remove(key, 1);
                    if (!Boolean.TRUE.equals(ArsRitualAccess.call(tile, "tryBurnStack", new Class<?>[]{ItemStack.class}, augment.copy()))) {
                        escrow.add(key, 1);
                        throw new IllegalStateException("Ritual rejected a validated augment");
                    }
                }
                ArsRitualAccess.call(tile, "startRitual", new Class<?>[]{Player.class}, (Object) null);
                tile.setChanged();
            }, (receipts, sink) -> {
                for (var receipt : receipts) {
                    var stack = receipt.stack();
                    long recovered = Math.min(stack.amount(), escrow.get(stack.what()));
                    if (recovered > 0) {
                        escrow.remove(stack.what(), recovered);
                        sink.accept(new GenericStack(stack.what(), recovered));
                    }
                }
            });
        } catch (RuntimeException | LinkageError e) {
            CompatibilityDiagnostics.failure(getClass(), "plan ritual", e);
            return null;
        }
    }

    @Override public List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos, AllowedOutputFilter filter, IActionSource source) {
        var tile = level.getBlockEntity(pos);
        return recognizesMain(level, pos, tile) ? ArsRitualCraftState.drain(tile) : List.of();
    }
}
