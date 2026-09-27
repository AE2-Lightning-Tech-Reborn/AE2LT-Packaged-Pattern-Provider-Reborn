package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Lazy optional-mod API access, separate from recipe planning and dispatch. */
final class ExtendedCraftingCombinationAccess {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ExtendedCraftingCombinationAccess.class);
    private static final String CORE_TILE_CLASS =
            "com.blakebr0.extendedcrafting.tileentity.CraftingCoreTileEntity";
    private static final String COMBINATION_RECIPE_CLASS =
            "com.blakebr0.extendedcrafting.crafting.recipe.CombinationRecipe";

    private static volatile boolean lookupDone;
    private static volatile @Nullable Method getProgressMethod;
    private static volatile @Nullable Method getActiveRecipeMethod;
    private static volatile @Nullable Method getInputMethod;
    private static volatile @Nullable Method getPedestalIngredientsMethod;

    /** Refresh the machine's recipe cache immediately after its inputs arrive. */
    static void refreshRecipe(BlockEntity be) {
        ensureLookup();
        if (getActiveRecipeMethod == null) return;
        try {
            getActiveRecipeMethod.invoke(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            CompatibilityDiagnostics.failure(ExtendedCraftingCombinationAccess.class, "refreshRecipe", failure);
        }
    }

    /** A zero progress value alone also describes a valid recipe waiting for FE. */
    static boolean isIdleWithoutRecipe(BlockEntity be) {
        ensureLookup();
        if (getActiveRecipeMethod == null || getProgress(be) != 0) return false;
        try {
            return getActiveRecipeMethod.invoke(be) == null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            CompatibilityDiagnostics.failure(ExtendedCraftingCombinationAccess.class, "isIdleWithoutRecipe", failure);
            return false;
        }
    }

    static int getProgress(BlockEntity be) {
        ensureLookup();
        if (getProgressMethod == null) return -1;
        try {
            var value = getProgressMethod.invoke(be);
            return value instanceof Integer i ? i : -1;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ExtendedCraftingCombinationAccess.class, "getProgress", ignored);
            return -1;
        }
    }

    @Nullable
    static ItemStack getResultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> r)) return null;
        try {
            return com.moakiee.ae2lt.packaged.logic.multiblock.shared.RecipeCompat26.resultItem(r, level);
        } catch (RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ExtendedCraftingCombinationAccess.class, "getResultItem", ignored);
            return null;
        }
    }

    @Nullable
    static Ingredient getCatalyst(Object recipe) {
        ensureLookup();
        if (getInputMethod == null) return null;
        try {
            var value = getInputMethod.invoke(recipe);
            return value instanceof Ingredient i ? i : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ExtendedCraftingCombinationAccess.class, "getCatalyst", ignored);
            return null;
        }
    }

    @Nullable
    static List<Ingredient> getRecipeIngredients(Object recipe) {
        ensureLookup();
        if (getPedestalIngredientsMethod == null) return null;
        try {
            var value = getPedestalIngredientsMethod.invoke(recipe);
            if (!(value instanceof List<?> ingredients)) return null;
            var result = new ArrayList<Ingredient>(ingredients.size());
            for (var ingredient : ingredients) {
                if (!(ingredient instanceof Ingredient i)) return null;
                result.add(i);
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ExtendedCraftingCombinationAccess.class, "getRecipeIngredients", ignored);
            return null;
        }
    }

    private static void ensureLookup() {
        if (lookupDone) return;
        synchronized (ExtendedCraftingCombinationAccess.class) {
            if (lookupDone) return;

            try {
                var coreClass = Class.forName(CORE_TILE_CLASS);
                getProgressMethod = coreClass.getMethod("getProgress");
                getActiveRecipeMethod = coreClass.getMethod("getActiveRecipe");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.warn("Combination reflection: CraftingCoreTileEntity lifecycle lookup failed: {}",
                        e.toString());
            }
            try {
                var recipeClass = Class.forName(COMBINATION_RECIPE_CLASS);
                getInputMethod = recipeClass.getMethod("getCenterIngredient");
                getPedestalIngredientsMethod = recipeClass.getMethod("getPedestalIngredients");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.warn("Combination reflection: CombinationRecipe ingredient lookup failed: {}",
                        e.toString());
            }
            lookupDone = true;
        }
    }
}
