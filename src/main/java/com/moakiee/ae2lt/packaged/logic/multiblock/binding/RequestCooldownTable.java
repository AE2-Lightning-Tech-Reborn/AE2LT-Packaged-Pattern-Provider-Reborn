package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.HashMap;
import java.util.Map;

/** A rejected recipe/mode must not throttle another order on the same controller. */
public final class RequestCooldownTable<K> {
    private record Failure(long firstTick, int count, long nextTick) {}
    private final Map<K, Failure> failures = new HashMap<>();

    public boolean isReady(K key, long tick) {
        var failure = failures.get(key);
        return failure == null || tick < failure.firstTick() || tick >= failure.nextTick();
    }

    public void recordFailure(K key, long tick) {
        var previous = failures.get(key);
        long first = previous == null || tick < previous.firstTick() ? tick : previous.firstTick();
        int count = previous == null || tick < previous.firstTick() ? 1 : Math.min(previous.count() + 1, 1000000);
        failures.put(key, new Failure(first, count, LaneCooldownTable.computeNextAllowedTick(first, count)));
    }

    public void recordSuccess(K key) { failures.remove(key); }
    public void clear() { failures.clear(); }
}
