package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.ToLongFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InputBatchCraftingAdapter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Provider-owned physical inputs that were accepted but not fully inserted. */
public final class PendingBatchInputs {
    private static final Logger LOG = LoggerFactory.getLogger(PendingBatchInputs.class);
    private static final String TAG_PENDING = "ae2ltpp_pending_batch_inputs";
    private static final String TAG_UNCERTAIN = TAG_PENDING + "_uncertain";
    private static final String TAG_OWNER = TAG_PENDING + "_owner";

    public record Ownership(ResourceLocation adapter, ResourceLocation dimension,
                            BlockPos pos, ResourceLocation block) {
        public Ownership { pos = pos.immutable(); }
    }

    public record Delivery(ResourceLocation adapter, ResourceLocation dimension, BlockPos pos,
                           Direction face, ResourceLocation block, GenericStack stack) {
        public Delivery {
            pos = pos.immutable();
        }

        public Delivery withAmount(long amount) {
            return new Delivery(adapter, dimension, pos, face, block,
                    new GenericStack(stack.what(), amount));
        }
    }

    private final List<Delivery> pending = new ArrayList<>();
    private final List<Delivery> uncertain = new ArrayList<>();
    private Ownership ownership;

    /** Every paid input must occur in the port plan exactly once. */
    public static boolean matchesInputs(List<Delivery> deliveries, KeyCounter[] inputs) {
        if (inputs == null || deliveries.isEmpty()) return false;
        var expected = new java.util.HashMap<appeng.api.stacks.AEKey, Long>();
        var actual = new java.util.HashMap<appeng.api.stacks.AEKey, Long>();
        try {
            for (var counter : inputs) {
                if (counter == null) return false;
                for (var entry : counter) {
                    if (entry.getLongValue() <= 0) return false;
                    expected.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
                }
            }
            for (var delivery : deliveries) actual.merge(delivery.stack().what(), delivery.stack().amount(), Math::addExact);
            return actual.equals(expected);
        } catch (ArithmeticException overflow) { return false; }
    }

    /** Only one independent input per distinct port may enter a scaled batch. */
    public static List<Delivery> prepare(DispatchPlan plan, InputBatchCraftingAdapter adapter) {
        if (plan == null || plan.onCommit() != null || plan.targets() == null
                || plan.targets().isEmpty()) return List.of();
        var result = new ArrayList<Delivery>();
        var ports = new HashSet<String>();
        for (var target : plan.targets()) {
            if (target == null || target.stacks() == null || target.stacks().size() != 1
                    || target.face() == null || target.stacks().get(0).amount() <= 0) return List.of();
            var dimension = target.level().dimension().location();
            var portKey = dimension + "@" + target.pos().asLong();
            if (!ports.add(portKey)) return List.of();
            result.add(new Delivery(adapter.batchAdapterId(), dimension, target.pos(), target.face(),
                    BuiltInRegistries.BLOCK.getKey(target.level().getBlockState(target.pos()).getBlock()),
                    target.stacks().get(0)));
        }
        return List.copyOf(result);
    }

    public static List<Delivery> scale(List<Delivery> oneCopy, long copies) {
        if (copies <= 0 || copies > Integer.MAX_VALUE) return List.of();
        var result = new ArrayList<Delivery>(oneCopy.size());
        for (var delivery : oneCopy) {
            result.add(delivery.withAmount(Math.multiplyExact(delivery.stack().amount(), copies)));
        }
        return List.copyOf(result);
    }

    public void adopt(List<Delivery> deliveries) {
        adopt(deliveries, null);
    }

    public void adopt(List<Delivery> deliveries, Ownership owner) {
        if (hasPending() || deliveries.isEmpty()) {
            throw new IllegalStateException("batch input ownership conflict");
        }
        pending.addAll(deliveries);
        ownership = owner;
    }

    /** Modulates each accepted port and retains only the uninserted remainder. */
    public void deliver(ToLongFunction<Delivery> sink) {
        var iterator = pending.listIterator();
        while (iterator.hasNext()) {
            var delivery = iterator.next();
            long amount = delivery.stack().amount();
            long accepted;
            try {
                accepted = sink.applyAsLong(delivery);
                if (accepted < 0 || accepted > amount) {
                    throw new IllegalStateException("invalid batch insertion receipt: " + accepted);
                }
            } catch (RuntimeException | LinkageError failure) {
                // The attempted call may already have reached the machine. Do not replay it.
                iterator.remove();
                uncertain.add(delivery);
                LOG.error("Batch input ownership is unknown at {} for {} x{}; retained without retry or refund",
                        delivery.pos(), delivery.stack().what(), amount, failure);
                throw failure;
            }
            if (accepted == amount) iterator.remove();
            else if (accepted > 0) iterator.set(delivery.withAmount(amount - accepted));
        }
        if (!hasPending()) ownership = null;
    }

