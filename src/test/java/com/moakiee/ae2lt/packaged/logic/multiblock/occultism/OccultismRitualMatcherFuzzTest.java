package com.moakiee.ae2lt.packaged.logic.multiblock.occultism;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Randomized differential test for the Occultism ritual matcher: on sizes
 * where the 4096-node budget cannot be exhausted, satisfiability must agree
 * with a brute-force oracle, the activation unit must be exclusive, and any
 * returned match must satisfy every predicate.
 */
class OccultismRitualMatcherFuzzTest {

    private static final int MAX_ORACLE_SIZE = 6;
    private static final int TRIALS = 2000;

    private record Unit(int id) {
    }

    @Test
    void agreesWithBruteForceOnRandomMatrices() {
        var random = new Random(20260909L);
        for (int trial = 0; trial < TRIALS; trial++) {
            final int t = trial;
            int unitCount = 1 + random.nextInt(MAX_ORACLE_SIZE);
            int ingredientCount = unitCount - 1;
            double density = 0.25 + 0.5 * random.nextDouble();

            List<Unit> units = new ArrayList<>();
            for (int i = 0; i < unitCount; i++) {
                units.add(new Unit(i));
            }
            boolean[] activationRow = new boolean[unitCount];
            for (int i = 0; i < unitCount; i++) {
                activationRow[i] = random.nextDouble() < density;
            }
            Predicate<Unit> activation = unit -> activationRow[unit.id()];

            boolean[][] matrix = new boolean[ingredientCount][unitCount];
            List<Predicate<Unit>> ingredientMatchers = new ArrayList<>();
            for (int c = 0; c < ingredientCount; c++) {
                boolean[] row = matrix[c];
                for (int i = 0; i < unitCount; i++) {
                    row[i] = random.nextDouble() < density;
                }
                int constraintId = c;
                ingredientMatchers.add(unit -> row[unit.id()]);
            }

            var match = OccultismRitualInputMatcher.match(units, activation, ingredientMatchers);

            if (match != null) {
                assertTrue(activation.test(match.activation()),
                        () -> "trial " + t + ": activation unit fails activation matcher");
                Set<Integer> used = new HashSet<>();
                assertTrue(used.add(match.activation().id()));
                assertEquals(ingredientCount, match.ingredients().size());
                for (int c = 0; c < ingredientCount; c++) {
                    final int constraintId = c;
                    Unit assigned = match.ingredients().get(constraintId);
                    assertTrue(ingredientMatchers.get(constraintId).test(assigned),
                            () -> "trial " + t + ": ingredient " + constraintId + " violated");
                    assertTrue(used.add(assigned.id()),
                            () -> "trial " + t + ": unit reused");
                }
            }

            assertEquals(oracle(units, activation, ingredientMatchers), match != null,
                    () -> "trial " + t + ": oracle mismatch");
        }
    }

    @Test
    void activationExclusiveWhenOnlyOneUnitQualifies() {
        // Every ingredient matcher also accepts the activation unit; the
        // matcher must still succeed by leaving it unassigned.
        List<Unit> units = List.of(new Unit(0), new Unit(1), new Unit(2));
        Predicate<Unit> all = unit -> true;
        var match = OccultismRitualInputMatcher.match(units, all, List.of(all, all));
        assertTrue(match != null);
        assertEquals(0, match.activation().id());
    }

    private static boolean oracle(List<Unit> units, Predicate<Unit> activation,
                                  List<Predicate<Unit>> ingredientMatchers) {
        if (units.size() != ingredientMatchers.size() + 1) {
            return false;
        }
        for (int a = 0; a < units.size(); a++) {
            if (!activation.test(units.get(a))) {
                continue;
            }
            var used = new boolean[units.size()];
            used[a] = true;
            if (search(units, ingredientMatchers, 0, used)) {
                return true;
            }
        }
        return false;
    }

    private static boolean search(List<Unit> units, List<Predicate<Unit>> matchers,
                                  int matcherIndex, boolean[] used) {
        if (matcherIndex == matchers.size()) {
            return true;
        }
        for (int i = 0; i < units.size(); i++) {
            if (used[i] || !matchers.get(matcherIndex).test(units.get(i))) {
                continue;
            }
            used[i] = true;
            if (search(units, matchers, matcherIndex + 1, used)) {
                return true;
            }
            used[i] = false;
        }
        return false;
    }
}
