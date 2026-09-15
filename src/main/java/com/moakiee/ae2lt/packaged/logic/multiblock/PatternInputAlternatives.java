package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

/** Bounded expansion of a pattern's declared item-input alternatives. */
public final class PatternInputAlternatives {

    private static final int MAX_VARIANTS = 4096;

    private PatternInputAlternatives() {
    }

    /**
     * Returns every item-only input aggregate representable by the pattern,
     * up to a fixed limit. Invalid or overflowing alternatives are skipped;
     * exceeding the limit fails closed.
     */
    @Nullable
    public static List<LinkedHashMap<AEItemKey, Long>> expandItemAlternatives(IPatternDetails pattern) {
        var inputs = pattern.getInputs();
        if (inputs == null) {
            return null;
        }
        var possibleByIndex = new GenericStack[inputs.length][];
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] == null) {
                return null;
            }
            var possible = inputs[i].getPossibleInputs();
            if (possible == null || possible.length == 0) {
                return null;
            }
            possibleByIndex[i] = possible;
        }

        // Inputs are recursed last-to-first and each leaf aggregate is built
        // first-to-last so variant order and key order match the previous
        // breadth-first expansion exactly.
        var variants = new ArrayList<LinkedHashMap<AEItemKey, Long>>();
        var chosenKeys = new AEItemKey[inputs.length];
        var chosenAmounts = new long[inputs.length];
        if (!expand(inputs, possibleByIndex, inputs.length - 1, chosenKeys, chosenAmounts, variants)) {
            return null;
        }
        return variants.isEmpty() ? null : List.copyOf(variants);
    }

    private static boolean expand(IPatternDetails.IInput[] inputs,
                                  GenericStack[][] possibleByIndex, int depth,
                                  AEItemKey[] chosenKeys, long[] chosenAmounts,
                                  List<LinkedHashMap<AEItemKey, Long>> variants) {
        if (depth < 0) {
            if (variants.size() == MAX_VARIANTS) {
                return false;
            }
            var aggregate = aggregate(chosenKeys, chosenAmounts);
            if (aggregate != null) {
                variants.add(aggregate);
            }
            return true;
        }
        var multiplier = Math.max(1L, inputs[depth].getMultiplier());
        boolean anyVariant = false;
        for (var stack : possibleByIndex[depth]) {
            if (stack == null || !(stack.what() instanceof AEItemKey key)) {
                continue;
            }
            long amount;
            try {
                amount = Math.multiplyExact(Math.max(1L, stack.amount()), multiplier);
            } catch (ArithmeticException ignored) {
                continue;
            }
            chosenKeys[depth] = key;
            chosenAmounts[depth] = amount;
            if (!expand(inputs, possibleByIndex, depth - 1, chosenKeys, chosenAmounts, variants)) {
                return false;
            }
            anyVariant = true;
        }
        return anyVariant;
    }

    @Nullable
    private static LinkedHashMap<AEItemKey, Long> aggregate(AEItemKey[] chosenKeys,
                                                            long[] chosenAmounts) {
        var merged = new LinkedHashMap<AEItemKey, Long>();
        for (int i = 0; i < chosenKeys.length; i++) {
            try {
                merged.merge(chosenKeys[i], chosenAmounts[i], Math::addExact);
            } catch (ArithmeticException ignored) {
                // The summed amount for this key overflows; the whole
                // variant is dropped, as before.
                return null;
            }
        }
        return merged;
    }

    /**
     * Expands each declared item alternative into individual item units for
     * adapters whose recipe matchers assign one physical item to each slot.
     */
    @Nullable
    public static List<List<AEItemKey>> expandItemUnitAlternatives(IPatternDetails pattern,
                                                                   int maxUnits) {
        if (maxUnits <= 0) {
            return null;
        }
        var aggregates = expandItemAlternatives(pattern);
        if (aggregates == null) {
            return null;
        }
        var variants = new ArrayList<List<AEItemKey>>(aggregates.size());
        for (var aggregate : aggregates) {
            var units = new ArrayList<AEItemKey>();
            for (var entry : aggregate.entrySet()) {
                if (entry.getValue() <= 0 || entry.getValue() > maxUnits - units.size()) {
                    return null;
                }
                for (long i = 0; i < entry.getValue(); i++) {
                    units.add(entry.getKey());
                }
            }
            if (units.isEmpty()) {
                return null;
            }
            variants.add(List.copyOf(units));
        }
        return List.copyOf(variants);
    }
}
