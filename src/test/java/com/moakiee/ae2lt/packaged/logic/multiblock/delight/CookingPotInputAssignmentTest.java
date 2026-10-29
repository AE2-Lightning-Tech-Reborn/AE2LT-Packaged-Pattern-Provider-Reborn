package com.moakiee.ae2lt.packaged.logic.multiblock.delight;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CookingPotInputAssignmentTest {
    @Test void reassignsOverlappingIngredientsWithoutGreedyRejection() {
        assertEquals(List.of("potato", "carrot"), CookingPotInputAssignment.assign(
                List.of("vegetable", "carrot"), List.of("carrot", "potato"),
                (ingredient, item) -> ingredient.equals("vegetable") || ingredient.equals(item)));
    }
    @Test void all81CopiesAreDistinctUnits() {
        var units = java.util.Collections.nCopies(81, "carrot");
        assertEquals(units, CookingPotInputAssignment.assign(units, units, String::equals));
        assertNull(CookingPotInputAssignment.assign(units, units.subList(0, 80), String::equals));
    }
    @Test void impossibleOverlapping81SlotRecipeHasPolynomialCost() {
        var ingredients = new ArrayList<Integer>();
        var units = new ArrayList<Integer>();
        for (int i = 0; i < 81; i++) { ingredients.add(i); units.add(i); }
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertNull(CookingPotInputAssignment.assign(
                ingredients, units, (ingredient, unit) -> ingredient < 41 ? unit < 40 : unit >= 40)));
    }
}
