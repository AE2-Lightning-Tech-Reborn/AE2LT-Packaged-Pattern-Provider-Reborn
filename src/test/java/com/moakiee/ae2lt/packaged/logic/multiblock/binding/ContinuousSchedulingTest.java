package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContinuousSchedulingTest {
    @Test void continuousInputCannotPostponeTheFirstPoll() {
        var schedule = new AdaptiveReturnSchedule<String>();
        schedule.wake("machine", 100);
        schedule.wake("machine", 101);
        assertTrue(schedule.hasDue(101));
        assertTrue(schedule.isDue("machine", 101));
    }

    @Test void failureOnlySuppressesTheSameRequestInTheSameTick() {
        var failures = new TickFailureSet<String>();
        assertTrue(failures.isReady("machine/recipe/mode", 50));
        failures.recordFailure("machine/recipe/mode", 50);
        assertFalse(failures.isReady("machine/recipe/mode", 50));
        assertTrue(failures.isReady("machine/other-recipe/mode", 50));
        assertTrue(failures.isReady("machine/recipe/other-mode", 50));
        assertTrue(failures.isReady("machine/recipe/mode", 51));
        assertTrue(failures.isReady("machine/recipe/mode", 51));
        failures.recordFailure("machine/recipe/mode", 51);
        assertTrue(failures.isReady("machine/recipe/mode", 4), "world time rollback clears transient failures");
    }

    @Test void emptyPollingBacksOffButNewInputWakesWithinOneTick() {
        var schedule = new AdaptiveReturnSchedule<String>();
        long tick = 100;
        for (int interval : new int[] {1, 2, 4, 8, 20, 20}) {
            assertTrue(schedule.isDue("machine", tick));
            schedule.record("machine", tick, false);
            assertFalse(schedule.isDue("machine", tick));
            assertFalse(schedule.hasDue(tick + interval - 1));
            tick += interval;
            assertTrue(schedule.hasDue(tick));
        }
        schedule.record("machine", tick, false);
        schedule.wake("machine", tick);
        assertFalse(schedule.isDue("machine", tick));
        assertTrue(schedule.hasDue(tick + 1));
        schedule.record("machine", tick + 1, true);
        assertTrue(schedule.hasDue(tick + 2));
    }

    @Test void targetsHaveIndependentDeadlinesAndRemovedTargetsStopWaking() {
        var schedule = new AdaptiveReturnSchedule<String>();
        schedule.record("a", 100, true);
        schedule.record("b", 101, true);
        assertTrue(schedule.hasDue(101));
        schedule.retainAll(List.of("b"));
        assertFalse(schedule.hasDue(101));
        assertTrue(schedule.hasDue(102));
        schedule.clear();
        assertFalse(schedule.hasDue(1000));
        schedule.wake("new", 1000);
        assertFalse(schedule.hasDue(1000));
        assertTrue(schedule.hasDue(1001));
    }

    @Test void aBusyMachineIsPolledOncePerTickEvenWhenRepeatedlyWoken() {
        var schedule = new AdaptiveReturnSchedule<String>();
        for (long tick = 1; tick <= 200; tick++) {
            assertTrue(schedule.isDue("controller", tick));
            schedule.record("controller", tick, true);
            for (int i = 0; i < 64; i++) {
                schedule.wake("controller", tick);
                assertFalse(schedule.isDue("controller", tick));
            }
        }
    }
}