    public void writeToNBT(CompoundTag parent) {
        parent.remove(TAG_PENDING);
        parent.remove(TAG_UNCERTAIN);
        parent.remove(TAG_OWNER);
        if (!pending.isEmpty()) parent.put(TAG_PENDING, writeDeliveries(pending));
        if (!uncertain.isEmpty()) parent.put(TAG_UNCERTAIN, writeDeliveries(uncertain));
        if (ownership != null) {
            var owner = new CompoundTag();
            owner.putString("adapter", ownership.adapter().toString());
            owner.putString("dimension", ownership.dimension().toString());
            owner.putLong("pos", ownership.pos().asLong());
            owner.putString("block", ownership.block().toString());
            parent.put(TAG_OWNER, owner);
        }
    }

    private static ListTag writeDeliveries(List<Delivery> deliveries) {
        var list = new ListTag();
        for (var delivery : deliveries) {
            var tag = new CompoundTag();
            tag.putString("adapter", delivery.adapter().toString());
            tag.putString("dimension", delivery.dimension().toString());
            tag.putLong("pos", delivery.pos().asLong());
            tag.putInt("face", delivery.face().get3DDataValue());
            tag.putString("block", delivery.block().toString());
            tag.put("stack", writeStack(delivery.stack()));
            list.add(tag);
        }
        return list;
    }

    public void readFromNBT(CompoundTag parent) {
        clear();
        readDeliveries(parent.getList(TAG_PENDING, Tag.TAG_COMPOUND), pending);
        readDeliveries(parent.getList(TAG_UNCERTAIN, Tag.TAG_COMPOUND), uncertain);
        if (parent.contains(TAG_OWNER, Tag.TAG_COMPOUND) && hasPending()) {
            var owner = parent.getCompound(TAG_OWNER);
            var adapter = ResourceLocation.tryParse(owner.getString("adapter"));
            var dimension = ResourceLocation.tryParse(owner.getString("dimension"));
            var block = ResourceLocation.tryParse(owner.getString("block"));
            if (adapter != null && dimension != null && block != null) {
                ownership = new Ownership(adapter, dimension, BlockPos.of(owner.getLong("pos")), block);
            } else {
                // An unreadable controller cannot authorize future insertions.
                uncertain.addAll(pending);
                pending.clear();
            }
        }
    }

    private static void readDeliveries(ListTag list, List<Delivery> deliveries) {
        for (int i = 0; i < list.size(); i++) {
            try {
                var tag = list.getCompound(i);
                var stack = readStack(tag.getCompound("stack"));
                var adapter = ResourceLocation.tryParse(tag.getString("adapter"));
                var dimension = ResourceLocation.tryParse(tag.getString("dimension"));
                var block = ResourceLocation.tryParse(tag.getString("block"));
                var face = Direction.from3DDataValue(tag.getInt("face"));
                if (stack == null || stack.amount() <= 0 || adapter == null || dimension == null
                        || block == null || face == null) continue;
                deliveries.add(new Delivery(adapter, dimension, BlockPos.of(tag.getLong("pos")),
                        face, block, stack));
            } catch (RuntimeException | LinkageError malformed) {
                LOG.warn("Ignoring malformed pending batch input at index {}", i, malformed);
            }
        }
    }

    public List<Delivery> snapshot() { return List.copyOf(pending); }
    public List<Delivery> uncertainSnapshot() { return List.copyOf(uncertain); }
    public Ownership ownership() { return ownership; }
    public boolean hasPending() { return !pending.isEmpty() || !uncertain.isEmpty(); }
    public void clear() { pending.clear(); uncertain.clear(); ownership = null; }

    private static CompoundTag writeStack(GenericStack stack) {
        var tag = new CompoundTag();
        tag.putLong("amount", stack.amount());
        if (stack.what() instanceof AEItemKey item) {
            tag.putString("kind", "item");
            tag.put("value", item.toTag());
        } else if (stack.what() instanceof AEFluidKey fluid) {
            tag.putString("kind", "fluid");
            tag.put("value", fluid.toTag());
        } else {
            tag.putString("kind", "generic");
            tag.put("value", GenericStack.writeTag(stack));
        }
        return tag;
    }

    private static GenericStack readStack(CompoundTag tag) {
        var value = tag.getCompound("value");
        long amount = tag.getLong("amount");
        return switch (tag.getString("kind")) {
            case "item" -> {
                var key = AEItemKey.fromTag(value);
                yield key == null || amount <= 0 ? null : new GenericStack(key, amount);
            }
            case "fluid" -> {
                var key = AEFluidKey.fromTag(value);
                yield key == null || amount <= 0 ? null : new GenericStack(key, amount);
            }
            case "generic" -> GenericStack.readTag(value);
            default -> null;
        };
    }
}
