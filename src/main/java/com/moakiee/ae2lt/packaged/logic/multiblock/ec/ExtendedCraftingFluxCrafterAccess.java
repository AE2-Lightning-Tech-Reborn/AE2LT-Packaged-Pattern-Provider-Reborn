package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import java.lang.reflect.Method;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

/** Lazy optional-mod API access, using the native Forge 1.20.1 contract. */
final class ExtendedCraftingFluxCrafterAccess {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ExtendedCraftingFluxCrafterAccess.class);
    private static final String BASE_INVENTORY_CLASS =
            "com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity";
    private static final String SHAPED_RECIPE_PATTERN =
            "net.minecraft.world.item.crafting.ShapedRecipePattern";

    private static final ReflectionBootstrap LOOKUP = new ReflectionBootstrap("ec-flux-crafter");
    private static volatile @Nullable Method getInventoryMethod;
    private static volatile @Nullable Method shapedWidthMethod;
    private static volatile @Nullable Method shapedHeightMethod;
    private static volatile @Nullable Class<?> shapedRecipePatternClass;

    @Nullable
    static IItemHandler getInventory(BlockEntity be) {
        ensureLookup();
        return com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess.get(be, getInventoryMethod);
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

    static int getShapedWidth(Object recipe) {
        return getShapedDimension(recipe, shapedWidthMethod, "getWidth");
    }

    static int getShapedHeight(Object recipe) {
        return getShapedDimension(recipe, shapedHeightMethod, "getHeight");
    }

    /**
     * 1.20.5+ keeps shaped dimensions in {@code ShapedRecipePattern}; on
     * 1.20.x EC's shaped recipes declare {@code getWidth()}/{@code
     * getHeight()} directly (shapeless variants have neither, which
     * classifies them as shapeless).
     */
    private static int getShapedDimension(Object recipe, @Nullable Method patternMethod,
                                          String directName) {
        if (patternMethod != null && shapedRecipePatternClass != null) {
            var pattern = findPattern(recipe);
            if (pattern != null) {
                try {
                    var value = patternMethod.invoke(pattern);
                    if (value instanceof Integer i) return i;
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                }
            }
        }
        var direct = ReflectionSupport.findMethodCached(recipe.getClass(), directName).orElse(null);
        if (direct == null) return -1;
        try {
            var value = direct.invoke(recipe);
            return value instanceof Integer i ? i : -1;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return -1;
        }
    }

    /** Same recipe-class walk as EnderCrafter — see that adapter's doc. */
    @Nullable
    private static Object findPattern(Object recipe) {
        var patternClass = shapedRecipePatternClass;
        if (patternClass == null) return null;
        for (Class<?> c = recipe.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (var f : c.getDeclaredFields()) {
                if (patternClass.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return f.get(recipe);
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static void ensureLookup() {
        LOOKUP.run(() -> {
            try {
                var baseInventoryClass = Class.forName(BASE_INVENTORY_CLASS);
                getInventoryMethod = baseInventoryClass.getMethod("getInventory");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.warn("FluxCrafter reflection: BaseInventoryTileEntity#getInventory lookup failed: {}",
                        e.toString());
            }
            try {
                var patternClass = Class.forName(SHAPED_RECIPE_PATTERN);
                shapedRecipePatternClass = patternClass;
                shapedWidthMethod = patternClass.getMethod("width");
                shapedHeightMethod = patternClass.getMethod("height");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                // 1.20.x: no ShapedRecipePattern; shaped EC recipes carry
                // their own getWidth()/getHeight() — resolved per call.
                shapedRecipePatternClass = null;
                shapedWidthMethod = null;
                shapedHeightMethod = null;
            }
        });
    }
}
