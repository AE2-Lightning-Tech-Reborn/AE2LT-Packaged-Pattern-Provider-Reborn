package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.logic.PendingAmounts;

/**
 * Batches virtual-craft outputs and flushes them on a fixed interval (default
 * every 10 ticks).
 *
 * <p>Outputs are merged by {@link AEKey} inside the pending buffer. A key is
 * split into multiple long-sized chunks only when its total exceeds
 * {@link Long#MAX_VALUE}, so no product is lost to saturation.
 *
 * <p>The class itself enforces no upper bound on accumulated amount; the
 * outer push path is expected to apply a per-tick admission cap (e.g. 64
 * outputs per lane per tick) before calling {@link #enqueue}.
 */
public final class VirtualBatchAccumulator {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualBatchAccumulator.class);
    private static final String TAG_PENDING = "pending";
    private static final String TAG_LAST_FLUSH_TICK = "last_flush_tick";

    public static final int FLUSH_INTERVAL_TICKS = 10;

    private final PendingAmounts<AEKey> pending = new PendingAmounts<>();
    private long lastFlushTick = Long.MIN_VALUE;

    /** Merge {@code outputs} into the pending buffer keyed by {@link AEKey}. */
    public void enqueue(List<GenericStack> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return;
        }
        for (var stack : outputs) {
            if (stack.amount() <= 0) {
                continue;
            }
            mergePending(stack.what(), stack.amount());
        }
    }

    /**
     * Deliver each quantity with a receipt. A later failed insertion cannot
     * restore outputs whose earlier insertions were already acknowledged.
     */
    public boolean tickFlushReceipts(long gameTick, ToLongFunction<GenericStack> sink) {
        if (lastFlushTick == Long.MIN_VALUE) {
            lastFlushTick = gameTick;
            return false;
        }
        long elapsed;
        try {
            elapsed = Math.subtractExact(gameTick, lastFlushTick);
            if (elapsed < 0) elapsed = Long.MAX_VALUE;
        } catch (ArithmeticException overflow) {
            elapsed = Long.MAX_VALUE;
        }
        if (elapsed < FLUSH_INTERVAL_TICKS) return false;
        lastFlushTick = gameTick;
        if (pending.isEmpty()) return false;
        try {
            pending.deliver(entry -> sink.applyAsLong(new GenericStack(entry.key(), entry.amount())));
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Virtual batch flush failed; retaining unacknowledged outputs", e);
        }
        return true;
    }

    /** Force flush regardless of interval; used on host removal / clearContent. */
    public List<GenericStack> drainAll() {
        if (pending.isEmpty()) {
            return List.of();
        }
        var merged = snapshotStacks();
        pending.clear();
        return merged;
    }

    public void writeToNBT(CompoundTag tag) {
        var entries = new ListTag();
        for (var stack : snapshotStacks()) {
            entries.add(GenericStack.writeTag(stack));
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
                var stack = GenericStack.readTag(entries.getCompound(i));
                if (stack != null && stack.what() != null && stack.amount() > 0) {
                    mergePending(stack.what(), stack.amount());
                }
            } catch (RuntimeException | LinkageError ignored) {
                LOG.warn("Ignoring malformed virtual batch entry");
            }
        }
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

    public void clear() {
        pending.clear();
        lastFlushTick = Long.MIN_VALUE;
    }

    /** Saturated debug snapshot; use the NBT entries for exact persisted totals. */
    public Map<AEKey, Long> snapshot() {
        var totals = new HashMap<AEKey, Long>();
        for (var entry : pending.snapshot()) {
            totals.merge(entry.key(), entry.amount(), (left, right) ->
                    left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right);
        }
        return totals;
    }
}
