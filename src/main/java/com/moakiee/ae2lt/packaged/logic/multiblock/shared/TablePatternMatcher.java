package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import net.minecraft.world.item.crafting.Ingredient;

/** Bind-time matching preserves each input's own mode and its declared alternatives. */
public final class TablePatternMatcher {
    private static final int MAX_VARIANTS = 4096;

    private TablePatternMatcher() {}

    public static boolean matches(List<Ingredient> ingredients, IPatternDetails pattern, long ratio) {
        if (ratio <= 0) return false;
        var nonEmpty = ingredients.stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        if (nonEmpty.isEmpty()) return false;
        List<List<DeclaredInput>> variants = List.of(List.of());
        for (int slot = 0; slot < pattern.getInputs().length; slot++) {
            var input = pattern.getInputs()[slot];
            if (input == null || input.getMultiplier() <= 0) return false;
            var next = new ArrayList<List<DeclaredInput>>();
            for (var possible : input.getPossibleInputs()) {
                if (!(possible.what() instanceof AEItemKey key) || possible.amount() <= 0) continue;
                long amount;
                try { amount = Math.multiplyExact(possible.amount(), input.getMultiplier()); }
                catch (ArithmeticException overflow) { continue; }
                if (amount % ratio != 0 || amount / ratio > nonEmpty.size()) continue;
                for (var previous : variants) {
                    if (next.size() == MAX_VARIANTS) return false;
                    var units = new ArrayList<>(previous);
                    if (amount / ratio > nonEmpty.size() - units.size()) continue;
                    for (long i = 0; i < amount / ratio; i++) {
                        units.add(new DeclaredInput(key, OverloadPatternSemantics.isIdOnlyInput(pattern, slot)));
                    }
                    next.add(List.copyOf(units));
                }
            }
            if (next.isEmpty()) return false;
            variants = next;
        }
        for (var units : variants) {
            if (units.size() != nonEmpty.size()) continue;
            var ingredientByUnit = new int[units.size()];
            Arrays.fill(ingredientByUnit, -1);
            boolean matched = true;
            for (int i = 0; i < nonEmpty.size(); i++) {
                if (!assign(i, nonEmpty, units, ingredientByUnit, new boolean[units.size()])) {
                    matched = false;
                    break;
                }
            }
            if (matched) return true;
        }
        return false;
    }

    private static boolean assign(int index, List<Ingredient> ingredients, List<DeclaredInput> units,
                                  int[] ingredientByUnit, boolean[] visited) {
        for (int unit = 0; unit < units.size(); unit++) {
            if (visited[unit] || !accepts(ingredients.get(index), units.get(unit))) continue;
            visited[unit] = true;
            if (ingredientByUnit[unit] == -1
                    || assign(ingredientByUnit[unit], ingredients, units, ingredientByUnit, visited)) {
                ingredientByUnit[unit] = index;
                return true;
            }
        }
        return false;
    }

    private static boolean accepts(Ingredient ingredient, DeclaredInput input) {
        if (!input.idOnly()) return ingredient.test(input.key().toStack(1));
        for (var candidate : ingredient.getItems()) {
            if (candidate.getItem() == input.key().getItem()) return true;
        }
        return false;
    }

    private record DeclaredInput(AEItemKey key, boolean idOnly) {}
    public record BoundRecipe(Object recipe, int craftRatio) {}
}
