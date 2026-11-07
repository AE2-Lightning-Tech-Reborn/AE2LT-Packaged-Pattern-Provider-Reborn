package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import org.jetbrains.annotations.Nullable;

public final class IngredientAssignment {

    private IngredientAssignment() {
    }

    /**
     * Consume every supplied unit and retain the first assignment in stable candidate-count order.
     * The matcher must return a stable result for each ingredient/key pair during this call.
     */
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

        // Index each key identity once; repeated copies reuse the same predicate result.
        var keys = new ArrayList<K>();
        var keyIndexes = new IdentityHashMap<K, Integer>();
        var keyByUnit = new int[units.size()];
        for (int unit = 0; unit < units.size(); unit++) {
            var key = units.get(unit);
            var keyIndex = keyIndexes.get(key);
            if (keyIndex == null) {
                keyIndex = keys.size();
                keys.add(key);
                keyIndexes.put(key, keyIndex);
            }
            keyByUnit[unit] = keyIndex;
        }

        var compatibility = new boolean[ingredients.size()][];
        var rows = new IdentityHashMap<I, boolean[]>();
        var candidateCounts = new int[ingredients.size()];
        var orderedIngredientIndexes = new ArrayList<Integer>(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            var ingredient = ingredients.get(i);
            var row = rows.get(ingredient);
            if (row == null) {
                row = new boolean[units.size()];
                // Equal objects can still have different identity-sensitive matching semantics.
                var matchesByKey = new boolean[keys.size()];
                for (int key = 0; key < keys.size(); key++) {
                    matchesByKey[key] = matcher.test(ingredient, keys.get(key));
                }
                for (int unit = 0; unit < units.size(); unit++) {
                    row[unit] = matchesByKey[keyByUnit[unit]];
                }
                rows.put(ingredient, row);
            }
            compatibility[i] = row;
            // Count copies, not distinct keys, to preserve the original stable search order.
            for (boolean matches : row) if (matches) candidateCounts[i]++;
            orderedIngredientIndexes.add(i);
        }
        orderedIngredientIndexes.sort(Comparator.comparingInt(index -> candidateCounts[index]));

        var ingredientByUnit = new int[units.size()];
        var chosenUnitByIngredient = new int[ingredients.size()];
        Arrays.fill(ingredientByUnit, -1);
        Arrays.fill(chosenUnitByIngredient, -1);
        var locked = new boolean[units.size()];
        var visited = new boolean[units.size()];
        boolean reordered = false;
        // Reject impossible graphs through augmenting paths instead of enumerating permutations.
        for (int ingredient : orderedIngredientIndexes) {
            if (assignFreeUnit(ingredient, compatibility, ingredientByUnit, chosenUnitByIngredient)) continue;
            reordered = true;
            Arrays.fill(visited, false);
            if (!augment(ingredient, compatibility, locked, visited, ingredientByUnit, chosenUnitByIngredient)) {
                return null;
            }
        }
        // A complete greedy assignment already is the former search's first solution.
        if (reordered) {
            preserveSelectionOrder(orderedIngredientIndexes, compatibility, locked, visited,
                    ingredientByUnit, chosenUnitByIngredient);
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

    private static boolean assignFreeUnit(int ingredient, boolean[][] compatibility,
                                           int[] ingredientByUnit, int[] chosenUnitByIngredient) {
        for (int unit = 0; unit < ingredientByUnit.length; unit++) {
            if (ingredientByUnit[unit] < 0 && compatibility[ingredient][unit]) {
                ingredientByUnit[unit] = ingredient;
                chosenUnitByIngredient[ingredient] = unit;
                return true;
            }
        }
        return false;
    }

    private static void preserveSelectionOrder(List<Integer> ingredientOrder, boolean[][] compatibility,
                                                boolean[] locked, boolean[] visited,
                                                int[] ingredientByUnit, int[] chosenUnitByIngredient) {
        // A perfect matching can choose different variants than the former depth-first search.
        // Fix each prefix to its earliest feasible unit, moving only the unlocked suffix.
        // At most n^2 attempts each visit at most n^2 edges, bounding this phase by O(n^4).
        for (int ingredient : ingredientOrder) {
            for (int unit = 0; unit < ingredientByUnit.length; unit++) {
                if (locked[unit] || !compatibility[ingredient][unit]) continue;
                if (ingredientByUnit[unit] == ingredient) {
                    locked[unit] = true;
                    break;
                }
                int displaced = ingredientByUnit[unit];
                int previousUnit = chosenUnitByIngredient[ingredient];
                ingredientByUnit[previousUnit] = -1;
                ingredientByUnit[unit] = ingredient;
                chosenUnitByIngredient[ingredient] = unit;
                chosenUnitByIngredient[displaced] = -1;
                locked[unit] = true;
                Arrays.fill(visited, false);
                if (augment(displaced, compatibility, locked, visited, ingredientByUnit, chosenUnitByIngredient)) {
                    break;
                }
                // A failed augmentation changes no pairs; restore just the tentative displacement.
                locked[unit] = false;
                ingredientByUnit[previousUnit] = ingredient;
                ingredientByUnit[unit] = displaced;
                chosenUnitByIngredient[ingredient] = previousUnit;
                chosenUnitByIngredient[displaced] = unit;
            }
        }
    }

    private static boolean augment(int ingredient, boolean[][] compatibility,
                                    boolean[] locked, boolean[] visited,
                                    int[] ingredientByUnit, int[] chosenUnitByIngredient) {
        // Repeated/common ingredients usually have a free compatible unit; avoid moving a chain.
        for (int unit = 0; unit < ingredientByUnit.length; unit++) {
            if (ingredientByUnit[unit] < 0 && !locked[unit] && !visited[unit]
                    && compatibility[ingredient][unit]) {
                ingredientByUnit[unit] = ingredient;
                chosenUnitByIngredient[ingredient] = unit;
                return true;
            }
        }
        for (int unit = 0; unit < ingredientByUnit.length; unit++) {
            if (locked[unit] || visited[unit] || !compatibility[ingredient][unit]) continue;
            visited[unit] = true;
            if (ingredientByUnit[unit] < 0
                    || augment(ingredientByUnit[unit], compatibility, locked, visited,
                            ingredientByUnit, chosenUnitByIngredient)) {
                ingredientByUnit[unit] = ingredient;
                chosenUnitByIngredient[ingredient] = unit;
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
