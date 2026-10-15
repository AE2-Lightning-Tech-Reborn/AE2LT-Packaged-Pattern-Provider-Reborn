package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.List;

/**
 * Immutable candidates ordered by adapter priority and discovery order.
 * An empty list caches a failed match; computedAtTick controls expiry.
 */
public record PatternBinding(List<LaneCandidate> candidates, long computedAtTick) {
    public PatternBinding {
        candidates = List.copyOf(candidates);
    }

    public boolean isMatched() {
        return !candidates.isEmpty();
    }

    public static PatternBinding unmatched(long tick) {
        return new PatternBinding(List.of(), tick);
    }
}
