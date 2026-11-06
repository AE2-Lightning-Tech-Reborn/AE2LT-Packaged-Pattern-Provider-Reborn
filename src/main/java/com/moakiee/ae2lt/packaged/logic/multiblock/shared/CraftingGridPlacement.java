package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class CraftingGridPlacement {

    private CraftingGridPlacement() {
    }

    public static <T> List<Assignment<T>> place(int recipeWidth, int gridSize, List<@Nullable T> recipeCells) {
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

    public record Assignment<T>(int slot, T value) {
    }
}
