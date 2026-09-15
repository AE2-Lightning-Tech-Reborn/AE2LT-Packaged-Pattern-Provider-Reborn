package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import org.jetbrains.annotations.Nullable;

/** Lazy optional-mod API access, separate from recipe planning and dispatch. */
final class ExtendedCraftingTableAccess {
    private static final String BASIC_TABLE_CLASS =
            "com.blakebr0.extendedcrafting.tileentity.BasicTableTileEntity";
    private static final String ADVANCED_TABLE_CLASS =
            "com.blakebr0.extendedcrafting.tileentity.AdvancedTableTileEntity";
    private static final String ELITE_TABLE_CLASS =
            "com.blakebr0.extendedcrafting.tileentity.EliteTableTileEntity";
    private static final String ULTIMATE_TABLE_CLASS =
            "com.blakebr0.extendedcrafting.tileentity.UltimateTableTileEntity";
    private static final String TABLE_INPUT_CLASS =
            "com.blakebr0.extendedcrafting.api.TableCraftingInput";

    private static volatile boolean lookupDone;
    private static volatile @Nullable Set<Class<?>> tableClasses;
    private static volatile @Nullable Method tableInputOfMethod;
    private static volatile @Nullable Method tableInputTopMethod;
    private static volatile @Nullable Method tableInputLeftMethod;
    private static volatile @Nullable Method shapedRecipeWidthMethod;

    static boolean isSupportedTable(Object o) {
        ensureLookup();
        if (tableClasses == null) {
            return false;
        }
        for (var clazz : tableClasses) {
            if (clazz.isInstance(o)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static CraftingInput createTableInput(int size, List<ItemStack> stacks, int tier) {
        ensureLookup();
        if (tableInputOfMethod == null) {
            return null;
        }
        try {
            var value = tableInputOfMethod.invoke(null, size, size, stacks, tier);
            return value instanceof CraftingInput input ? input : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ExtendedCraftingTableAccess.class, "createTableInput", ignored);
            return null;
        }
    }

    static int top(CraftingInput input) {
        ensureLookup();
        if (tableInputTopMethod == null) {
            return 0;
        }
        try {
            var value = tableInputTopMethod.invoke(input);
            return value instanceof Integer i ? i : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ExtendedCraftingTableAccess.class, "top", ignored);
            return 0;
        }
    }

    static int left(CraftingInput input) {
        ensureLookup();
        if (tableInputLeftMethod == null) {
            return 0;
        }
        try {
            var value = tableInputLeftMethod.invoke(input);
            return value instanceof Integer i ? i : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ExtendedCraftingTableAccess.class, "left", ignored);
            return 0;
        }
    }

    @Nullable
    static Integer recipeWidth(Object recipe) {
        ensureLookup();
        if (shapedRecipeWidthMethod == null) {
            return null;
        }
        try {
            if (!shapedRecipeWidthMethod.getDeclaringClass().isInstance(recipe)) {
                return null;
            }
            var value = shapedRecipeWidthMethod.invoke(recipe);
            return value instanceof Integer i ? i : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ExtendedCraftingTableAccess.class, "recipeWidth", ignored);
            return null;
        }
    }

    private static void ensureLookup() {
        if (lookupDone) {
            return;
        }
        synchronized (ExtendedCraftingTableAccess.class) {
            if (lookupDone) {
                return;
            }
            try {
                doLookup();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                if (!(ignored instanceof ClassNotFoundException) || net.neoforged.fml.ModList.get().isLoaded("extendedcrafting")) {
                    CompatibilityDiagnostics.failure(ExtendedCraftingTableAccess.class, "ensureLookup", ignored);
                }
            } finally {
                lookupDone = true;
            }
        }
    }

    private static void doLookup() throws ReflectiveOperationException {
        tableClasses = Set.of(
                Class.forName(BASIC_TABLE_CLASS),
                Class.forName(ADVANCED_TABLE_CLASS),
                Class.forName(ELITE_TABLE_CLASS),
                Class.forName(ULTIMATE_TABLE_CLASS));

        var tableInputClass = Class.forName(TABLE_INPUT_CLASS);
        tableInputOfMethod = tableInputClass.getMethod("of", int.class, int.class, List.class, int.class);
        tableInputTopMethod = tableInputClass.getMethod("top");
        tableInputLeftMethod = tableInputClass.getMethod("left");

        var shapedRecipeClass = Class.forName("com.blakebr0.extendedcrafting.crafting.recipe.ShapedTableRecipe");
        shapedRecipeWidthMethod = shapedRecipeClass.getMethod("getWidth");
    }
}
