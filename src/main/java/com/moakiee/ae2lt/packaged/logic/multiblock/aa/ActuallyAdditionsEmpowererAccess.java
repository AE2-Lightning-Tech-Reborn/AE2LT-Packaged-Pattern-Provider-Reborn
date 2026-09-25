package com.moakiee.ae2lt.packaged.logic.multiblock.aa;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

import com.moakiee.ae2lt.packaged.logic.multiblock.aa.ActuallyAdditionsEmpowererAdapter.PlannedUnit;

/** Lazy optional-mod API access, using the native Forge 1.20.1 contract. */
final class ActuallyAdditionsEmpowererAccess {
    private static final String EMPOWERER_CLASS =
            "de.ellpeck.actuallyadditions.mod.tile.TileEntityEmpowerer";
    private static final String DISPLAY_STAND_CLASS =
            "de.ellpeck.actuallyadditions.mod.tile.TileEntityDisplayStand";
    private static final String INVENTORY_BASE_CLASS =
            "de.ellpeck.actuallyadditions.mod.tile.TileEntityInventoryBase";
    private static final String EMPOWERER_RECIPE_CLASS =
            "de.ellpeck.actuallyadditions.mod.crafting.EmpowererRecipe";

    private static final ReflectionBootstrap LOOKUP = new ReflectionBootstrap("aa-empowerer");
    private static volatile @Nullable Class<?> empowererClass;
    private static volatile @Nullable Class<?> displayStandClass;
    private static volatile @Nullable Class<?> empowererRecipeClass;
    private static volatile @Nullable Field invField;
    private static volatile @Nullable Field processTimeField;
    private static volatile @Nullable Field storageField;
    private static volatile @Nullable Method isPossibleInputMethod;
    private static volatile @Nullable Method recipeMatchesMethod;
    private static volatile @Nullable Method getInputMethod;
    private static volatile @Nullable Method getStandOneMethod;
    private static volatile @Nullable Method getStandTwoMethod;
    private static volatile @Nullable Method getStandThreeMethod;
    private static volatile @Nullable Method getStandFourMethod;
    private static volatile @Nullable Method getTimeMethod;
    private static volatile @Nullable Method getEnergyPerStandMethod;

    static boolean isEmpowerer(Object o) {
        ensureLookup();
        return empowererClass != null && empowererClass.isInstance(o);
    }

    static boolean isDisplayStand(Object o) {
        ensureLookup();
        return displayStandClass != null && displayStandClass.isInstance(o);
    }

    @Nullable
    static IItemHandler getHandler(BlockEntity be) {
        ensureLookup();
        if (invField == null) {
            return null;
        }
        try {
            var value = invField.get(be);
            return value instanceof IItemHandler h ? h : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static int getProcessTime(BlockEntity be) {
        ensureLookup();
        if (processTimeField == null || !isEmpowerer(be)) {
            return 0;
        }
        try {
            return processTimeField.getInt(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    static boolean isPossibleInput(ItemStack stack) {
        ensureLookup();
        if (isPossibleInputMethod == null) {
            return false;
        }
        try {
            var value = isPossibleInputMethod.invoke(null, stack);
            return value instanceof Boolean b && b;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    static int getEnergyStored(BlockEntity be) {
        ensureLookup();
        if (storageField == null || !isDisplayStand(be)) {
            return 0;
        }
        try {
            var value = storageField.get(be);
            return value instanceof IEnergyStorage storage ? storage.getEnergyStored() : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    @Nullable
    static Ingredient getInput(Object recipe) {
        ensureLookup();
        if (getInputMethod == null || !isEmpowererRecipe(recipe)) {
            return null;
        }
        try {
            var value = getInputMethod.invoke(recipe);
            return value instanceof Ingredient ingredient ? ingredient : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    static List<Ingredient> getModifiers(Object recipe) {
        ensureLookup();
        if (!isEmpowererRecipe(recipe)
                || getStandOneMethod == null
                || getStandTwoMethod == null
                || getStandThreeMethod == null
                || getStandFourMethod == null) {
            return null;
        }
        try {
            var one = getStandOneMethod.invoke(recipe);
            var two = getStandTwoMethod.invoke(recipe);
            var three = getStandThreeMethod.invoke(recipe);
            var four = getStandFourMethod.invoke(recipe);
            if (one instanceof Ingredient i1
                    && two instanceof Ingredient i2
                    && three instanceof Ingredient i3
                    && four instanceof Ingredient i4) {
                return List.of(i1, i2, i3, i4);
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static boolean recipeMatches(Object recipe, PlannedUnit base, List<PlannedUnit> modifiers) {
        ensureLookup();
        if (recipeMatchesMethod == null || !isEmpowererRecipe(recipe) || modifiers.size() != 4) {
            return false;
        }
        try {
            var value = recipeMatchesMethod.invoke(recipe,
                    base.stack(),
                    modifiers.get(0).stack(),
                    modifiers.get(1).stack(),
                    modifiers.get(2).stack(),
                    modifiers.get(3).stack());
            return value instanceof Boolean b && b;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    static int getTime(Object recipe) {
        ensureLookup();
        if (getTimeMethod == null || !isEmpowererRecipe(recipe)) {
            return 0;
        }
        try {
            return (Integer) getTimeMethod.invoke(recipe);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    static int getEnergyPerStand(Object recipe) {
        ensureLookup();
        if (getEnergyPerStandMethod == null || !isEmpowererRecipe(recipe)) {
            return 0;
        }
        try {
            return (Integer) getEnergyPerStandMethod.invoke(recipe);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    private static boolean isEmpowererRecipe(Object recipe) {
        return empowererRecipeClass != null && empowererRecipeClass.isInstance(recipe);
    }

    private static void ensureLookup() {
        LOOKUP.run(() -> {
            try {
                doLookup();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        });
    }

    private static void doLookup() throws ReflectiveOperationException {
        empowererClass = Class.forName(EMPOWERER_CLASS);
        displayStandClass = Class.forName(DISPLAY_STAND_CLASS);
        empowererRecipeClass = Class.forName(EMPOWERER_RECIPE_CLASS);
        var inventoryBaseClass = Class.forName(INVENTORY_BASE_CLASS);

        invField = inventoryBaseClass.getField("inv");
        processTimeField = empowererClass.getField("processTime");
        storageField = displayStandClass.getField("storage");

        isPossibleInputMethod = empowererClass.getMethod("isPossibleInput", ItemStack.class);
        recipeMatchesMethod = empowererRecipeClass.getMethod("matches",
                ItemStack.class, ItemStack.class, ItemStack.class, ItemStack.class, ItemStack.class);
        getInputMethod = empowererRecipeClass.getMethod("getInput");
        getStandOneMethod = empowererRecipeClass.getMethod("getStandOne");
        getStandTwoMethod = empowererRecipeClass.getMethod("getStandTwo");
        getStandThreeMethod = empowererRecipeClass.getMethod("getStandThree");
        getStandFourMethod = empowererRecipeClass.getMethod("getStandFour");
        getTimeMethod = empowererRecipeClass.getMethod("getTime");
        getEnergyPerStandMethod = empowererRecipeClass.getMethod("getEnergyPerStand");
    }
}
