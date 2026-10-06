package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.me.key.LightningKey;
import java.util.List;
import com.moakiee.thunderbolt.core.crafting.overload.OverloadedPatternDetails;
import com.moakiee.thunderbolt.core.crafting.pattern.IWrappedPatternDetails;
import net.minecraft.core.Direction;

class BatchCopiesTest {
    @Test
    void unboundedCapacitySearchHandlesLargeAndPartialBudgetsWithoutOverflow() {
        for (long affordable : new long[] {0, 1, 63, 64, 65, 4096,
                (long) Integer.MAX_VALUE + 1, 3_000_000_000L, Long.MAX_VALUE - 1, Long.MAX_VALUE}) {
            var probes = new AtomicInteger();
            assertEquals(affordable, BatchCopies.fittingLong(Long.MAX_VALUE, copies -> {
                probes.incrementAndGet();
                return copies <= affordable;
            }));
            assertTrue(probes.get() <= 64);
            if (affordable == Long.MAX_VALUE) assertEquals(1, probes.get());
        }
        assertEquals(0, BatchCopies.fittingLong(-1, copies -> {
            throw new AssertionError("nonpositive budgets need no probe");
        }));
    }

    @Test
    void largeProductsAndContainerRemaindersStayExactAndBoundTheBatch() {
        var outputs = List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 3),
                new GenericStack(LightningKey.EXTREME_HIGH_VOLTAGE, 2));
        var scaled = BatchCopies.scale(outputs, 3_000_000_000L);
        assertEquals(9_000_000_000L, scaled.get(0).amount());
        assertEquals(6_000_000_000L, scaled.get(1).amount());
        long maximum = BatchCopies.maxScalableCopies(outputs);
        assertEquals(Long.MAX_VALUE / 3, maximum);
        assertEquals(maximum * 3, BatchCopies.scale(outputs, maximum).get(0).amount());
        assertThrows(ArithmeticException.class, () -> BatchCopies.scale(outputs, maximum + 1));
        assertEquals(0, BatchCopies.maxScalableCopies(List.of()));
    }

    @Test
    void acceptsExactlyWhatTheTightestPortCanHoldWithBoundedProbes() {
        for (int available = 0; available <= 64; available++) {
            int capacity = available;
            var probes = new AtomicInteger();
            assertEquals(capacity, BatchCopies.fitting(Integer.MAX_VALUE, copies -> {
                probes.incrementAndGet();
                return copies <= capacity;
            }));
            assertTrue(probes.get() <= 7);
        }
    }

    @Test
    void ordinaryAndBatchPushesShareTheSameVirtualLaneBudget() {
        var limiter = new LaneRateLimiter(64);
        var lane = new LaneKey.FaceLane(Direction.NORTH);
        assertTrue(limiter.tryAcquire(lane, 10, 60));
        assertEquals(4, limiter.remaining(lane, 10));
        assertTrue(limiter.tryAcquire(lane, 10));
        assertFalse(limiter.tryAcquire(lane, 10, 4));
        assertTrue(limiter.tryAcquire(lane, 10, 3));
        assertFalse(limiter.tryAcquire(lane, 10));
        assertEquals(64, limiter.remaining(lane, 11));
    }

    @Test
    void overloadAndWrappedPatternsNeverEnterTheNewContract() {
        assertTrue(BatchCopies.eligible(pattern(IPatternDetails.class)));
        assertFalse(BatchCopies.eligible(pattern(OverloadedPatternDetails.class)));
        assertFalse(BatchCopies.eligible(pattern(IWrappedPatternDetails.class)));
        assertFalse(BatchCopies.eligible(null));
    }

    private IPatternDetails pattern(Class<?> type) {
        return (IPatternDetails) Proxy.newProxyInstance(getClass().getClassLoader(),
                type == IPatternDetails.class ? new Class<?>[] {type} : new Class<?>[] {IPatternDetails.class, type}, (proxy, method, args) -> null);
    }
}
