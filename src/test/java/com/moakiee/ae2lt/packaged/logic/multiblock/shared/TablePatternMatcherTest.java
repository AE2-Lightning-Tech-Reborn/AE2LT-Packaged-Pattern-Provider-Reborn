package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import static org.junit.jupiter.api.Assertions.*;
import static com.moakiee.ae2lt.packaged.testsupport.TestPatterns.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

class TablePatternMatcherTest {
    @BeforeAll static void bootstrap() { MinecraftTestBootstrap.initialize(); }
    private static GenericStack stone(String variant, long amount) {
        var stack = new ItemStack(Items.STONE);
        stack.getOrCreateTag().putString("variant", variant);
        return new GenericStack(AEItemKey.of(stack), amount);
    }
    private static GenericStack[] outputs() { return new GenericStack[]{stone("result", 1)}; }

    @Test void fuzzyOutputDoesNotRelaxStrictInputNbt() {
        var recipe = List.<Ingredient>of(StrictNBTIngredient.of(((AEItemKey) stone("a", 1).what()).toStack(1)));
        var pattern = pattern(outputs(), new IPatternDetails.IInput[]{input(1, stone("b", 1))}, Set.of(), true);
        assertFalse(TablePatternMatcher.matches(recipe, pattern, 1));
    }
    @Test void fuzzyInputWorksWithStrictOutputAndMixedSlots() {
        var recipe = List.<Ingredient>of(StrictNBTIngredient.of(((AEItemKey) stone("a", 1).what()).toStack(1)),
                StrictNBTIngredient.of(((AEItemKey) stone("c", 1).what()).toStack(1)));
        var inputs = new IPatternDetails.IInput[]{input(1, stone("b", 1)), input(1, stone("c", 1))};
        assertTrue(TablePatternMatcher.matches(recipe, pattern(outputs(), inputs, Set.of(0), false), 1));
        assertFalse(TablePatternMatcher.matches(recipe, pattern(outputs(), inputs, Set.of(1), false), 1));
    }
    @Test void considersNonFirstAlternativeWithoutConsumingBothChoices() {
        var choices = input(1, new GenericStack(AEItemKey.of(Items.DIRT), 1), stone("a", 1));
        assertTrue(TablePatternMatcher.matches(List.of(Ingredient.of(Items.STONE)), pattern(outputs(), choices), 1));
        assertFalse(TablePatternMatcher.matches(List.of(Ingredient.of(Items.STONE), Ingredient.of(Items.DIRT)),
                pattern(outputs(), choices), 1));
    }
    @Test void repeatedIngredientsAndRatiosNeedExactQuantities() {
        var recipe = List.of(Ingredient.of(Items.STONE), Ingredient.of(Items.STONE));
        assertTrue(TablePatternMatcher.matches(recipe, pattern(outputs(), input(3, stone("a", 2))), 3));
        assertFalse(TablePatternMatcher.matches(recipe, pattern(outputs(), input(3, stone("a", 1))), 3));
        assertFalse(TablePatternMatcher.matches(recipe, pattern(outputs(), input(Long.MAX_VALUE, stone("a", 2))), 1));
    }
    @Test void ambiguousIngredientsLeaveTheOnlyStrictCandidateAvailable() {
        var recipe = List.<Ingredient>of(Ingredient.of(Items.STONE, Items.DIRT), Ingredient.of(Items.STONE));
        assertTrue(TablePatternMatcher.matches(recipe, pattern(outputs(), input(1, stone("a", 1)),
                input(1, new GenericStack(AEItemKey.of(Items.DIRT), 1))), 1));
    }
}
