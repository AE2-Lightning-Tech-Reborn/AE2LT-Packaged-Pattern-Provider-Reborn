package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

class PendingBatchInputsTest {
    @BeforeAll
    static void bootstrap() { MinecraftTestBootstrap.initialize(); }

    private static PendingBatchInputs.Delivery delivery(GenericStack stack) {
        return new PendingBatchInputs.Delivery(new ResourceLocation("ae2ltpp", "mekmm"),
                new ResourceLocation("minecraft", "overworld"), new BlockPos(1, 2, 3),
                Direction.NORTH, new ResourceLocation("minecraft", "stone"), stack);
    }

    private static PendingBatchInputs.Delivery item(long amount) {
        return delivery(new GenericStack(AEItemKey.of(Items.IRON_INGOT), amount));
    }

    @Test
    void partialReceiptsRetryOnlyTheRemainder() {
        var queue = new PendingBatchInputs();
        queue.adopt(List.of(item(100)));
        queue.deliver(d -> 30);
        assertEquals(List.of(item(70)), queue.snapshot());
        queue.deliver(d -> 0);
        assertEquals(List.of(item(70)), queue.snapshot());
        queue.deliver(d -> d.stack().amount());
        assertFalse(queue.hasPending());
    }

    @Test
    void itemsAndFluidsRetainTheirRouteAcrossReloads() {
        var deliveries = List.of(item(130), delivery(new GenericStack(AEFluidKey.of(Fluids.WATER), 10000)));
        var queue = new PendingBatchInputs();
        queue.adopt(deliveries);
        var tag = new CompoundTag();
        queue.writeToNBT(tag);
        var restored = new PendingBatchInputs();
        restored.readFromNBT(tag);
        assertEquals(deliveries, restored.snapshot());
        restored.deliver(d -> d.stack().amount());
        restored.writeToNBT(tag);
        queue.readFromNBT(tag);
        assertFalse(queue.hasPending());
    }

    @Test
    void ambiguousInsertionIsNeverReplayedAndOtherPortsRemainOwned() {
        var queue = new PendingBatchInputs();
        var remaining = delivery(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000));
        queue.adopt(List.of(item(64), remaining));
        assertThrows(IllegalStateException.class, () -> queue.deliver(d -> {
            throw new IllegalStateException("injected failure after insertion");
        }));
        assertEquals(List.of(remaining), queue.snapshot());
        queue.deliver(d -> d.stack().amount());
        assertFalse(queue.hasPending());
    }

    @Test
    void rejectsDuplicateOwnershipAndOverflowBeforeAdoption() {
        var queue = new PendingBatchInputs();
        queue.adopt(List.of(item(10)));
        assertThrows(IllegalStateException.class, () -> queue.adopt(List.of(item(20))));
        assertEquals(List.of(item(10)), queue.snapshot());
        assertThrows(ArithmeticException.class, () -> PendingBatchInputs.scale(List.of(item(Long.MAX_VALUE)), 2));
        assertTrue(PendingBatchInputs.scale(List.of(item(1)), 0).isEmpty());
        assertEquals(List.of(item(130)), PendingBatchInputs.scale(List.of(item(1)), 130));
    }

    @Test
    void randomizedBackpressureAndReloadsConservePhysicalInputs() {
        int trials = Integer.getInteger("ae2ltpp.stress.trials", 10000);
        int seeds = Integer.getInteger("ae2ltpp.stress.seeds", 1);
        for (int seed = 0; seed < seeds; seed++) {
            var random = new Random(0x580e3e5L + seed);
            var queue = new PendingBatchInputs();
            long produced = 0;
            var delivered = new AtomicLong();
            for (int i = 0; i < trials; i++) {
                if (!queue.hasPending()) {
                    long amount = 1 + random.nextInt(100000);
                    queue.adopt(List.of(item(amount)));
                    produced += amount;
                }
                queue.deliver(d -> {
                    long accepted = random.nextInt(4) == 0 ? 0
                            : Math.min(d.stack().amount(), 1 + random.nextInt(8192));
                    delivered.addAndGet(accepted);
                    return accepted;
                });
                if (i % 97 == 0) {
                    var tag = new CompoundTag();
                    queue.writeToNBT(tag);
                    var restored = new PendingBatchInputs();
                    restored.readFromNBT(tag);
                    queue = restored;
                }
                long pending = queue.snapshot().stream().mapToLong(d -> d.stack().amount()).sum();
                assertEquals(produced, delivered.get() + pending, "seed=" + seed + ", trial=" + i);
            }
            queue.deliver(d -> { delivered.addAndGet(d.stack().amount()); return d.stack().amount(); });
            assertFalse(queue.hasPending());
            assertEquals(produced, delivered.get());
        }
    }
}
