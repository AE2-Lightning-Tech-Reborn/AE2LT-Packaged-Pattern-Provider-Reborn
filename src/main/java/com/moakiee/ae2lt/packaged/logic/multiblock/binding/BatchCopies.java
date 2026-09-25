package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.core.crafting.overload.OverloadedPatternDetails;
import com.moakiee.thunderbolt.core.crafting.pattern.IWrappedPatternDetails;

/** Bounded ordinary-pattern batching; no overload or shared-input accounting is introduced. */
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
        if (copies <= 0) throw new IllegalArgumentException("copies must be positive");
        var result = new ArrayList<GenericStack>(stacks.size());
        for (var stack : stacks) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) throw new IllegalArgumentException("nonpositive input/output");
            result.add(new GenericStack(stack.what(), Math.multiplyExact(stack.amount(), copies)));
        }
        return List.copyOf(result);
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
