package com.moakiee.ae2lt.packaged.logic.multiblock.occultism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

final class OccultismRitualInputMatcher {

    private static final int MAX_SEARCH_NODES = 4096;

    private OccultismRitualInputMatcher() {
    }

    @Nullable
    static <T> Match<T> match(List<T> units,
                              Predicate<T> activationMatcher,
                              List<Predicate<T>> ingredientMatchers) {
        if (units.size() != ingredientMatchers.size() + 1) {
            return null;
        }

        var budget = new SearchBudget();
        for (int i = 0; i < units.size(); i++) {
            if (!budget.tryNode() || !activationMatcher.test(units.get(i))) {
                continue;
            }

            var match = matchIngredients(units, i, ingredientMatchers, budget);
            if (match != null) {
                return new Match<>(units.get(i), match);
            }
        }

        return null;
    }

    @Nullable
    private static <T> List<T> matchIngredients(List<T> units,
                                                int activationIndex,
                                                List<Predicate<T>> ingredientMatchers,
                                                SearchBudget budget) {
        var used = new boolean[units.size()];
        used[activationIndex] = true;

        var matcherOrder = new ArrayList<Integer>(ingredientMatchers.size());
        for (int i = 0; i < ingredientMatchers.size(); i++) {
            matcherOrder.add(i);
        }
        matcherOrder.sort((left, right) -> Integer.compare(
                candidateCount(units, used, ingredientMatchers.get(left)),
                candidateCount(units, used, ingredientMatchers.get(right))));

        var assignments = new ArrayList<T>(Collections.nCopies(ingredientMatchers.size(), null));
        if (!assignIngredients(units, ingredientMatchers, matcherOrder, used, assignments, 0, budget)) {
            return null;
        }
        return List.copyOf(assignments);
    }

    private static <T> int candidateCount(List<T> units, boolean[] used, Predicate<T> matcher) {
        int count = 0;
        for (int i = 0; i < units.size(); i++) {
            if (!used[i] && matcher.test(units.get(i))) {
                count++;
            }
        }
        return count;
    }

    private static <T> boolean assignIngredients(List<T> units,
                                                 List<Predicate<T>> matchers,
                                                 List<Integer> matcherOrder,
                                                 boolean[] used,
                                                 List<T> assignments,
                                                 int depth,
                                                 SearchBudget budget) {
        if (depth == matcherOrder.size()) {
            return true;
        }
        int matcherIndex = matcherOrder.get(depth);
        var matcher = matchers.get(matcherIndex);
        for (int unitIndex = 0; unitIndex < units.size(); unitIndex++) {
            if (used[unitIndex] || !matcher.test(units.get(unitIndex)) || !budget.tryNode()) {
                continue;
            }
            used[unitIndex] = true;
            assignments.set(matcherIndex, units.get(unitIndex));
            if (assignIngredients(units, matchers, matcherOrder, used, assignments, depth + 1, budget)) {
                return true;
            }
            assignments.set(matcherIndex, null);
            used[unitIndex] = false;
        }
        return false;
    }

    record Match<T>(T activation, List<T> ingredients) {
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
