package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.util.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.IngredientAssignment;
import net.minecraft.world.item.crafting.Ingredient;

final class FAPatterns {
    private FAPatterns() {}
    static KeyCounter declared(IPatternDetails pattern) {
        var out = new KeyCounter();
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length != 1) return null;
            var stack = possible[0];
            try { out.add(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier())); }
            catch (ArithmeticException e) { return null; }
        }
        return out;
    }
    static KeyCounter actual(KeyCounter[] inputs) {
        var out = new KeyCounter();
        for (var input : inputs) for (var entry : input) {
            if (entry.getLongValue() <= 0) continue;
            try { out.set(entry.getKey(), Math.addExact(out.get(entry.getKey()), entry.getLongValue())); }
            catch (ArithmeticException e) { return null; }
        }
        return out;
    }
    static List<GenericStack> stacks(KeyCounter amounts) {
        var out = new ArrayList<GenericStack>();
        for (var entry : amounts) if (entry.getLongValue() > 0) out.add(new GenericStack(entry.getKey(),entry.getLongValue()));
        return List.copyOf(out);
    }
    static List<AEItemKey> assign(List<Ingredient> ingredients, KeyCounter amounts, long copies, boolean resourceInputs) {
        if (amounts == null) return null;
        var items = new ArrayList<IngredientAssignment.Input<AEItemKey>>();
        for (var entry : amounts) {
            if (entry.getLongValue() <= 0) continue;
            if (entry.getKey() instanceof AEItemKey item) items.add(new IngredientAssignment.Input<>(item, entry.getLongValue()));
            else if (!resourceInputs || !(entry.getKey() instanceof com.moakiee.ae2lt.packaged.resource.PackagedResourceKey key)
                    || !com.moakiee.ae2lt.packaged.resource.ForbiddenEssenceResources.isEssence(key)) return null;
        }
        return IngredientAssignment.assign(ingredients, items, copies, (ingredient,key) -> ingredient.test(key.toStack()));
    }
    static boolean output(IPatternDetails pattern, AEKey key, long amount) {
        return pattern.getOutputs().size()==1 && pattern.getPrimaryOutput().what().equals(key) && pattern.getPrimaryOutput().amount()==amount;
    }
}
