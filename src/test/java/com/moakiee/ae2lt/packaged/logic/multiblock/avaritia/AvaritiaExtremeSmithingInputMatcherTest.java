package com.moakiee.ae2lt.packaged.logic.multiblock.avaritia;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvaritiaExtremeSmithingInputMatcherTest {
    @Test
    void all120InputOrdersPreserveRolesAndUseEveryMaterialOnce() {
        permutations(new ArrayList<>(), new ArrayList<>(List.of("template", "base", "a", "b", "c")), order -> {
            var match = AvaritiaExtremeSmithingInputMatcher.match(order,
                    "template"::equals, "base"::equals, value -> List.of("a", "b", "c").contains(value));
            assertNotNull(match, order.toString());
            assertEquals("template", match.template());
            assertEquals("base", match.base());
            assertEquals(List.of("a", "b", "c"), match.additions().stream().sorted().toList());
        });
    }

    @Test
    void overlappingRolesBacktrackRatherThanStealingTheOnlyBase() {
        var match = AvaritiaExtremeSmithingInputMatcher.match(List.of("base", "template", "a", "a", "a"),
                value -> !value.equals("a"), "base"::equals, "a"::equals);
        assertNotNull(match);
        assertEquals("template", match.template());
        assertEquals("base", match.base());
        assertEquals(List.of("a", "a", "a"), match.additions());
    }

    @Test
    void repeatedEqualKeysStillRepresentFiveDistinctUnits() {
        var match = AvaritiaExtremeSmithingInputMatcher.match(Collections.nCopies(5, "iron"),
                value -> true, value -> true, value -> true);
        assertNotNull(match);
        assertEquals(Collections.nCopies(3, "iron"), match.additions());
        assertThrows(UnsupportedOperationException.class, () -> match.additions().clear());
    }

    @Test
    void rejectsMissingExtraAndUnmatchableMaterials() {
        for (int size : new int[]{0, 1, 2, 3, 4, 6, 100}) {
            assertNull(AvaritiaExtremeSmithingInputMatcher.match(Collections.nCopies(size, 1),
                    value -> true, value -> true, value -> true));
        }
        assertNull(AvaritiaExtremeSmithingInputMatcher.match(List.of(0, 1, 2, 3, 4),
                value -> value == 0, value -> value == 0, value -> true));
    }

    @Test
    void all32768EligibilityGraphsAgreeWithIndependentPermutationOracle() {
        var orders = new ArrayList<List<Integer>>();
        permutations(new ArrayList<>(), new ArrayList<>(List.of(0, 1, 2, 3, 4)), orders::add);
        for (int mask = 0; mask < 1 << 15; mask++) {
            final int graph = mask;
            boolean possible = orders.stream().anyMatch(order -> accepts(graph, 0, order.get(0))
                    && accepts(graph, 1, order.get(1)) && accepts(graph, 2, order.get(2))
                    && accepts(graph, 2, order.get(3)) && accepts(graph, 2, order.get(4)));
            var match = AvaritiaExtremeSmithingInputMatcher.match(List.of(0, 1, 2, 3, 4),
                    value -> accepts(graph, 0, value), value -> accepts(graph, 1, value),
                    value -> accepts(graph, 2, value));
            assertEquals(possible, match != null, "eligibility graph " + graph);
            if (match != null) {
                var used = new ArrayList<>(match.additions());
                used.add(match.template());
                used.add(match.base());
                assertEquals(List.of(0, 1, 2, 3, 4), used.stream().sorted().toList());
                assertTrue(accepts(graph, 0, match.template()) && accepts(graph, 1, match.base()));
                assertTrue(match.additions().stream().allMatch(value -> accepts(graph, 2, value)));
            }
        }
    }

    private static boolean accepts(int graph, int role, int unit) {
        return (graph & (1 << (role * 5 + unit))) != 0;
    }

    private static <T> void permutations(List<T> prefix, List<T> remaining,
                                         java.util.function.Consumer<List<T>> check) {
        if (remaining.isEmpty()) {
            check.accept(List.copyOf(prefix));
            return;
        }
        for (int i = 0; i < remaining.size(); i++) {
            var next = remaining.remove(i);
            prefix.add(next);
            permutations(prefix, remaining, check);
            prefix.removeLast();
            remaining.add(i, next);
        }
    }
}
