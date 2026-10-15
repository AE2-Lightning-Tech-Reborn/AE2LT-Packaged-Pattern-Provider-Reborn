package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.packaged.logic.multiblock.BoundedConstraintMatcher;

final class ExtendedCraftingTableGridPlanner {

    private ExtendedCraftingTableGridPlanner() {
    }

    static <T> List<Assignment<T>> place(int recipeWidth, int gridSize, List<@Nullable T> recipeCells) {
        if (recipeWidth <= 0 || gridSize <= 0 || recipeWidth > gridSize) {
            return List.of();
        }

        var assignments = new ArrayList<Assignment<T>>();
        for (int recipeIndex = 0; recipeIndex < recipeCells.size(); recipeIndex++) {
            var value = recipeCells.get(recipeIndex);
            if (value == null) {
                continue;
            }
            int recipeX = recipeIndex % recipeWidth;
            int recipeY = recipeIndex / recipeWidth;
            if (recipeY >= gridSize) {
                return List.of();
            }
            assignments.add(new Assignment<>(recipeX + recipeY * gridSize, value));
        }
        return List.copyOf(assignments);
    }

    static <I, T> List<Assignment<T>> placeMatching(int recipeWidth,
                                                    int gridSize,
                                                    List<@Nullable I> recipeCells,
                                                    List<T> inputs,
                                                    BiPredicate<I, T> matcher) {
        if (recipeWidth <= 0 || gridSize <= 0 || recipeWidth > gridSize) {
            return List.of();
        }

        var cellIndexes = new ArrayList<Integer>();
        for (int recipeIndex = 0; recipeIndex < recipeCells.size(); recipeIndex++) {
            if (recipeCells.get(recipeIndex) == null) {
                continue;
            }

            if (recipeIndex / recipeWidth >= gridSize) {
                return List.of();
            }
            cellIndexes.add(recipeIndex);
        }
        if (cellIndexes.size() != inputs.size()) {
            return List.of();
        }

        cellIndexes.sort(Comparator.comparingInt(index ->
                candidateCount(recipeCells.get(index), inputs, matcher)));

        var used = new boolean[inputs.size()];
        var chosenInputIndexes = new int[cellIndexes.size()];
        if (!assignInputs(0, recipeCells, inputs, matcher, cellIndexes, used, chosenInputIndexes)) {
            return List.of();
        }

        var assignments = new ArrayList<Assignment<T>>();
        for (int i = 0; i < chosenInputIndexes.length; i++) {
            int recipeIndex = cellIndexes.get(i);
            int targetSlot = (recipeIndex % recipeWidth) + (recipeIndex / recipeWidth) * gridSize;
            assignments.add(new Assignment<>(targetSlot, inputs.get(chosenInputIndexes[i])));
        }
        assignments.sort(Comparator.comparingInt(Assignment::slot));
        return List.copyOf(assignments);
    }

    /** Shared Ender/Flux layout: preserve shaped gaps and padded 3x3 recipes. */
    @Nullable
    static <I, T> List<Assignment<T>> placeCrafter(int width, int height, List<I> ingredients,
                                                List<T> inputs, Predicate<I> isEmpty,
                                                BiPredicate<I, T> matcher) {
        if (ingredients.isEmpty() || ingredients.size() > 9
                || inputs.size() != ingredients.stream().filter(isEmpty.negate()).count()) {
            return null;
        }
        boolean shaped = width > 0 && height > 0 && width <= 3 && height <= 3;
        if (!shaped && ingredients.size() == 9) {
            width = height = 3;
            shaped = true;
        }
        var slots = new ArrayList<Integer>();
        var constraints = new ArrayList<Predicate<T>>();
        int cells = shaped ? Math.min(ingredients.size(), width * height) : ingredients.size();
        for (int index = 0; index < cells; index++) {
            var ingredient = ingredients.get(index);
            if (!isEmpty.test(ingredient)) {
                slots.add(shaped ? index / width * 3 + index % width : slots.size());
                constraints.add(input -> matcher.test(ingredient, input));
            }
        }
        var assigned = BoundedConstraintMatcher.matchExactly(inputs, constraints);
        if (assigned == null) {
            return null;
        }
        var placements = new ArrayList<Assignment<T>>(assigned.size());
        for (int index = 0; index < assigned.size(); index++) {
            placements.add(new Assignment<>(slots.get(index), assigned.get(index)));
        }
        return List.copyOf(placements);
    }

    private static <I, T> int candidateCount(@Nullable I ingredient,
                                             List<T> inputs,
                                             BiPredicate<I, T> matcher) {
        if (ingredient == null) {
            return 0;
        }
        int count = 0;
        for (var input : inputs) {
            if (matcher.test(ingredient, input)) {
                count++;
            }
        }
        return count;
    }

    private static <I, T> boolean assignInputs(int cellNumber,
                                               List<@Nullable I> recipeCells,
                                               List<T> inputs,
                                               BiPredicate<I, T> matcher,
                                               List<Integer> cellIndexes,
                                               boolean[] used,
                                               int[] chosenInputIndexes) {
        if (cellNumber >= cellIndexes.size()) {
            return true;
        }

        var ingredient = recipeCells.get(cellIndexes.get(cellNumber));
        if (ingredient == null) {
            return false;
        }

        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            if (used[inputIndex] || !matcher.test(ingredient, inputs.get(inputIndex))) {
                continue;
            }

            used[inputIndex] = true;
            chosenInputIndexes[cellNumber] = inputIndex;
            if (assignInputs(cellNumber + 1, recipeCells, inputs, matcher, cellIndexes, used, chosenInputIndexes)) {
                return true;
            }
            used[inputIndex] = false;
        }
        return false;
    }

    record Assignment<T>(int slot, T value) {
    }
}
