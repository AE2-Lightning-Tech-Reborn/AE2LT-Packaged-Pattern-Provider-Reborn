package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.ToLongFunction;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.compat.LegacyAeStackTags;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InputBatchCraftingAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;

/** One accepted physical batch. Every undelivered port input remains owned by PP until inserted. */
public final class PendingBatchInputs {
    public record Delivery(Identifier adapter, Identifier dimension, BlockPos pos,
                           Direction face, Identifier block, GenericStack stack) {
        public Delivery {
            pos = pos.immutable();
        }

        public Delivery withAmount(long amount) {
            return new Delivery(adapter, dimension, pos, face, block, new GenericStack(stack.what(), amount));
        }
    }

    private final List<Delivery> pending = new ArrayList<>();

    /** Only independent, single-input ports are safe to probe together without a shadow inventory. */
    public static List<Delivery> prepare(DispatchPlan plan, InputBatchCraftingAdapter adapter) {
        if (plan == null || plan.onCommit() != null || plan.targets().isEmpty()) return List.of();
        var result = new ArrayList<Delivery>();
        var ports = new HashSet<List<Object>>();
        for (var target : plan.targets()) {
            if (target.stacks().size() != 1 || target.face() == null
                    || target.stacks().getFirst().amount() <= 0) return List.of();
            var dimension = target.level().dimension().identifier();
            // Different faces can expose the same tank/inventory, so deduplicate by block position.
            if (!ports.add(List.of(dimension, target.pos()))) return List.of();
            result.add(new Delivery(adapter.batchAdapterId(), dimension, target.pos(), target.face(),
                    BuiltInRegistries.BLOCK.getKey(target.level().getBlockState(target.pos()).getBlock()),
                    target.stacks().getFirst()));
        }
        return List.copyOf(result);
    }

    public static List<Delivery> scale(List<Delivery> oneCopy, int copies) {
        var result = new ArrayList<Delivery>(oneCopy.size());
        for (var delivery : oneCopy) {
            result.add(delivery.withAmount(Math.multiplyExact(delivery.stack().amount(), copies)));
        }
        return List.copyOf(result);
    }

    public void adopt(List<Delivery> deliveries) {
        if (hasPending() || deliveries.isEmpty()) throw new IllegalStateException("batch input ownership conflict");
        pending.addAll(deliveries);
    }

    /** Update each accepted amount before visiting the next port; never discard an unvisited tail. */
    public void deliver(ToLongFunction<Delivery> sink) {
        var iterator = pending.listIterator();
        while (iterator.hasNext()) {
            var delivery = iterator.next();
            long amount = delivery.stack().amount();
            long accepted = Math.max(0, Math.min(amount, sink.applyAsLong(delivery)));
            if (accepted == amount) iterator.remove();
            else if (accepted > 0) iterator.set(delivery.withAmount(amount - accepted));
        }
    }

    public ListTag writeToNBT(HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var delivery : pending) {
            var tag = new CompoundTag();
            tag.putString("adapter", delivery.adapter().toString());
            tag.putString("dimension", delivery.dimension().toString());
            tag.putLong("pos", delivery.pos().asLong());
            tag.putInt("face", delivery.face().get3DDataValue());
            tag.putString("block", delivery.block().toString());
            tag.put("stack", LegacyAeStackTags.writeGeneric(registries, delivery.stack()));
            list.add(tag);
        }
        return list;
    }

    public void readFromNBT(ListTag list, HolderLookup.Provider registries) {
        clear();
        for (var entry : list) {
            if (!(entry instanceof CompoundTag tag)) continue;
            var stack = LegacyAeStackTags.readGeneric(registries, tag.getCompoundOrEmpty("stack"));
            if (stack == null || stack.amount() <= 0) continue;
            pending.add(new Delivery(Identifier.parse(tag.getStringOr("adapter", "minecraft:air")),
                    Identifier.parse(tag.getStringOr("dimension", "minecraft:overworld")),
                    BlockPos.of(tag.getLongOr("pos", 0L)),
                    Direction.from3DDataValue(tag.getIntOr("face", 0)),
                    Identifier.parse(tag.getStringOr("block", "minecraft:air")), stack));
        }
    }

    public List<Delivery> snapshot() { return List.copyOf(pending); }
    public boolean hasPending() { return !pending.isEmpty(); }
    public void clear() { pending.clear(); }
}
