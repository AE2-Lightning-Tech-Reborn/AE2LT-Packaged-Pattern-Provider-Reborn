package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import com.moakiee.ae2lt.packaged.logic.multiblock.shared.IngredientAssignment;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import org.jetbrains.annotations.Nullable;

/** Adapter-local input vocabulary backed by the shared exact matcher. */
final class ExtendedCraftingTableIngredientAssigner {
    private ExtendedCraftingTableIngredientAssigner() {}

    @Nullable
    static <I, K> List<K> assign(List<I> ingredients, List<Input<K>> inputs,
                                long amountPerIngredient, BiPredicate<I, K> matcher) {
        return IngredientAssignment.assign(ingredients, inputs.stream()
                .map(input -> new IngredientAssignment.Input<>(input.key(), input.amount())).toList(),
                amountPerIngredient, matcher);
    }

    record Input<K>(K key, long amount) {
        Input {
            Objects.requireNonNull(key, "key");
            if (amount < 0) throw new IllegalArgumentException("amount must be non-negative");
        }
    }
}
