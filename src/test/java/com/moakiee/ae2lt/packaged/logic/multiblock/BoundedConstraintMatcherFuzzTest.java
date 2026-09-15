package com.moakiee.ae2lt.packaged.logic.multiblock;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Randomized differential test: the bounded matcher must agree with a
 * brute-force oracle on satisfiability for sizes where the 4096-node budget
 * can never be exhausted, and must never return an invalid assignment.
 */
class BoundedConstraintMatcherFuzzTest {

    /**
     * Nodes the backtracker can consume is bounded by the number of partial
     * injective assignments explored: sum_{k=0..m} P(n, k). Sizes are capped
     * where that bound stays below the 4096-node budget so budget exhaustion
     * cannot masquerade as unsatisfiable and break oracle agreement.
     */
    private static final int MAX_ORACLE_SIZE = 6;
    private static final int TRIALS = 2000;

    private record Unit(int id) {
    }

    @Test
    void agreesWithBruteForceOnRandomMatrices() {
        var random = new Random(20260909L);
        for (int trial = 0; trial < TRIALS; trial++) {
            final int t = trial;
            int inputCount = 1 + random.nextInt(MAX_ORACLE_SIZE);
            int constraintCount = 1 + random.nextInt(inputCount);
            double density = 0.25 + 0.5 * random.nextDouble();

            List<Unit> inputs = new ArrayList<>();
            for (int i = 0; i < inputCount; i++) {
                inputs.add(new Unit(i));
            }
            boolean[][] matrix = new boolean[constraintCount][inputCount];
            List<Predicate<Unit>> constraints = new ArrayList<>();
            for (int c = 0; c < constraintCount; c++) {
                boolean[] row = matrix[c];
                for (int i = 0; i < inputCount; i++) {
                    row[i] = random.nextDouble() < density;
                }
                int constraintId = c;
                constraints.add(unit -> row[unit.id()]);
            }

            var exact = BoundedConstraintMatcher.matchExactly(inputs, constraints);
            var within = BoundedConstraintMatcher.matchWithin(inputs, constraints);

            if (exact != null) {
                assertValid(inputs, constraints, exact, "exact", trial);
            }
            if (within != null) {
                assertValid(inputs, constraints, within, "within", trial);
            }

            boolean oracleExact = oracleExact(inputs, constraints);
            boolean oracleWithin = oracleWithin(inputs, constraints);
            assertEquals(oracleExact, exact != null,
                    () -> "trial " + t + ": exact mismatch");
            assertEquals(oracleWithin, within != null,
                    () -> "trial " + t + ": within mismatch");
        }
    }

    @Test
    void oversizeInputFailsClosed() {
        List<Unit> inputs = new ArrayList<>();
        List<Predicate<Unit>> constraints = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            final int id = i;
            inputs.add(new Unit(id));
            constraints.add(unit -> unit.id() == id);
        }
        assertEquals(null, BoundedConstraintMatcher.matchExactly(inputs, constraints));
        assertEquals(null, BoundedConstraintMatcher.matchWithin(inputs, constraints));
    }

    @Test
    void emptyConstraintListSatisfiesWithEmptyAssignment() {
        List<Unit> inputs = List.of(new Unit(0));
        var result = BoundedConstraintMatcher.matchWithin(inputs, List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(null, BoundedConstraintMatcher.matchExactly(inputs, List.of()));
    }

    private static void assertValid(List<Unit> inputs,
                                    List<Predicate<Unit>> constraints,
                                    List<Unit> assignment,
                                    String variant,
                                    int trial) {
        assertEquals(constraints.size(), assignment.size(),
                () -> "trial " + trial + ": " + variant + " arity");
        Set<Integer> used = new HashSet<>();
        for (int c = 0; c < constraints.size(); c++) {
            final int constraintId = c;
            Unit assigned = assignment.get(c);
            assertTrue(inputs.contains(assigned),
                    () -> "trial " + trial + ": " + variant + " unknown unit");
            assertTrue(constraints.get(constraintId).test(assigned),
                    () -> "trial " + trial + ": " + variant + " constraint " + constraintId + " violated");
            assertTrue(used.add(assigned.id()),
                    () -> "trial " + trial + ": " + variant + " unit reused");
        }
    }

    private static boolean oracleExact(List<Unit> inputs, List<Predicate<Unit>> constraints) {
        if (inputs.size() != constraints.size()) {
            return false;
        }
        return oracleSearch(inputs, constraints, 0, new boolean[inputs.size()]);
    }

    private static boolean oracleWithin(List<Unit> inputs, List<Predicate<Unit>> constraints) {
        if (inputs.size() < constraints.size()) {
            return false;
        }
        return oracleSearch(inputs, constraints, 0, new boolean[inputs.size()]);
    }

    private static boolean oracleSearch(List<Unit> inputs, List<Predicate<Unit>> constraints,
                                        int constraintIndex, boolean[] used) {
        if (constraintIndex == constraints.size()) {
            return true;
        }
        for (int i = 0; i < inputs.size(); i++) {
            if (used[i] || !constraints.get(constraintIndex).test(inputs.get(i))) {
                continue;
            }
            used[i] = true;
            if (oracleSearch(inputs, constraints, constraintIndex + 1, used)) {
                return true;
            }
            used[i] = false;
        }
        return false;
    }
}
