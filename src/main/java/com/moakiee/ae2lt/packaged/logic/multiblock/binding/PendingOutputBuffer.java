package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.compat.LegacyAeStackTags;
import com.moakiee.ae2lt.packaged.logic.PendingAmounts;
import java.util.List;
import java.util.function.ToLongFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Persistent ownership of virtual products, harvested outputs and dispatch refunds. */
public final class PendingOutputBuffer {
    public static final int FLUSH_INTERVAL_TICKS = 10;

    private final PendingAmounts<AEKey> pending = new PendingAmounts<>();
    private long lastFlushTick = Long.MIN_VALUE;

    public void enqueue(List<GenericStack> outputs) {
        for (var stack : outputs) pending.add(stack.what(), stack.amount());
    }

    public boolean tickFlush(long gameTick, ToLongFunction<GenericStack> sink) {
        if (lastFlushTick == Long.MIN_VALUE) {
            lastFlushTick = gameTick;
            return false;
        }
        if (gameTick - lastFlushTick < FLUSH_INTERVAL_TICKS) return false;
        lastFlushTick = gameTick;
        if (pending.isEmpty()) return false;
        deliver(sink);
        return true;
    }

    public void deliver(ToLongFunction<GenericStack> sink) {
        pending.deliver(entry -> sink.applyAsLong(new GenericStack(entry.key(), entry.amount())));
    }

    public List<GenericStack> snapshot() {
        return pending.snapshot().stream().map(entry -> new GenericStack(entry.key(), entry.amount())).toList();
    }

    public ListTag writeToNBT(HolderLookup.Provider registries) {
        var tag = new ListTag();
        for (var stack : snapshot()) tag.add(LegacyAeStackTags.writeGeneric(registries, stack));
        return tag;
    }

    public void readFromNBT(ListTag tag, HolderLookup.Provider registries) {
        clear();
        for (var entry : tag) {
            if (entry instanceof CompoundTag stackTag) {
                var stack = LegacyAeStackTags.readGeneric(registries, stackTag);
                if (stack != null) pending.add(stack.what(), stack.amount());
            }
        }
    }

    public boolean hasPending() { return !pending.isEmpty(); }

    public void clear() {
        pending.clear();
        lastFlushTick = Long.MIN_VALUE;
    }
}
