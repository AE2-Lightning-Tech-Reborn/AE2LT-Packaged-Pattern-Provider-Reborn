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
    private static volatile @Nullable Method getInputMethod;

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
            return r.getResultItem(level.registryAccess());
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
        if (!(recipe instanceof Recipe<?> r)) return null;
        try {
            return new ArrayList<>(r.getIngredients());
        } catch (RuntimeException | LinkageError ignored) {
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
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.warn("Combination reflection: CraftingCoreTileEntity#getProgress lookup failed: {}",
                        e.toString());
            }
            try {
                var recipeClass = Class.forName(COMBINATION_RECIPE_CLASS);
                getInputMethod = recipeClass.getMethod("getInput");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.warn("Combination reflection: CombinationRecipe#getInput lookup failed: {}",
                        e.toString());
            }
            lookupDone = true;
        }
    }
}
