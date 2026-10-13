package com.moakiee.ae2lt.packaged.logic.multiblock.chisel;

import java.lang.reflect.Method;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelReader;
import net.neoforged.fml.ModList;

/** Supports the 1.1 recipe registry and the 1.2 public API with block/slab worth. */
final class RechiseledAccess {
    private static boolean searched;
    private static Method managerGet, recipeForItem, recipeWorth, convert, leftover, result;
    private static Method legacyRecipe, legacyContains;

    private static void lookup() throws ReflectiveOperationException {
        if (searched) return;
        searched = true;
        Class<?> manager;
        try {
            manager = Class.forName("com.supermartijn642.rechiseled.api.chiseling.ChiselingRecipeManager");
        } catch (ClassNotFoundException legacy) {
            legacyRecipe = Class.forName("com.supermartijn642.rechiseled.chiseling.ChiselingRecipes")
                    .getMethod("getRecipe", ItemStack.class);
            legacyContains = Class.forName("com.supermartijn642.rechiseled.chiseling.ChiselingRecipe")
                    .getMethod("contains", ItemStack.class);
            return;
        }
        managerGet = manager.getMethod("get", LevelReader.class);
        recipeForItem = manager.getMethod("getRecipeForItem", ItemLike.class);
        recipeWorth = Class.forName("com.supermartijn642.rechiseled.api.chiseling.ChiselingRecipe")
                .getMethod("getWorth", ItemLike.class);
        var worth = Class.forName("com.supermartijn642.rechiseled.api.chiseling.ItemWithWorth");
        convert = Class.forName("com.supermartijn642.rechiseled.api.chiseling.conversion.ChiselingConversionHelper")
                .getMethod("convert", int.class, worth, worth, int.class);
        var converted = Class.forName("com.supermartijn642.rechiseled.api.chiseling.conversion.ConversionResult");
        leftover = converted.getMethod("leftover");
        result = converted.getMethod("result");
    }

    static boolean permits(ServerLevel level, ChiselConversion conversion) {
        if (!ModList.get().isLoaded("rechiseled") || !conversion.hasDefaultOutput()) return false;
        try {
            lookup();
            if (legacyRecipe != null && legacyContains != null) {
                var recipe = legacyRecipe.invoke(null, conversion.input().toStack());
                return recipe != null && conversion.inputAmount() == conversion.outputAmount()
                        && Boolean.TRUE.equals(legacyContains.invoke(recipe, conversion.output().toStack()));
            }
            if (managerGet == null || recipeForItem == null || recipeWorth == null || convert == null
                    || leftover == null || result == null || conversion.inputAmount() > Integer.MAX_VALUE
                    || conversion.outputAmount() > Integer.MAX_VALUE) return false;
            var manager = managerGet.invoke(null, level);
            var recipe = recipeForItem.invoke(manager, conversion.input().getItem());
            if (recipe == null) return false;
            var from = recipeWorth.invoke(recipe, conversion.input().getItem());
            var to = recipeWorth.invoke(recipe, conversion.output().getItem());
            if (from == null || to == null) return false;
            // Use upstream rounding and refuse partial conversions: every accepted input is consumed.
            var converted = convert.invoke(null, (int) conversion.inputAmount(), from, to, (int) conversion.outputAmount());
            return ((Number) leftover.invoke(converted)).intValue() == 0
                    && ((Number) result.invoke(converted)).longValue() == conversion.outputAmount();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            CompatibilityDiagnostics.failure(RechiseledAccess.class, "conversion", failure);
            return false;
        }
    }
}
