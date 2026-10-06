package com.moakiee.ae2lt.packaged.logic.multiblock.compat;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class HephaestusIngredientMatchTest {
    @BeforeAll static void bootstrap() { MinecraftTestBootstrap.initialize(); }

    @Test void overlappingIngredientsBacktrackWithoutConsumingInput() {
        var inputs = List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT));
        var matched = ForbiddenArcanusHephaestusForgeAdapter.match(List.of(
                Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), Ingredient.of(Items.IRON_INGOT)), inputs);
        assertNotNull(matched);
        assertEquals(Items.GOLD_INGOT, matched.get(0).getItem());
        assertEquals(Items.IRON_INGOT, matched.get(1).getItem());
        assertEquals(1, inputs.get(0).getCount());
        assertEquals(1, inputs.get(1).getCount());
    }

    @Test void rejectsMissingAndExtraIngredients() {
        var ingredients = List.of(Ingredient.of(Items.IRON_INGOT));
        assertNull(ForbiddenArcanusHephaestusForgeAdapter.match(ingredients, List.of()));
        assertNull(ForbiddenArcanusHephaestusForgeAdapter.match(ingredients,
                List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT))));
        assertNull(ForbiddenArcanusHephaestusForgeAdapter.match(ingredients, List.of(new ItemStack(Items.GOLD_INGOT))));
    }
}
