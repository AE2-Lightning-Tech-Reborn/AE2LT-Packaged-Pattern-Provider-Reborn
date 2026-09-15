package com.moakiee.ae2lt.packaged.logic.multiblock.ma;

import java.util.List;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

/** Selects the first retained output-compatible recipe that matches a push. */
final class CandidateRecipeSelector {
    private CandidateRecipeSelector() {
    }

    @Nullable
    static <C, M> M firstMatch(List<C> candidates, Function<C, @Nullable M> matcher) {
        var selection = firstSelection(candidates, matcher);
        return selection == null ? null : selection.match();
    }

    @Nullable
    static <C, M> Selection<C, M> firstSelection(List<C> candidates,
                                                  Function<C, @Nullable M> matcher) {
        for (var candidate : candidates) {
            var match = matcher.apply(candidate);
            if (match != null) {
                return new Selection<>(candidate, match);
            }
        }
        return null;
    }

    record Selection<C, M>(C candidate, M match) {
    }
}
