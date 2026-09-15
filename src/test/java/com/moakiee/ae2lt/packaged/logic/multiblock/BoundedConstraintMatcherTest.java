package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class BoundedConstraintMatcherTest {

    @Test
    void backtracksAndReturnsOriginalConstraintOrder() {
        var match = BoundedConstraintMatcher.matchExactly(
                List.of("specific", "broad-only"),
                List.of(
                        value -> value.equals("specific") || value.equals("broad-only"),
                        value -> value.equals("specific")));

        assertEquals(List.of("broad-only", "specific"), match);
    }

    @Test
    void rejectsUnusedInputsAndUnboundedSearchDomain() {
        assertNull(BoundedConstraintMatcher.matchExactly(
                List.of("required", "extra"),
                List.of(value -> value.equals("required"))));

        var inputs = new ArrayList<Integer>();
        var constraints = new ArrayList<java.util.function.Predicate<Integer>>();
        for (int i = 0; i < 65; i++) {
            inputs.add(i);
            constraints.add(value -> true);
        }
        assertNull(BoundedConstraintMatcher.matchExactly(inputs, constraints));
    }
}
