package com.moakiee.ae2lt.packaged.logic.resonant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import appeng.capabilities.Capabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.parts.automation.StackWorldBehaviors;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingBatchInputs;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Pure routing plus native capability IO. The provider owns every accepted remainder. */
public final class ResonantDispatch {
    public record Anchor(ServerLevel level, BlockPos pos, Direction face) {
        public Anchor { pos = pos.immutable(); }
    }
    private record Port(BlockPos pos, Direction face, AEKey key) {}

    private static <T> T capability(ServerLevel level, net.minecraft.core.BlockPos pos,
                                    net.minecraftforge.common.capabilities.Capability<T> capability,
                                    net.minecraft.core.Direction side) {
        var target = level.getBlockEntity(pos);
        return target == null || target.isRemoved() ? null : target.getCapability(capability, side).orElse(null);
    }

    private ResonantDispatch() {}

    @Nullable
    public static List<PendingBatchInputs.Delivery> plan(ResonantLayout layout, ResonantPatternAccess.Data data,
                                                        KeyCounter[] inputs, Anchor anchor, BlockPos providerPos) {
        if (data == null || inputs == null || !validAnchor(layout, anchor)) return null;
        var level = anchor.level();
        var orientation = ResonantLayout.forward(level.getBlockState(anchor.pos()), anchor.face());
        var amounts = new LinkedHashMap<Port, Long>();
        var blocks = new HashMap<Port, net.minecraft.resources.ResourceLocation>();
        var remaining = new HashMap<AEKey, Long>();
        try {
            for (var counter : inputs) {
                if (counter == null) return null;
                for (var entry : counter) {
                    if (entry.getLongValue() <= 0) return null;
                    remaining.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
                }
            }
            for (int index = 0; index < data.inputs().size(); index++) {
                var stack = data.inputs().get(index);
                if (stack == null) continue;
                if (stack.amount() <= 0 || stack.what() == null) return null;
                long available = remaining.getOrDefault(stack.what(), 0L);
                if (available < stack.amount()) return null;
                if (available == stack.amount()) remaining.remove(stack.what());
                else remaining.put(stack.what(), available - stack.amount());

                var absolute = data.targets().get(index);
                ResonantLayout.Target target;
                if (absolute.isPresent()) {
                    var marked = absolute.get();
                    // A relative layout has one source dimension; never reinterpret a cross-dimension mark.
                    if (!marked.pos().dimension().equals(layout.source().dimension())) return null;
                    if (!ResonantRange.contains(layout.source().pos(), marked.pos().pos())) return null;
                    target = new ResonantLayout.Target(marked.pos().pos().subtract(layout.source().pos()),
                            marked.face(), Optional.empty());
                } else {
                    target = layout.target(index).orElse(new ResonantLayout.Target(BlockPos.ZERO,
                            layout.anchorFace(), Optional.of(layout.anchorBlock())));
                }
                if (!ResonantRange.contains(BlockPos.ZERO, target.offset())) return null;
                target = target.rotated(layout.forward(), orientation);
                var pos = anchor.pos().offset(target.offset());
                if (pos.equals(providerPos) || !validTarget(level, pos, target)) return null;
                var port = new Port(pos, target.face(), stack.what());
                amounts.merge(port, stack.amount(), Math::addExact);
                blocks.put(port, BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()));
            }
        } catch (ArithmeticException overflow) { return null; }
        if (!remaining.isEmpty() || amounts.isEmpty()) return null;
        var result = new ArrayList<PendingBatchInputs.Delivery>();
        amounts.forEach((port, amount) -> result.add(new PendingBatchInputs.Delivery(AdapterIds.RESONANT,
                level.dimension().location(), port.pos(), port.face(), blocks.get(port),
                new GenericStack(port.key(), amount))));
        return List.copyOf(result);
    }

    public static boolean canInsertAll(ServerLevel level, List<PendingBatchInputs.Delivery> deliveries,
                                       IActionSource source, boolean blocking, Set<AEKey> patternInputs) {
        var reservations = new ResonantReservations();
        for (var delivery : deliveries) {
            var target = resolve(level, delivery, source);
            if (target == null || (blocking && target.containsPatternInput(patternInputs))) return false;
            if (target.insert(delivery.stack().what(), delivery.stack().amount(), Actionable.SIMULATE)
                    < delivery.stack().amount()) return false;
            if (!reservations.reserve(level, delivery)) return false;
        }
        return true;
    }

    public static long insert(ServerLevel level, PendingBatchInputs.Delivery delivery, IActionSource source) {
        var target = resolve(level, delivery, source);
        return target == null ? 0 : target.insert(delivery.stack().what(), delivery.stack().amount(), Actionable.MODULATE);
    }

    @Nullable
    private static PatternProviderTarget resolve(ServerLevel level, PendingBatchInputs.Delivery delivery,
                                                 IActionSource source) {
        if (!level.dimension().location().equals(delivery.dimension()) || !level.isLoaded(delivery.pos())
                || !BuiltInRegistries.BLOCK.getKey(level.getBlockState(delivery.pos()).getBlock()).equals(delivery.block())) {
            return null;
        }
        return PatternProviderTarget.get(level, delivery.pos(), level.getBlockEntity(delivery.pos()), delivery.face(), source);
    }

    public static boolean validAnchor(ResonantLayout layout, Anchor anchor) {
        return anchor.level().isLoaded(anchor.pos()) && layout.matchesAnchor(anchor.level().getBlockState(anchor.pos()));
    }

    private static boolean validTarget(ServerLevel level, BlockPos pos, ResonantLayout.Target target) {
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.isLoaded(pos)) return false;
        var state = level.getBlockState(pos);
        return !state.isAir() && target.block().map(id -> id.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock())))
                .orElse(true);
    }

    /** New layouts pull only from the main machine; pre-existing output layouts keep their routes. */
    public static void extractOutputs(ResonantLayout layout, Anchor anchor, BlockPos providerPos,
                                      AllowedOutputFilter filter, IActionSource source, Consumer<GenericStack> retain) {
        if (!validAnchor(layout, anchor) || filter.isEmpty()) return;
        var level = anchor.level();
        var orientation = ResonantLayout.forward(level.getBlockState(anchor.pos()), anchor.face());
        boolean legacyOutputs = layout.bindings().stream().anyMatch(b -> b.slot() >= ResonantLayout.INPUTS);
        if (!legacyOutputs) {
            if (!anchor.pos().equals(providerPos)) extractAt(level, anchor.pos(), anchor.face(), filter, source, retain);
            return;
        }
        var seen = new HashSet<List<Object>>();
        for (var binding : layout.bindings()) {
            if (binding.slot() < ResonantLayout.INPUTS) continue;
            var target = binding.target().rotated(layout.forward(), orientation);
            var pos = anchor.pos().offset(target.offset());
            if (pos.equals(providerPos) || !validTarget(level, pos, target)
                    || !seen.add(List.of(pos, target.face()))) continue;
            extractAt(level, pos, target.face(), filter, source, retain);
        }
    }

    private static void extractAt(ServerLevel level, BlockPos pos, Direction face,
                                  AllowedOutputFilter filter, IActionSource source, Consumer<GenericStack> retain) {
        var storage = capability(level, pos, Capabilities.STORAGE, face);
        if (storage != null) extract(storage, filter, source, retain);
        else {
            for (var strategy : StackWorldBehaviors.createExternalStorageStrategies(level, pos, face).values()) {
                var wrapper = strategy.createWrapper(false, () -> {});
                if (wrapper != null) extract(wrapper, filter, source, retain);
            }
        }
    }

    private static void extract(MEStorage storage, AllowedOutputFilter filter, IActionSource source,
                                Consumer<GenericStack> retain) {
        for (var entry : storage.getAvailableStacks()) {
            var key = entry.getKey();
            if (entry.getLongValue() <= 0 || !filter.matches(key)) continue;
            long extracted = storage.extract(key, entry.getLongValue(), Actionable.MODULATE, source);
            if (extracted > 0) retain.accept(new GenericStack(key, extracted));
        }
    }
}
