package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEItemKey;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TableCraftingSupportTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        try (var loading = mockStatic(LoadingModList.class)) {
            var list = mock(LoadingModList.class);
            loading.when(LoadingModList::get).thenReturn(list);
            when(list.getModFiles()).thenReturn(List.of());
            Bootstrap.bootStrap();
        }
    }

    @Test
    void rectangularGridKeepsHolesAndCopiesExactComponents() {
        var named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("component"));
        var ingredients = List.of(Ingredient.of(Items.IRON_INGOT), Ingredient.EMPTY, Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT));
        var grid = TableCraftingSupport.layoutGrid(ingredients, List.of(named, new ItemStack(Items.GOLD_INGOT)), 5, 2);
        assertNotNull(grid);
        assertEquals(AEItemKey.of(named), AEItemKey.of(grid.get(0)));
        assertTrue(grid.get(1).isEmpty());
        assertTrue(grid.get(5).isEmpty());
        assertTrue(grid.get(6).is(Items.GOLD_INGOT));
        grid.get(0).setCount(0);
        assertEquals(1, named.getCount());
    }

    @Test
    void impossibleShapeAndMissingOrExtraAssignedUnitsCannotProduceGrid() {
        var ingredients = List.of(Ingredient.of(Items.IRON_INGOT), Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT));
        assertNull(TableCraftingSupport.layoutGrid(ingredients, List.of(new ItemStack(Items.IRON_INGOT)), 3, 3));
        assertNull(TableCraftingSupport.layoutGrid(ingredients, List.of(new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT)), 3, 3));
        assertNull(TableCraftingSupport.layoutGrid(ingredients, List.of(new ItemStack(Items.IRON_INGOT)), 3, 4));
        assertNull(TableCraftingSupport.layoutGrid(ingredients, List.of(new ItemStack(Items.IRON_INGOT)), 0, 1));
    }

    @Test
    void assignmentPreservesDistinctComponentKeysAndLeavesAvailableMapUntouched() {
        var named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("precise"));
        var precise = net.neoforged.neoforge.common.crafting.DataComponentIngredient.of(false, named);
        var ingredients = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT), precise);
        var available = new LinkedHashMap<AEItemKey, Long>();
        available.put(AEItemKey.of(named), 65L);
        available.put(AEItemKey.of(Items.IRON_INGOT), 65L);
        var original = new LinkedHashMap<>(available);
        var assigned = TableCraftingSupport.assignIngredientsStrict(ingredients, available, 65);
        assertNotNull(assigned);
        assertEquals(AEItemKey.of(Items.IRON_INGOT), AEItemKey.of(assigned.get(0)));
        assertEquals(AEItemKey.of(named), AEItemKey.of(assigned.get(1)));
        assertEquals(original, available);
        assertNull(TableCraftingSupport.assignIngredientsStrict(ingredients, available, 64));
        assertEquals(original, available);
    }

    @Test
    void virtualOutputsKeepRemainderComponentsAndScaleBeyondStackSize() {
        var tool = new ItemStack(Items.IRON_PICKAXE);
        tool.setDamageValue(8);
        tool.set(DataComponents.CUSTOM_NAME, Component.literal("kept tool"));
        var remaining = NonNullList.of(ItemStack.EMPTY, tool, ItemStack.EMPTY, new ItemStack(Items.BUCKET, 2));
        var outputs = TableCraftingSupport.virtualOutputs(new ItemStack(Items.DIAMOND, 2), remaining, Integer.MAX_VALUE);
        assertEquals(4_294_967_294L, outputs.get(0).amount());
        assertEquals(AEItemKey.of(tool), outputs.get(1).what());
        assertEquals(Integer.MAX_VALUE, outputs.get(1).amount());
        assertEquals(4_294_967_294L, outputs.get(2).amount());
        assertEquals(8, tool.getDamageValue());
    }

    @Test
    void physicalRemainderCollisionAndOverflowCannotDestroyExistingInput() {
        var inventory = new ItemStackHandler(9);
        var input = net.minecraft.world.item.crafting.CraftingInput.of(1, 1, List.of(new ItemStack(Items.STONE, 2)));
        inventory.setStackInSlot(8, new ItemStack(Items.STONE, 2));
        var bucket = NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.BUCKET));
        assertFalse(TableCraftingSupport.canApplyRemaining(inventory, 3, input, bucket, 2, 2));
        assertEquals(2, inventory.getStackInSlot(8).getCount());
        var oversized = NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.STONE, 64));
        assertFalse(TableCraftingSupport.canApplyRemaining(inventory, 3, input, oversized, 2, 2));
        assertFalse(TableCraftingSupport.canApplyRemaining(inventory, 3, input, NonNullList.create(), 2, 2));
    }

    @Test
    void physicalRemainderUsesTrimOffsetsAndIndependentStacks() {
        var inventory = new ItemStackHandler(25);
        inventory.setStackInSlot(18, new ItemStack(Items.MILK_BUCKET));
        var input = net.minecraft.world.item.crafting.CraftingInput.of(1, 1, List.of(new ItemStack(Items.MILK_BUCKET)));
        var bucket = new ItemStack(Items.BUCKET);
        var remaining = NonNullList.of(ItemStack.EMPTY, bucket);
        assertTrue(TableCraftingSupport.canApplyRemaining(inventory, 5, input, remaining, 3, 3));
        TableCraftingSupport.applyCraftRemainders(inventory, 5, input, remaining, 3, 3);
        assertTrue(inventory.getStackInSlot(18).is(Items.BUCKET));
        inventory.getStackInSlot(18).setCount(0);
        assertEquals(1, bucket.getCount());
    }
}
