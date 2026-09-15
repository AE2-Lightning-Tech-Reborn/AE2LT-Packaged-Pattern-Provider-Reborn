package com.moakiee.ae2lt.packaged.logic.multiblock.ma;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.packaged.logic.multiblock.BoundedConstraintMatcher;

/** MA-facing bridge to the shared bounded exact matcher. */
final class ConstraintFirstMatcher {
    private ConstraintFirstMatcher() {
    }

    @Nullable
    static <T> List<T> match(List<T> inputs, List<? extends Predicate<T>> constraints) {
        return BoundedConstraintMatcher.matchExactly(inputs, constraints);
    }
}
