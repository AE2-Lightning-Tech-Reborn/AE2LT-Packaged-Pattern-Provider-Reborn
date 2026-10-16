package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MekRecipeReflectionTest {
    static class GenericRecipe<T> {
        public T getOutput(T left, T right) { return left; }
    }
    static class Ingredient {
        public long getNeededAmount(CharSequence stack) { return stack.length(); }
    }

    @Test
    void resolvesErasedRecipeParametersForConcreteStacks() {
        var recipe = new GenericRecipe<String>();
        assertEquals("hydrogen", MekRecipeBinding.invoke(recipe, "getOutput", "hydrogen", "chlorine"));
        assertEquals("chlorine", MekRecipeBinding.invoke(recipe, "getOutput", "chlorine", "hydrogen"));
    }

    @Test
    void resolvesAssignableIngredientParametersAndRejectsIncompatibleTypes() {
        var ingredient = new Ingredient();
        assertEquals(5L, MekRecipeBinding.invoke(ingredient, "getNeededAmount", new StringBuilder("stack")));
        assertNull(MekRecipeBinding.invoke(ingredient, "getNeededAmount", 10));
        assertNull(MekRecipeBinding.invoke(ingredient, "missingMethod", "stack"));
    }
}
