package com.moakiee.ae2lt.packaged.logic.multiblock.avaritia;

import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Lazy optional-mod API access, separate from recipe planning and dispatch. */
final class AvaritiaTableAccess {
    private static final String TABLE_CLASS =
            "committee.nova.mods.avaritia.common.tile.TierCraftTile";
    private static final String BASE_INVENTORY_CLASS =
            "committee.nova.mods.avaritia.api.common.tile.BaseInventoryTileEntity";
    private static final String TIER_INPUT_CLASS =
            "committee.nova.mods.avaritia.api.common.crafting.TierInput";
    private static final String SHAPED_RECIPE_CLASS =
            "committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe";
    private static final String TIER_RECIPE_CLASS =
            "committee.nova.mods.avaritia.api.common.crafting.ITierCraftingRecipe";

    private static volatile boolean lookupDone;
    private static volatile @Nullable Class<?> tableClass;
    private static volatile @Nullable Method getInventoryMethod;
    private static volatile @Nullable Method tierInputOfMethod;
    private static volatile @Nullable Method tierInputTopMethod;
    private static volatile @Nullable Method tierInputLeftMethod;
    private static volatile @Nullable Method shapedRecipeWidthMethod;
    private static volatile @Nullable Method recipeTierMethod;
    private static volatile @Nullable Method recipeHasRequiredTierMethod;

    static boolean isSupportedTable(Object value) {
        ensureLookup();
        return tableClass != null && tableClass.isInstance(value);
    }

    @Nullable
    static IItemHandler getHandler(BlockEntity be) {
        ensureLookup();
        if (getInventoryMethod == null) {
            return null;
        }
        try {
            var value = getInventoryMethod.invoke(be);
            return com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess.wrap(value);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AvaritiaTableAccess.class, "getHandler", ignored);
            return null;
        }
    }

    @Nullable
    static CraftingInput createTierInput(int size, List<ItemStack> stacks, int tier) {
        ensureLookup();
        if (tierInputOfMethod == null) {
            return null;
        }
        try {
            var value = tierInputOfMethod.invoke(null, size, size, stacks, tier);
            return value instanceof CraftingInput input ? input : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AvaritiaTableAccess.class, "createTierInput", ignored);
            return null;
        }
    }

    static int top(CraftingInput input) {
        ensureLookup();
        if (tierInputTopMethod == null) {
            return 0;
        }
        try {
            var value = tierInputTopMethod.invoke(input);
            return value instanceof Integer i ? i : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AvaritiaTableAccess.class, "top", ignored);
            return 0;
        }
    }

    static int left(CraftingInput input) {
        ensureLookup();
        if (tierInputLeftMethod == null) {
            return 0;
        }
        try {
            var value = tierInputLeftMethod.invoke(input);
            return value instanceof Integer i ? i : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AvaritiaTableAccess.class, "left", ignored);
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
            CompatibilityDiagnostics.failure(AvaritiaTableAccess.class, "recipeWidth", ignored);
            return null;
        }
    }

    static int recipeTier(Object recipe) {
        ensureLookup();
        if (recipeTierMethod == null
                || !recipeTierMethod.getDeclaringClass().isInstance(recipe)) {
            return 0;
        }
        try {
            var value = recipeTierMethod.invoke(recipe);
            return value instanceof Integer i ? i : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AvaritiaTableAccess.class, "recipeTier", ignored);
            return 0;
        }
    }

    static boolean recipeHasRequiredTier(Object recipe) {
        ensureLookup();
        if (recipeHasRequiredTierMethod == null
                || !recipeHasRequiredTierMethod.getDeclaringClass().isInstance(recipe)) {
            return false;
        }
        try {
            var value = recipeHasRequiredTierMethod.invoke(recipe);
            return value instanceof Boolean b && b;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AvaritiaTableAccess.class, "recipeHasRequiredTier", ignored);
            return false;
        }
    }

    private static void ensureLookup() {
        if (lookupDone) {
            return;
        }
        synchronized (AvaritiaTableAccess.class) {
            if (lookupDone) {
                return;
            }
            try {
                doLookup();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                if (!(ignored instanceof ClassNotFoundException) || net.neoforged.fml.ModList.get().isLoaded("avaritia")) {
                    CompatibilityDiagnostics.failure(AvaritiaTableAccess.class, "ensureLookup", ignored);
                }
            } finally {
                lookupDone = true;
            }
        }
    }

    private static void doLookup() throws ReflectiveOperationException {
        tableClass = Class.forName(TABLE_CLASS);

        var baseInventoryClass = Class.forName(BASE_INVENTORY_CLASS);
        getInventoryMethod = baseInventoryClass.getMethod("getInventory");

        var tierInputClass = Class.forName(TIER_INPUT_CLASS);
        tierInputOfMethod = tierInputClass.getMethod("of", int.class, int.class, List.class, int.class);
        tierInputTopMethod = tierInputClass.getMethod("top");
        tierInputLeftMethod = tierInputClass.getMethod("left");

        var shapedRecipeClass = Class.forName(SHAPED_RECIPE_CLASS);
        shapedRecipeWidthMethod = shapedRecipeClass.getMethod("getWidth");

        var tierRecipeClass = Class.forName(TIER_RECIPE_CLASS);
        recipeTierMethod = tierRecipeClass.getMethod("getTier");
        recipeHasRequiredTierMethod = tierRecipeClass.getMethod("hasRequiredTier");
    }
}
