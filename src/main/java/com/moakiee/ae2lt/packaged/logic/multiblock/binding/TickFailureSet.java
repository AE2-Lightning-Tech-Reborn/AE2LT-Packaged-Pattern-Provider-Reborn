package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.HashSet;
import java.util.Set;

/** Failed requests are suppressed only for the current tick; successful requests are unlimited. */
public final class TickFailureSet<K> {
    private long tick = Long.MIN_VALUE;
    private final Set<K> failures = new HashSet<>();

    public boolean isReady(K key, long now) {
        advance(now);
        return !failures.contains(key);
    }

    public void recordFailure(K key, long now) {
        advance(now);
        failures.add(key);
    }

    public void clear() { failures.clear(); }

    private void advance(long now) {
        if (tick != now) {
            failures.clear();
            tick = now;
        }
    }
}
