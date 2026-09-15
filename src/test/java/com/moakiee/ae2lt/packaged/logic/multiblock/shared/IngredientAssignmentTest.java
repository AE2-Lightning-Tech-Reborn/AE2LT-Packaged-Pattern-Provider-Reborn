package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IngredientAssignmentTest {
    @Test
    void preservesVariantIdentityAndIngredientOrder() {
        var actual = IngredientAssignment.assign(List.of("any", "named"),
                List.of(new IngredientAssignment.Input<>("iron:named", 4),
                        new IngredientAssignment.Input<>("iron:plain", 4)), 4,
                (ingredient, key) -> ingredient.equals("any") || key.equals("iron:named"));
        assertEquals(List.of("iron:plain", "iron:named"), actual);
    }

    @Test
    void consumesEveryInputExactlyAndRejectsInvalidBatchQuantities() {
        assertNull(assign(2, List.of(new IngredientAssignment.Input<>("iron", 3)), 2));
        assertNull(assign(2, List.of(new IngredientAssignment.Input<>("iron", 6)), 2));
        assertNull(assign(2, List.of(new IngredientAssignment.Input<>("iron", 2)), 2));
        assertNull(assign(2, List.of(new IngredientAssignment.Input<>("iron", 4)), 0));
        assertEquals(List.of("iron", "iron"), assign(2,
                List.of(new IngredientAssignment.Input<>("ignored", 0), new IngredientAssignment.Input<>("iron", 4)), 2));
    }

    @Test
    void veryLargeCountsDoNotOverflowOrExpandIntoAnUnboundedList() {
        assertEquals(List.of("iron"), assign(1, List.of(new IngredientAssignment.Input<>("iron", Long.MAX_VALUE)), Long.MAX_VALUE));
        assertNull(assign(9, List.of(new IngredientAssignment.Input<>("iron", Long.MAX_VALUE)), 1));
    }

    @Test
    void randomizedAssignmentsAgreeWithExhaustivePermutationOracle() {
        var random = new Random(9052026);
        for (int sample = 0; sample < 600; sample++) {
            int size = 1 + random.nextInt(6);
            var graph = new boolean[size][size];
            for (var row : graph) for (int j = 0; j < size; j++) row[j] = random.nextBoolean();
            var indexes = IntStream.range(0, size).boxed().toList();
            var inputs = indexes.stream().map(i -> new IngredientAssignment.Input<>(i, 7)).toList();
            var result = IngredientAssignment.assign(indexes, inputs, 7, (i, j) -> graph[i][j]);
            boolean possible = permutations(new ArrayList<>(), indexes, graph);
            assertEquals(possible, result != null, "sample " + sample);
            if (result != null) {
                assertEquals(size, result.stream().distinct().count());
                for (int i = 0; i < size; i++) assertTrue(graph[i][result.get(i)]);
            }
        }
    }

    private static boolean permutations(List<Integer> prefix, List<Integer> remaining, boolean[][] graph) {
        if (remaining.isEmpty()) {
            for (int i = 0; i < prefix.size(); i++) if (!graph[i][prefix.get(i)]) return false;
            return true;
        }
        for (var next : remaining) {
            var tail = new ArrayList<>(remaining);
            tail.remove(next);
            prefix.add(next);
            boolean found = permutations(prefix, tail, graph);
            prefix.removeLast();
            if (found) return true;
        }
        return false;
    }

    private static List<String> assign(int slots, List<IngredientAssignment.Input<String>> inputs, long batch) {
        return IngredientAssignment.assign(IntStream.range(0, slots).boxed().toList(), inputs, batch, (i, k) -> true);
    }
}
