package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CraftingGridPlacementTest {

    @Test
    void placesCompactRecipeRowsIntoTopLeftOfLargerTableGrid() {
        var slots = CraftingGridPlacement.place(3, 5,
                List.of("a", "b", "c", "d", "e", "f"));

        assertEquals(List.of(
                new CraftingGridPlacement.Assignment<>(0, "a"),
                new CraftingGridPlacement.Assignment<>(1, "b"),
                new CraftingGridPlacement.Assignment<>(2, "c"),
                new CraftingGridPlacement.Assignment<>(5, "d"),
                new CraftingGridPlacement.Assignment<>(6, "e"),
                new CraftingGridPlacement.Assignment<>(7, "f")), slots);
    }

    @Test
    void leavesEmptyRecipeCellsUnassigned() {
        var slots = CraftingGridPlacement.place(2, 7,
                Arrays.asList("a", null, null, "d"));

        assertEquals(List.of(
                new CraftingGridPlacement.Assignment<>(0, "a"),
                new CraftingGridPlacement.Assignment<>(8, "d")), slots);
    }

    @Test
    void rejectsShapesThatDoNotFitTheTargetGrid() {
        assertEquals(List.of(), CraftingGridPlacement.place(4, 3, List.of("a")));
        assertEquals(List.of(), CraftingGridPlacement.place(1, 2, List.of("a", "b", "c")));
    }

    @Test
    void rejectsInvalidDimensions() {
        assertEquals(List.of(), CraftingGridPlacement.place(0, 3, List.of("a")));
        assertEquals(List.of(), CraftingGridPlacement.place(2, 0, List.of("a")));
    }
}
