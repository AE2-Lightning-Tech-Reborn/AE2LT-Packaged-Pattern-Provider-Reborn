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

import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InputBatchCraftingAdapter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Provider-owned physical inputs that were accepted but not fully inserted. */
public final class PendingBatchInputs {
    private static final Logger LOG = LoggerFactory.getLogger(PendingBatchInputs.class);
    private static final String TAG_PENDING = "ae2ltpp_pending_batch_inputs";

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
                    Registry.BLOCK.getKey(target.level().getBlockState(target.pos()).getBlock()),
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
        if (hasPending() || deliveries.isEmpty()) {
            throw new IllegalStateException("batch input ownership conflict");
        }
        pending.addAll(deliveries);
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
                LOG.error("Batch input ownership is unknown at {} for {} x{}; removed from retry",
                        delivery.pos(), delivery.stack().what(), amount, failure);
                throw failure;
            }
            if (accepted == amount) iterator.remove();
            else if (accepted > 0) iterator.set(delivery.withAmount(amount - accepted));
        }
    }

    public void writeToNBT(CompoundTag parent) {
        parent.remove(TAG_PENDING);
        if (pending.isEmpty()) return;
        var list = new ListTag();
        for (var delivery : pending) {
            var tag = new CompoundTag();
            tag.putString("adapter", delivery.adapter().toString());
            tag.putString("dimension", delivery.dimension().toString());
            tag.putLong("pos", delivery.pos().asLong());
            tag.putInt("face", delivery.face().get3DDataValue());
            tag.putString("block", delivery.block().toString());
            tag.put("stack", writeStack(delivery.stack()));
            list.add(tag);
        }
        parent.put(TAG_PENDING, list);
    }

    public void readFromNBT(CompoundTag parent) {
        clear();
        if (!parent.contains(TAG_PENDING, Tag.TAG_LIST)) return;
        var list = parent.getList(TAG_PENDING, Tag.TAG_COMPOUND);
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
                pending.add(new Delivery(adapter, dimension, BlockPos.of(tag.getLong("pos")),
                        face, block, stack));
            } catch (RuntimeException | LinkageError malformed) {
                LOG.warn("Ignoring malformed pending batch input at index {}", i, malformed);
            }
        }
    }

    public List<Delivery> snapshot() { return List.copyOf(pending); }
    public boolean hasPending() { return !pending.isEmpty(); }
    public void clear() { pending.clear(); }

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
