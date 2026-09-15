package com.moakiee.ae2lt.packaged.logic.multiblock.de;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class DraconicFusionInputMatcherTest {

    @Test
    void backtracksWhenBroadIngredientOverlapsNarrowIngredient() {
        var units = List.of("specific", "broad", "broad");
        var match = DraconicFusionInputMatcher.match(
                units,
                1,
                value -> value.equals("specific"),
                List.<Predicate<String>>of(
                        value -> value.equals("specific") || value.equals("broad"),
                        value -> value.equals("broad")));
        assertNotNull(match);
        assertEquals(List.of("broad", "broad"), match.ingredients());
    }

    @Test
    void requiresExactCatalystGroupAndRejectsShortfall() {
        var units = List.of("catalyst", "ingredient");
        assertNull(DraconicFusionInputMatcher.match(
                units, 2, value -> value.equals("catalyst"),
                List.<Predicate<String>>of(value -> value.equals("ingredient"))));
    }

    @Test
    void rejectsWrongUnitCount() {
        var units = List.of("a", "b");
        assertNull(DraconicFusionInputMatcher.match(
                units, 1, value -> true, List.of()));
        assertNull(DraconicFusionInputMatcher.match(
                units, 1, value -> true,
                List.<Predicate<String>>of(value -> true, value -> true)));
    }

    /**
     * Budget stress: an unsatisfiable system whose every prefix stays
     * satisfiable would explode combinatorially without the node budget.
     * The bounded matcher must fail closed promptly instead of hanging.
     */
    @Test
    void failsClosedWhenSearchBudgetIsExhausted() {
        var units = new ArrayList<String>();
        for (int i = 0; i < 21; i++) {
            units.add("unit-" + i);
        }
        var constraints = new ArrayList<Predicate<String>>();
        for (int i = 0; i < 20; i++) {
            constraints.add(value -> true);
        }
        constraints.add(value -> false);

        long start = System.nanoTime();
        var match = DraconicFusionInputMatcher.match(
                units, 1, value -> true, constraints);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertNull(match);
        assertTrue(elapsedMillis < 2000,
                "budgeted matcher should fail closed quickly, took " + elapsedMillis + "ms");
    }

    @Test
    void satisfiableBroadSystemStillSucceeds() {
        var units = new ArrayList<String>();
        for (int i = 0; i < 21; i++) {
            units.add("unit-" + i);
        }
        var constraints = new ArrayList<Predicate<String>>();
        for (int i = 0; i < 20; i++) {
            constraints.add(value -> true);
        }
        var match = DraconicFusionInputMatcher.match(
                units, 1, value -> true, constraints);
        assertNotNull(match);
        assertEquals(20, match.ingredients().size());
    }
}
