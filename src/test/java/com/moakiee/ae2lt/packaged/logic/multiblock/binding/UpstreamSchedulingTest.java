package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;
import net.minecraft.world.item.Items;

class UpstreamSchedulingTest {
    @BeforeAll static void bootstrap() { MinecraftTestBootstrap.initialize(); }
    @Test void pollingBacksOffAndInputWakesItWithoutDuplicatePolls() {
        var schedule = new AdaptiveReturnSchedule<String>();
        int tick = 0;
        for (int interval : new int[]{1, 2, 4, 8, 20, 20}) {
            assertTrue(schedule.isDue("target", tick));
            schedule.record("target", tick, false);
            assertFalse(schedule.isDue("target", tick));
            assertFalse(schedule.isDue("target", tick + interval - 1));
            tick += interval;
        }
        schedule.record("target", tick, false);
        schedule.wake("target", tick);
        assertFalse(schedule.isDue("target", tick));
        assertTrue(schedule.isDue("target", tick + 1));
    }
    @Test void rejectedPatternRetriesEveryNextTickWithoutThrottlingOtherPatterns() {
        var retry = new TickFailureSet<String>();
        for (long tick = 0; tick < 100; tick++) {
            assertTrue(retry.isReady("blocked", tick));
            retry.recordFailure("blocked", tick);
            assertFalse(retry.isReady("blocked", tick));
            assertTrue(retry.isReady("other", tick));
        }
        assertTrue(retry.isReady("blocked", 10), "clock rollback starts a fresh tick");
        retry.recordFailure("blocked", 10);
        retry.clear();
        assertTrue(retry.isReady("blocked", 10));
    }
    @Test void longBatchSearchFindsEnergyBudgetWithoutIntTruncation() {
        assertEquals(1L << 35, BatchCopies.fittingLong(Long.MAX_VALUE, n -> n <= (1L << 35)));
        assertEquals(Long.MAX_VALUE, BatchCopies.fittingLong(Long.MAX_VALUE, n -> true));
        assertEquals(0, BatchCopies.fittingLong(Long.MAX_VALUE, n -> false));
    }
    @Test void pendingAndDuplicateRemaindersLimitAcceptedCopiesBeforeOverflow() {
        var key = AEItemKey.of(Items.STONE);
        var batch = new VirtualBatchAccumulator();
        batch.enqueue(List.of(new GenericStack(key, Long.MAX_VALUE - 10)));
        assertEquals(2, batch.maxCopies(List.of(new GenericStack(key, 2), new GenericStack(key, 3))));
        assertEquals(0, batch.maxCopies(List.of(new GenericStack(key, Long.MAX_VALUE), new GenericStack(key, 1))));
        assertEquals(List.of(new GenericStack(key, 15)), BatchCopies.scale(List.of(new GenericStack(key, 3)), 5L));
        assertThrows(ArithmeticException.class, () -> BatchCopies.scale(List.of(new GenericStack(key, Long.MAX_VALUE)), 2L));
    }
}
