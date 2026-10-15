package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.core.crafting.overload.OverloadedPatternDetails;
import com.moakiee.thunderbolt.core.crafting.pattern.IWrappedPatternDetails;

/** Ordinary-pattern batch arithmetic; no overload or shared-input accounting is introduced. */
public final class BatchCopies {
    public static final int MAX_PER_LANE = 64;
    private BatchCopies() {}

    public static boolean eligible(IPatternDetails pattern) {
        return pattern != null && !(pattern instanceof OverloadedPatternDetails)
                && !(pattern instanceof IWrappedPatternDetails);
    }

    public static KeyCounter[] copy(KeyCounter[] template) {
        var result = new KeyCounter[template.length];
        for (int i = 0; i < template.length; i++) {
            result[i] = new KeyCounter();
            if (template[i] != null) result[i].addAll(template[i]);
        }
        return result;
    }

    public static List<GenericStack> scale(List<GenericStack> stacks, int copies) {
        return scale(stacks, (long) copies);
    }

    public static List<GenericStack> scale(List<GenericStack> stacks, long copies) {
        if (copies <= 0) throw new IllegalArgumentException("copies must be positive");
        var result = new ArrayList<GenericStack>(stacks.size());
        for (var stack : stacks) {
            if (stack.amount() <= 0) throw new IllegalArgumentException("nonpositive input/output");
            result.add(new GenericStack(stack.what(), Math.multiplyExact(stack.amount(), copies)));
        }
        return List.copyOf(result);
    }

    /** Largest homogeneous batch whose individual products and remainders fit in a long. */
    public static long maxScalableCopies(List<GenericStack> stacks) {
        if (stacks.isEmpty()) return 0;
        long maximum = Long.MAX_VALUE;
        for (var stack : stacks) {
            if (stack.amount() <= 0) throw new IllegalArgumentException("nonpositive output");
            maximum = Math.min(maximum, Long.MAX_VALUE / stack.amount());
        }
        return maximum;
    }

    /** Pure, monotonic capacity probes: one when fully affordable, at most 64 otherwise. */
    public static long fittingLong(long maximum, LongPredicate fits) {
        if (maximum <= 0) return 0;
        if (fits.test(maximum)) return maximum;
        long low = 0, high = maximum - 1;
        while (low < high) {
            long difference = high - low;
            long middle = low + (difference >>> 1) + (difference & 1L);
            if (fits.test(middle)) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    /** Capacity probes must be pure and monotonic; at most seven probes for the lane limit. */
    public static int fitting(int maximum, IntPredicate fits) {
        int low = 0, high = Math.min(MAX_PER_LANE, Math.max(0, maximum));
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (fits.test(middle)) low = middle;
            else high = middle - 1;
        }
        return low;
    }
}
