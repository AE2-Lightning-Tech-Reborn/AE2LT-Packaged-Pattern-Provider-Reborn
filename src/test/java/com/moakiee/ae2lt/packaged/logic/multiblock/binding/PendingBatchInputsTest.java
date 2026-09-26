package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.PendingBatchInputs;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PendingBatchInputsTest {
    private static class TestKey extends AEKey {
        public AEKeyType getType() { return null; }
        public AEKey dropSecondary() { return this; }
        public Object getPrimaryKey() { return getId(); }
        public ResourceLocation getId() { return ResourceLocation.fromNamespaceAndPath("review", "input"); }
        public boolean hasComponents() { return false; }
        protected Component computeDisplayName() { return Component.literal("review"); }
        public CompoundTag toTag(HolderLookup.Provider registries) { return new CompoundTag(); }
        public void writeToPacket(RegistryFriendlyByteBuf out) {}
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
        public boolean isTagged(TagKey<?> tag) { return false; }
    }

    @Test
    void failedBatchReceiptMustNotReplayAlreadyInsertedMaterialsOnTheNextTick() {
        var queue = new PendingBatchInputs();
        queue.adopt(List.of(new PendingBatchInputs.Delivery(
                ResourceLocation.fromNamespaceAndPath("ae2ltpp", "mekmm"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                BlockPos.ZERO, Direction.NORTH,
                ResourceLocation.fromNamespaceAndPath("minecraft", "barrel"),
                new GenericStack(new TestKey(), 10))));
        var machine = new AtomicLong();
        assertThrows(IllegalStateException.class, () -> queue.deliver(d -> {
            machine.addAndGet(d.stack().amount());
            throw new IllegalStateException("accepted input, callback threw before receipt");
        }));
        long queuedAfterFailure = queue.snapshot().stream().mapToLong(d -> d.stack().amount()).sum();
        queue.deliver(d -> { machine.addAndGet(d.stack().amount()); return d.stack().amount(); });
        System.out.println("REVIEW_BATCH_REPLAY paid=10 queuedAfterFailure=" + queuedAfterFailure
                + " machineAfterRetry=" + machine.get());
        assertEquals(10, machine.get(), "already accepted input must not be replayed by tick retry");
    }

    private PendingBatchInputs.Delivery delivery(long amount, int x) {
        return new PendingBatchInputs.Delivery(
                ResourceLocation.fromNamespaceAndPath("ae2ltpp", "mekmm"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                new BlockPos(x, 0, 0), Direction.NORTH,
                ResourceLocation.fromNamespaceAndPath("minecraft", "barrel"),
                new GenericStack(new TestKey(), amount));
    }

    @Test
    void failureKeepsEarlierPartialReceiptsAndTheUnvisitedTail() {
        var queue = new PendingBatchInputs();
        queue.adopt(List.of(delivery(10, 0), delivery(5, 1), delivery(8, 2)));
        var machine = new AtomicLong();
        assertThrows(IllegalStateException.class, () -> queue.deliver(d -> {
            if (d.pos().getX() == 0) { machine.addAndGet(3); return 3; }
            machine.addAndGet(d.stack().amount());
            throw new IllegalStateException("accepted second input before throwing");
        }));
        assertEquals(List.of(7L, 8L), queue.snapshot().stream().map(d -> d.stack().amount()).toList());
        queue.deliver(d -> { machine.addAndGet(d.stack().amount()); return d.stack().amount(); });
        assertEquals(23, machine.get());
        assertFalse(queue.hasPending());
    }

    @Test
    void invalidReceiptsCannotBeRetriedOrIncludedInDrops() {
        for (long receipt : new long[] {-1, 11}) {
            var queue = new PendingBatchInputs();
            queue.adopt(List.of(delivery(10, 0)));
            assertThrows(IllegalStateException.class, () -> queue.deliver(d -> receipt));
            assertTrue(queue.snapshot().isEmpty());
            queue.deliver(d -> { fail("unknown input cannot be retried"); return 0; });
        }
    }

    @Test
    void confirmedRejectionAndPartialAcceptanceRemainRetryable() {
        var queue = new PendingBatchInputs();
        queue.adopt(List.of(delivery(10, 0)));
        queue.deliver(d -> 0);
        assertEquals(10, queue.snapshot().getFirst().stack().amount());
        queue.deliver(d -> 4);
        assertEquals(6, queue.snapshot().getFirst().stack().amount());
        queue.deliver(d -> 6);
        assertFalse(queue.hasPending());
    }
}
