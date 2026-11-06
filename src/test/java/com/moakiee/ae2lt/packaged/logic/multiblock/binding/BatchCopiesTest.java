package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import appeng.api.crafting.IPatternDetails;
import com.moakiee.thunderbolt.core.crafting.overload.OverloadedPatternDetails;
import com.moakiee.thunderbolt.core.crafting.pattern.IWrappedPatternDetails;
import net.minecraft.core.Direction;

class BatchCopiesTest {
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
