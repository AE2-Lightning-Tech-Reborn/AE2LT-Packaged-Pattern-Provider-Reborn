package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import org.jetbrains.annotations.Nullable;

public final class IngredientAssignment {

    private IngredientAssignment() {
    }

    @Nullable
    public static <I, K> List<K> assign(List<I> ingredients,
                                 List<Input<K>> inputs,
                                 long amountPerIngredient,
                                 BiPredicate<I, K> matcher) {
        Objects.requireNonNull(ingredients, "ingredients");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(matcher, "matcher");
        if (amountPerIngredient <= 0) {
            return null;
        }

        var units = new ArrayList<K>();
        for (var input : inputs) {
            if (input.amount() == 0) {
                continue;
            }
            if (input.amount() % amountPerIngredient != 0) {
                return null;
            }
            long copies = input.amount() / amountPerIngredient;
            if (copies > ingredients.size() - units.size()) {
                return null;
            }
            for (long i = 0; i < copies; i++) {
                units.add(input.key());
            }
        }
        if (units.size() != ingredients.size()) {
            return null;
        }

        // Augmenting paths also bound failed 81-slot upgrades. Backtracking can
        // explore factorially many permutations when overlapping tags are short.
        var ingredientByUnit = new int[units.size()];
        Arrays.fill(ingredientByUnit, -1);
        for (int ingredient = 0; ingredient < ingredients.size(); ingredient++) {
            if (!assignOne(ingredient, ingredients, units, matcher, ingredientByUnit, new boolean[units.size()])) {
                return null;
            }
        }
        var chosenUnitByIngredient = new int[ingredients.size()];
        Arrays.fill(chosenUnitByIngredient, -1);
        for (int unit = 0; unit < ingredientByUnit.length; unit++) {
            if (ingredientByUnit[unit] >= 0) chosenUnitByIngredient[ingredientByUnit[unit]] = unit;
        }

        var assigned = new ArrayList<K>(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            int unitIndex = chosenUnitByIngredient[i];
            if (unitIndex < 0) {
                return null;
            }
            assigned.add(units.get(unitIndex));
        }
        return List.copyOf(assigned);
    }

    private static <I, K> boolean assignOne(int ingredientIndex,
                                            List<I> ingredients,
                                            List<K> units,
                                            BiPredicate<I, K> matcher,
                                            int[] ingredientByUnit,
                                            boolean[] visited) {
        var ingredient = ingredients.get(ingredientIndex);
        for (int unitIndex = 0; unitIndex < units.size(); unitIndex++) {
            if (visited[unitIndex] || !matcher.test(ingredient, units.get(unitIndex))) {
                continue;
            }
            visited[unitIndex] = true;
            if (ingredientByUnit[unitIndex] < 0 || assignOne(ingredientByUnit[unitIndex], ingredients, units, matcher,
                    ingredientByUnit, visited)) {
                ingredientByUnit[unitIndex] = ingredientIndex;
                return true;
            }
        }
        return false;
    }

    public record Input<K>(K key, long amount) {
        public Input {
            Objects.requireNonNull(key, "key");
            if (amount < 0) {
                throw new IllegalArgumentException("amount must be non-negative");
            }
        }
    }
}
