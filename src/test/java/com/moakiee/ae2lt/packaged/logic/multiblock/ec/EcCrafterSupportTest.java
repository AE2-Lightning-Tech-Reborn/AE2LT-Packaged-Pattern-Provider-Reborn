package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingTableGridPlanner.Assignment;

class EcCrafterSupportTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void mapsDirectDimensionsAndKeepsTaggedInputsWithoutMutation() {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.getOrCreateTag().putString("variant", "required");
        var key = AEItemKey.of(stack);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var recipe = new ShapedRecipe(new ResourceLocation("test", "shaped"), "",
                CraftingBookCategory.MISC, 2, 2,
                NonNullList.of(Ingredient.EMPTY, StrictNBTIngredient.of(stack), Ingredient.EMPTY,
                        Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT)), new ItemStack(Items.DIAMOND));
        var counter = new KeyCounter();
        counter.add(gold, 1);
        counter.add(key, 1);

        assertEquals(List.of(new Assignment<>(0, key), new Assignment<>(4, gold)),
                EcCrafterSupport.assignGrid(recipe, new KeyCounter[]{counter}));
        assertEquals(1, counter.get(key));
        assertEquals(1, counter.get(gold));
        assertEquals("required", key.toStack(1).getTag().getString("variant"));

        var wrong = new KeyCounter();
        wrong.add(AEItemKey.of(Items.IRON_INGOT), 1);
        wrong.add(gold, 1);
        assertNull(EcCrafterSupport.assignGrid(recipe, new KeyCounter[]{wrong}));
    }

    @Test
    void expandsRepeatedShapelessInputsAcrossCounters() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var first = new KeyCounter();
        first.add(iron, 1);
        var second = new KeyCounter();
        second.add(iron, 1);
        assertEquals(List.of(new Assignment<>(0, iron), new Assignment<>(1, iron)),
                EcCrafterSupport.assignGrid(shapeless(2), new KeyCounter[]{first, second}));
        assertEquals(1, first.get(iron));
        assertEquals(1, second.get(iron));
    }

    @Test
    void boundsExpansionAndRejectsFluids() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        for (long amount : new long[]{10, Long.MAX_VALUE}) {
            var counter = new KeyCounter();
            counter.add(iron, amount);
            assertNull(EcCrafterSupport.assignGrid(shapeless(9), new KeyCounter[]{counter}));
            assertEquals(amount, counter.get(iron));
        }
        var fluid = new KeyCounter();
        fluid.add(AEFluidKey.of(Fluids.WATER), 1);
        assertNull(EcCrafterSupport.assignGrid(shapeless(1), new KeyCounter[]{fluid}));
        assertNull(EcCrafterSupport.assignGrid(new Object(), new KeyCounter[]{}));
    }

    private static ShapelessRecipe shapeless(int count) {
        return new ShapelessRecipe(new ResourceLocation("test", "shapeless"), "",
                CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND),
                NonNullList.withSize(count, Ingredient.of(Items.IRON_INGOT)));
    }
}
