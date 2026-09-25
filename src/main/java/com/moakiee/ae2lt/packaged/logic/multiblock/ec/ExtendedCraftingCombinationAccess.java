package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

/** Lazy optional-mod API access, using the native Forge 1.20.1 contract. */
final class ExtendedCraftingCombinationAccess {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ExtendedCraftingCombinationAccess.class);
    private static final String BASE_INVENTORY_CLASS =
            "com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity";
    private static final String CORE_TILE_CLASS =
            "com.blakebr0.extendedcrafting.tileentity.CraftingCoreTileEntity";
    private static final String COMBINATION_RECIPE_CLASS =
            "com.blakebr0.extendedcrafting.crafting.recipe.CombinationRecipe";

    private static final ReflectionBootstrap LOOKUP = new ReflectionBootstrap("ec-combination");
    private static volatile @Nullable Method getInventoryMethod;
    private static volatile @Nullable Method getProgressMethod;
    private static volatile boolean inventoryInvokeDiagnosticLogged;
    private static volatile boolean progressInvokeDiagnosticLogged;

    @Nullable
    static IItemHandler getInventory(BlockEntity be) {
        ensureLookup();
        return com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess.get(be, getInventoryMethod);
    }

    static int getProgress(BlockEntity be) {
        ensureLookup();
        if (getProgressMethod == null) return -1;
        try {
            var value = getProgressMethod.invoke(be);
            return value instanceof Number number ? number.intValue() : -1;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!progressInvokeDiagnosticLogged) {
                progressInvokeDiagnosticLogged = true;
                LOG.warn("Combination reflection: getProgress invocation failed for {}: {}",
                        be.getClass().getName(), e.toString());
            }
            return -1;
        }
    }

    @Nullable
    static ItemStack getResultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> r)) return null;
        try {
            return r.getResultItem(level.registryAccess());
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    static Ingredient getCatalyst(Object recipe) {
        // 1.20.x CombinationRecipe stores the core input at index 0 of its
        // single ingredient list (no getInput(); that split only exists on
        // 1.21).
        var all = allIngredients(recipe);
        if (all == null || all.isEmpty()) return null;
        var first = all.get(0);
        return first.isEmpty() ? null : first;
    }

    @Nullable
    static List<Ingredient> getRecipeIngredients(Object recipe) {
        // Pedestal ingredients are everything after the index-0 core input.
        var all = allIngredients(recipe);
        if (all == null || all.size() < 1) return null;
        return new ArrayList<>(all.subList(1, all.size()));
    }

    @Nullable
    private static List<Ingredient> allIngredients(Object recipe) {
        if (!(recipe instanceof Recipe<?> r)) return null;
        try {
            return r.getIngredients();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static void ensureLookup() {
        LOOKUP.run(() -> {
            try {
                var baseInventoryClass = Class.forName(BASE_INVENTORY_CLASS);
                getInventoryMethod = baseInventoryClass.getMethod("getInventory");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.warn("Combination reflection: BaseInventoryTileEntity#getInventory lookup failed: {}",
                        e.toString());
            }
            try {
                var coreClass = Class.forName(CORE_TILE_CLASS);
                getProgressMethod = coreClass.getMethod("getProgress");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.warn("Combination reflection: CraftingCoreTileEntity#getProgress lookup failed: {}",
                        e.toString());
            }
            LOG.info("Extended Crafting Combination reflection ready: inventory={} progress={} "
                            + "catalyst=recipe.getIngredients()[0]",
                    methodName(getInventoryMethod), methodName(getProgressMethod));
        });
    }

    private static String methodName(@Nullable Method method) {
        return method == null
                ? "unresolved"
                : method.getDeclaringClass().getName() + "#" + method.getName();
    }
}
