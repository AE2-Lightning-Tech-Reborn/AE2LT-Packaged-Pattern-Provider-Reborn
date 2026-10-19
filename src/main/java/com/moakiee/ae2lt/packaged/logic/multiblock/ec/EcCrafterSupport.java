package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingTableGridPlanner.Assignment;

/** Inventory access and single-item grid planning shared by Ender and Flux Crafters. */
final class EcCrafterSupport {
    private static final ReflectionBootstrap LOOKUP = new ReflectionBootstrap("ec-crafter");
    private static volatile @Nullable Method getInventoryMethod;

    private EcCrafterSupport() {
    }

    @Nullable
    static IItemHandler getInventory(BlockEntity be) {
        LOOKUP.run(() -> getInventoryMethod = Class.forName(
                "com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity").getMethod("getInventory"));
        if (getInventoryMethod == null) return null;
        try {
            var value = getInventoryMethod.invoke(be);
            return value instanceof IItemHandler handler ? handler : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return null;
        }
    }

    @Nullable
    static ItemStack getResultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> typed)) return null;
        try {
            return typed.getResultItem();
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    @Nullable
    static List<Assignment<AEItemKey>> assignGrid(Object recipe, KeyCounter[] inputs) {
        if (!(recipe instanceof Recipe<?> typed)) return null;
        List<Ingredient> ingredients;
        try {
            ingredients = new ArrayList<>(typed.getIngredients());
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
        if (ingredients.isEmpty() || ingredients.size() > 9) return null;
        var units = new ArrayList<AEItemKey>();
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey key)) return null;
                long amount = entry.getLongValue();
                if (amount > 9 - units.size()) return null;
                for (int i = 0; i < amount; i++) units.add(key);
            }
        }
        return ExtendedCraftingTableGridPlanner.placeCrafter(
                dimension(recipe, "getWidth"), dimension(recipe, "getHeight"),
                ingredients, units, Ingredient::isEmpty,
                (ingredient, key) -> ingredient.test(key.toStack(1)));
    }

    // EC 1.20.1 shaped recipes expose these methods directly; shapeless recipes do not.
    private static int dimension(Object recipe, String methodName) {
        var method = ReflectionSupport.findMethodCached(recipe.getClass(), methodName).orElse(null);
        if (method == null) return -1;
        try {
            var value = method.invoke(recipe);
            return value instanceof Integer size ? size : -1;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return -1;
        }
    }
}
