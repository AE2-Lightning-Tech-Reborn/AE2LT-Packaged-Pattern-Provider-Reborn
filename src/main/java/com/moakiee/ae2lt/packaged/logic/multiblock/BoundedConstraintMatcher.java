package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

/**
 * Deterministic exact bipartite assignment with a fixed search budget.
 *
 * <p>Constraints are tried from the fewest candidate inputs to the most,
 * preserving the returned order of the original constraints. A budget
 * exhaustion is indistinguishable from no valid assignment and fails closed.
 */
public final class BoundedConstraintMatcher {

    private static final int MAX_SEARCH_NODES = 4096;

    private BoundedConstraintMatcher() {
    }

    @Nullable
    public static <T> List<T> matchExactly(List<T> inputs,
                                           List<? extends Predicate<T>> constraints) {
        if (inputs.size() != constraints.size() || inputs.size() > 64) {
            return null;
        }
        return matchInternal(inputs, constraints);
    }

    /**
     * Assignment where every constraint consumes one input, but inputs may
     * remain unused. Callers use this when the input list models capacity
     * (e.g. one token per available portion) and leftover capacity must not
     * invalidate the match.
     */
    @Nullable
    public static <T> List<T> matchWithin(List<T> inputs,
                                          List<? extends Predicate<T>> constraints) {
        if (inputs.size() < constraints.size() || inputs.size() > 64) {
            return null;
        }
        return matchInternal(inputs, constraints);
    }

    @Nullable
    private static <T> List<T> matchInternal(List<T> inputs,
                                             List<? extends Predicate<T>> constraints) {

        var choices = new ArrayList<ConstraintChoices>(constraints.size());
        for (int constraintIndex = 0; constraintIndex < constraints.size(); constraintIndex++) {
            var matchingInputs = new ArrayList<Integer>();
            var constraint = constraints.get(constraintIndex);
            for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
                if (constraint.test(inputs.get(inputIndex))) {
                    matchingInputs.add(inputIndex);
                }
            }
            if (matchingInputs.isEmpty()) {
                return null;
            }
            choices.add(new ConstraintChoices(constraintIndex, List.copyOf(matchingInputs)));
        }

        choices.sort(Comparator.comparingInt((ConstraintChoices choice) -> choice.inputIndexes().size())
                .thenComparingInt(ConstraintChoices::constraintIndex));
        var assignment = new int[constraints.size()];
        var budget = new SearchBudget();
        if (!assign(0, choices, assignment, 0L, budget)) {
            return null;
        }

        var result = new ArrayList<T>(constraints.size());
        for (int inputIndex : assignment) {
            result.add(inputs.get(inputIndex));
        }
        return List.copyOf(result);
    }

    private static boolean assign(int depth, List<ConstraintChoices> choices,
                                  int[] assignment, long usedInputs,
                                  SearchBudget budget) {
        if (depth == choices.size()) {
            return true;
        }
        var choice = choices.get(depth);
        for (int inputIndex : choice.inputIndexes()) {
            long bit = 1L << inputIndex;
            if ((usedInputs & bit) != 0) {
                continue;
            }
            if (!budget.tryNode()) {
                // The budget only shrinks; no later candidate can be tried.
                break;
            }
            assignment[choice.constraintIndex()] = inputIndex;
            if (assign(depth + 1, choices, assignment, usedInputs | bit, budget)) {
                return true;
            }
        }
        return false;
    }

    private record ConstraintChoices(int constraintIndex, List<Integer> inputIndexes) {
    }

    private static final class SearchBudget {
        private int remaining = MAX_SEARCH_NODES;

        boolean tryNode() {
            if (remaining == 0) {
                return false;
            }
            remaining--;
            return true;
        }
    }
}
