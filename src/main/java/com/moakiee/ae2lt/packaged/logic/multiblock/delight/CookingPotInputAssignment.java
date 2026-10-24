package com.moakiee.ae2lt.packaged.logic.multiblock.delight;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

/** Bounded bipartite matching: 81 overlapping ingredients must not cause factorial backtracking. */
final class CookingPotInputAssignment {
    private CookingPotInputAssignment() {}

    static <I, K> List<K> assign(List<I> ingredients, List<K> units, BiPredicate<I, K> matches) {
        int size = ingredients.size();
        if (size == 0 || size > 81 || units.size() != size) return null;
        var edges = new boolean[size][size];
        for (int i = 0; i < size; i++) for (int j = 0; j < size; j++)
            edges[i][j] = matches.test(ingredients.get(i), units.get(j));
        int[] ingredientForUnit = new int[size];
        Arrays.fill(ingredientForUnit, -1);
        for (int i = 0; i < size; i++)
            if (!augment(i, edges, ingredientForUnit, new boolean[size])) return null;
        int[] unitForIngredient = new int[size];
        for (int j = 0; j < size; j++) unitForIngredient[ingredientForUnit[j]] = j;
        return Arrays.stream(unitForIngredient).mapToObj(units::get).toList();
    }

    private static boolean augment(int ingredient, boolean[][] edges, int[] assigned, boolean[] seen) {
        for (int unit = 0; unit < assigned.length; unit++) {
            if (!edges[ingredient][unit] || seen[unit]) continue;
            seen[unit] = true;
            if (assigned[unit] < 0 || augment(assigned[unit], edges, assigned, seen)) {
                assigned[unit] = ingredient;
                return true;
            }
        }
        return false;
    }
}
