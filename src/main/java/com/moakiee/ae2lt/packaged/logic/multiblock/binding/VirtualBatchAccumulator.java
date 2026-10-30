package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import com.moakiee.ae2lt.packaged.logic.PendingAmounts;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

/**
 * Batches virtual-craft outputs and flushes them on a fixed interval (default
 * every 10 ticks).
 *
 * <p>Outputs are merged by {@link AEKey} inside the pending buffer so each
 * pattern only contributes one entry per key per flush window, keeping the
 * inserted stack count bounded even when push frequency is very high.
 *
 * <p>The push path checks {@link #maxCopies} before accepting a craft so
 * each accepted craft, including duplicate remainder keys, stays within a long.
 * Totals beyond a long are retained as multiple chunks, including after reload.
 */
public final class VirtualBatchAccumulator {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualBatchAccumulator.class);
    private static final String TAG_PENDING = "pending";
    private static final String TAG_LAST_FLUSH_TICK = "last_flush_tick";
    private static final String TAG_AMOUNT = "a";
    private static final String TAG_KIND = "k";
    private static final String TAG_VALUE = "v";

    public static final int FLUSH_INTERVAL_TICKS = 10;

    private final PendingAmounts<AEKey> pending = new PendingAmounts<>();
    private long lastFlushTick = Long.MIN_VALUE;

    /** Merge {@code outputs} into the pending buffer keyed by {@link AEKey}. */
    public void enqueue(List<GenericStack> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return;
        }
        for (var stack : outputs) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                continue;
            }
            mergePending(stack.what(), stack.amount());
        }
    }

    /**
     * Flush every {@link #FLUSH_INTERVAL_TICKS} ticks. The sink receives a fresh
     * snapshot and returns only the portion which was not delivered. This keeps
     * delivery failures from restoring products that already reached the sink.
     *
     * @return true when a flush actually happened on this call
     */
    public boolean tickFlush(long gameTick, Function<List<GenericStack>, List<GenericStack>> sink) {
        if (lastFlushTick == Long.MIN_VALUE) {
            lastFlushTick = gameTick;
            return false;
        }
        long elapsed;
        try {
            elapsed = Math.subtractExact(gameTick, lastFlushTick);
            if (elapsed < 0) {
                // A restored world can move the clock backwards; do not keep
                // pending outputs asleep until it catches up to an old save.
                elapsed = Long.MAX_VALUE;
            }
        } catch (ArithmeticException overflow) {
            // A wrapped game clock must not suppress flushing forever.
            elapsed = Long.MAX_VALUE;
        }
        if (elapsed < FLUSH_INTERVAL_TICKS) {
            return false;
        }
        lastFlushTick = gameTick;
        if (pending.isEmpty()) {
            return true;
        }
        var merged = snapshotStacks();
        pending.clear();
        try {
            var residual = sink.apply(merged);
            if (residual != null) {
                for (var stack : residual) {
                    if (stack != null && stack.what() != null && stack.amount() > 0) {
                        mergePending(stack.what(), stack.amount());
                    }
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // A sink exception means no delivery result can be trusted. Restore
            // the snapshot; sinks must be exception-safe after committing output.
            for (var stack : merged) {
                mergePending(stack.what(), stack.amount());
            }
            LOG.warn("Virtual batch flush failed; retaining {} stack(s)", merged.size(), e);
        }
        return true;
    }

    /** Deliver owned outputs by exact receipt without changing the regular flush schedule. */
    public void deliver(ToLongFunction<GenericStack> sink) {
        pending.deliver(entry -> sink.applyAsLong(new GenericStack(entry.key(), entry.amount())));
    }

    /** Drain remaining outputs after removal-time delivery. */
    public List<GenericStack> drainAll() {
        if (pending.isEmpty()) {
            return List.of();
        }
        var merged = snapshotStacks();
        pending.clear();
        return merged;
    }

    public void writeToNBT(CompoundTag tag) {
        tag.remove(TAG_PENDING);
        tag.remove(TAG_LAST_FLUSH_TICK);
        var entries = new ListTag();
        for (var entry : pending.snapshot()) {
            entries.add(writeStack(entry.key(), entry.amount()));
        }
        if (!entries.isEmpty()) {
            tag.put(TAG_PENDING, entries);
        }
        if (lastFlushTick != Long.MIN_VALUE) {
            tag.putLong(TAG_LAST_FLUSH_TICK, lastFlushTick);
        }
    }

    public void readFromNBT(CompoundTag tag) {
        clear();
        if (tag.contains(TAG_LAST_FLUSH_TICK, Tag.TAG_LONG)) {
            lastFlushTick = tag.getLong(TAG_LAST_FLUSH_TICK);
        }
        if (!tag.contains(TAG_PENDING, Tag.TAG_LIST)) {
            return;
        }
        var entries = tag.getList(TAG_PENDING, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            try {
                var stack = readStack(entries.getCompound(i));
                if (stack != null && stack.what() != null && stack.amount() > 0) {
                    mergePending(stack.what(), stack.amount());
                }
            } catch (RuntimeException | LinkageError exception) {
                LOG.warn("Ignoring malformed virtual batch entry at index {}", i, exception);
            }
        }
    }

    /** Capacity is checked before accepting inputs; pending products must never saturate. */
    public long maxCopies(List<GenericStack> outputs) {
        var oneCopy = new HashMap<AEKey, Long>();
        try {
            for (var stack : outputs) {
                if (stack == null || stack.what() == null || stack.amount() <= 0) return 0;
                oneCopy.merge(stack.what(), stack.amount(), Math::addExact);
            }
        } catch (ArithmeticException overflow) { return 0; }
        if (oneCopy.isEmpty()) return 0;
        long maximum = Long.MAX_VALUE;
        for (var entry : oneCopy.entrySet()) {
            maximum = Math.min(maximum,
                    (Long.MAX_VALUE - pending.cappedAmount(entry.getKey())) / entry.getValue());
        }
        return maximum;
    }

    private static CompoundTag writeStack(AEKey key, long amount) {
        var tag = new CompoundTag();
        tag.putLong(TAG_AMOUNT, amount);
        if (key instanceof AEItemKey item) {
            tag.putString(TAG_KIND, "i");
            tag.put(TAG_VALUE, item.toTag());
        } else if (key instanceof AEFluidKey fluid) {
            tag.putString(TAG_KIND, "f");
            tag.put(TAG_VALUE, fluid.toTag());
        } else {
            tag.putString(TAG_KIND, "g");
            tag.put(TAG_VALUE, GenericStack.writeTag(new GenericStack(key, amount)));
        }
        return tag;
    }

    private static GenericStack readStack(CompoundTag tag) {
        if (!tag.contains(TAG_KIND, Tag.TAG_STRING)) {
            return GenericStack.readTag(tag);
        }
        var amount = tag.getLong(TAG_AMOUNT);
        if (amount <= 0 || !tag.contains(TAG_VALUE, Tag.TAG_COMPOUND)) {
            return null;
        }
        var value = tag.getCompound(TAG_VALUE);
        return switch (tag.getString(TAG_KIND)) {
            case "i" -> {
                var key = AEItemKey.fromTag(value);
                yield key == null ? null : new GenericStack(key, amount);
            }
            case "f" -> {
                var key = AEFluidKey.fromTag(value);
                yield key == null ? null : new GenericStack(key, amount);
            }
            case "g" -> {
                var stack = GenericStack.readTag(value);
                yield stack == null ? null : new GenericStack(stack.what(), amount);
            }
            default -> null;
        };
    }

    private void mergePending(AEKey key, long amount) {
        if (key == null || amount <= 0) {
            return;
        }
        pending.add(key, amount);
    }

    private List<GenericStack> snapshotStacks() {
        var merged = new ArrayList<GenericStack>();
        for (var entry : pending.snapshot()) {
            merged.add(new GenericStack(entry.key(), entry.amount()));
        }
        return merged;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public long getLastFlushTick() {
        return lastFlushTick;
    }

    public void clear() {
        pending.clear();
        lastFlushTick = Long.MIN_VALUE;
    }

    /** Debug totals capped per key at Long.MAX_VALUE; drain and NBT preserve all chunks. */
    public Map<AEKey, Long> snapshot() {
        var amounts = new HashMap<AEKey, Long>();
        for (var entry : pending.snapshot()) {
            amounts.put(entry.key(), pending.cappedAmount(entry.key()));
        }
        return amounts;
    }
}
