package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Provider-local polling deadlines. A target is sampled at most once per game tick. */
public final class AdaptiveReturnSchedule<K> {
    private static final int[] INTERVALS = {1, 2, 4, 8, 20};
    private static final class State {
        long lastPoll = Long.MIN_VALUE;
        long nextPoll = Long.MAX_VALUE;
        int emptyCount;
    }
    private final Map<K, State> states = new HashMap<>();
    private long nextPoll = Long.MAX_VALUE;
    private boolean dirty;

    public boolean hasDue(long tick) {
        return tick >= nextPollTick();
    }

    public long nextPollTick() {
        if (dirty) recomputeDeadline();
        return nextPoll;
    }

    public boolean isDue(K key, long tick) {
        var state = states.get(key);
        return state == null || (state.lastPoll != tick && tick >= state.nextPoll);
    }

    public void record(K key, long tick, boolean extracted) {
        var state = states.computeIfAbsent(key, ignored -> new State());
        state.lastPoll = tick;
        if (extracted) state.emptyCount = 0;
        int interval = INTERVALS[Math.min(state.emptyCount, INTERVALS.length - 1)];
        if (!extracted) state.emptyCount = Math.min(state.emptyCount + 1, INTERVALS.length - 1);
        state.nextPoll = tick + interval;
        dirty = true;
    }

    /** New input wakes a backed-off target without polling again in the same tick. */
    public void wake(K key, long tick) {
        var state = states.computeIfAbsent(key, ignored -> new State());
        state.emptyCount = 0;
        state.nextPoll = Math.min(state.nextPoll, tick + 1);
        nextPoll = Math.min(nextPoll, state.nextPoll);
    }

    public void retainAll(Collection<K> targets) {
        states.keySet().retainAll(targets);
        dirty = true;
    }

    public void clear() {
        states.clear();
        dirty = false;
        nextPoll = Long.MAX_VALUE;
    }

    private void recomputeDeadline() {
        dirty = false;
        nextPoll = Long.MAX_VALUE;
        for (var state : states.values()) nextPoll = Math.min(nextPoll, state.nextPoll);
    }
}
