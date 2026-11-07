package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
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
    void matchesEachIngredientAndKeyIdentityOnceDespiteRepeatedInputRecordsAndCopies() {
        for (int distinctIngredients : new int[]{1, 81}) {
            var conditions = IntStream.range(0, distinctIngredients).mapToObj(i -> new Object()).toList();
            var ingredients = IntStream.range(0, 81).mapToObj(i -> conditions.get(i % distinctIngredients)).toList();
            var material = new Object();
            var inputs = List.of(new IngredientAssignment.Input<>(material, 40_000),
                    new IngredientAssignment.Input<>(material, 41_000));
            var calls = new AtomicInteger();
            var result = IngredientAssignment.assign(ingredients, inputs, 1000, (ingredient, key) -> {
                assertTrue(calls.incrementAndGet() <= distinctIngredients);
                return true;
            });
            assertEquals(Collections.nCopies(81, material), result);
            assertEquals(distinctIngredients, calls.get());
        }
    }

    @Test
    void equalButDistinctIngredientsAndKeysKeepIdentitySensitiveMatching() {
        var firstIngredient = new String("condition");
        var secondIngredient = new String("condition");
        var firstKey = new String("material");
        var secondKey = new String("material");
        var result = IngredientAssignment.assign(List.of(firstIngredient, secondIngredient),
                List.of(new IngredientAssignment.Input<>(secondKey, 1),
                        new IngredientAssignment.Input<>(firstKey, 1)), 1,
                (ingredient, key) -> ingredient == firstIngredient ? key == firstKey : key == secondKey);
        assertNotNull(result);
        assertSame(firstKey, result.get(0));
        assertSame(secondKey, result.get(1));
    }

    @Test
    void compatibilityCacheIsLocalToEachAssignment() {
        var ingredient = new Object();
        var inputs = List.of(new IngredientAssignment.Input<>(new Object(), 1));
        var calls = new AtomicInteger();
        assertNotNull(IngredientAssignment.assign(List.of(ingredient), inputs, 1, (i, k) -> {
            calls.incrementAndGet();
            return true;
        }));
        assertNull(IngredientAssignment.assign(List.of(ingredient), inputs, 1, (i, k) -> {
            calls.incrementAndGet();
            return false;
        }));
        assertEquals(2, calls.get());
    }

    @Test
    void denseEightyOneSlotMatchingPreservesFirstInputOrder() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var keys = IntStream.range(0, 81).boxed().toList();
            var inputs = keys.stream().map(key -> new IngredientAssignment.Input<>(key, 1)).toList();
            var calls = new AtomicInteger();
            var result = IngredientAssignment.assign(Collections.nCopies(81, new Object()), inputs, 1, (i, k) -> {
                assertTrue(calls.incrementAndGet() <= 81);
                return true;
            });
            assertEquals(keys, result);
            assertEquals(81, calls.get());
            assertThrows(UnsupportedOperationException.class, () -> result.set(0, -1));
        });
    }

    @Test
    void hallDeficientEightyOneSlotGraphIsRejectedWithoutEnumeratingCopies() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var ingredients = new ArrayList<>(Collections.nCopies(41, "A"));
            ingredients.addAll(Collections.nCopies(40, "B"));
            var inputs = List.of(new IngredientAssignment.Input<>("A", 40),
                    new IngredientAssignment.Input<>("B", 41));
            var calls = new AtomicInteger();
            assertNull(IngredientAssignment.assign(ingredients, inputs, 1, (ingredient, key) -> {
                assertTrue(calls.incrementAndGet() <= 4);
                return ingredient.equals(key);
            }));
            assertEquals(4, calls.get());
        });
    }

    @Test
    void emptyAndInvalidQuantitiesDoNotEvaluateTheMatcher() {
        var calls = new AtomicInteger();
        assertEquals(List.of(), IngredientAssignment.assign(List.of(),
                List.of(new IngredientAssignment.Input<>("ignored", 0)), 1, (i, k) -> {
                    calls.incrementAndGet();
                    return true;
                }));
        assertNull(IngredientAssignment.assign(List.of("A"),
                List.of(new IngredientAssignment.Input<>("A", 3)), 2, (i, k) -> {
                    calls.incrementAndGet();
                    return true;
                }));
        assertEquals(0, calls.get());
    }

    @Test
    void allFourSlotCompatibilityGraphsPreserveTheFormerFirstSolution() {
        var ingredients = List.of(0, 1, 2, 3);
        var inputs = ingredients.stream().map(i -> new IngredientAssignment.Input<>(i, 7)).toList();
        for (int mask = 0; mask < 65_536; mask++) {
            var graph = new boolean[4][4];
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) graph[i][j] = (mask & (1 << (4 * i + j))) != 0;
            }
            var result = IngredientAssignment.assign(ingredients, inputs, 7, (i, key) -> graph[i][key]);
            assertEquals(firstSolution(graph, ingredients), result, "graph " + mask);
        }
    }

    @Test
    void repeatedKeysAndShuffledInputsPreserveTheFormerFirstSolution() {
        var random = new Random(7112026);
        for (int sample = 0; sample < 6000; sample++) {
            int size = 1 + random.nextInt(8);
            int keyCount = 1 + random.nextInt(size);
            var counts = new int[keyCount];
            for (int i = 0; i < size; i++) counts[random.nextInt(keyCount)]++;
            var graph = new boolean[size][keyCount];
            for (var row : graph) for (int j = 0; j < keyCount; j++) row[j] = random.nextBoolean();
            long batch = sample % 2 == 0 ? 1 : 1000;
            var inputs = new ArrayList<IngredientAssignment.Input<Integer>>();
            for (int key = 0; key < keyCount; key++) {
                inputs.add(new IngredientAssignment.Input<>(key, counts[key] * batch));
            }
            Collections.shuffle(inputs, random);
            var units = new ArrayList<Integer>();
            for (var input : inputs) {
                for (long copy = 0; copy < input.amount() / batch; copy++) units.add(input.key());
            }
            var ingredients = IntStream.range(0, size).boxed().toList();
            var result = IngredientAssignment.assign(ingredients, inputs, batch, (i, key) -> graph[i][key]);
            assertEquals(firstSolution(graph, units), result, "sample " + sample);
        }
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

    // Small exhaustive reference for the stable candidate-count and input-order contract.
    private static List<Integer> firstSolution(boolean[][] graph, List<Integer> units) {
        var order = IntStream.range(0, graph.length).boxed()
                .sorted(Comparator.comparingLong(i -> units.stream().filter(key -> graph[i][key]).count())).toList();
        var chosen = new Integer[graph.length];
        return firstSolution(0, order, units, graph, new boolean[units.size()], chosen) ? List.of(chosen) : null;
    }

    private static boolean firstSolution(int depth, List<Integer> order, List<Integer> units,
                                          boolean[][] graph, boolean[] used, Integer[] chosen) {
        if (depth == order.size()) return true;
        int ingredient = order.get(depth);
        for (int unit = 0; unit < units.size(); unit++) {
            if (used[unit] || !graph[ingredient][units.get(unit)]) continue;
            used[unit] = true;
            chosen[ingredient] = units.get(unit);
            if (firstSolution(depth + 1, order, units, graph, used, chosen)) return true;
            used[unit] = false;
        }
        return false;
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
