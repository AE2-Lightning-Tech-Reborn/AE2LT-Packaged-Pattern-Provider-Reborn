package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

/** Lazy optional-mod API access, separate from recipe planning and dispatch. */
final class ExtendedCraftingEnderCrafterAccess {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ExtendedCraftingEnderCrafterAccess.class);
    private static final String SHAPED_RECIPE_PATTERN =
            "net.minecraft.world.item.crafting.ShapedRecipePattern";

    private static volatile boolean lookupDone;
    private static volatile @Nullable Method shapedWidthMethod;
    private static volatile @Nullable Method shapedHeightMethod;
    private static volatile @Nullable Class<?> shapedRecipePatternClass;

    @Nullable
    static ItemStack getResultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> r)) return null;
        try {
            return r.getResultItem(level.registryAccess());
        } catch (RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ExtendedCraftingEnderCrafterAccess.class, "getResultItem", ignored);
            return null;
        }
    }

    static int getShapedWidth(Object recipe) {
        ensureLookup();
        return invokePatternDimension(recipe, shapedWidthMethod);
    }

    static int getShapedHeight(Object recipe) {
        ensureLookup();
        return invokePatternDimension(recipe, shapedHeightMethod);
    }

    private static int invokePatternDimension(Object recipe, @Nullable Method m) {
        if (m == null || shapedRecipePatternClass == null) return -1;
        var pattern = findPattern(recipe);
        if (pattern == null) return -1;
        try {
            var value = m.invoke(pattern);
            return value instanceof Integer i ? i : -1;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ExtendedCraftingEnderCrafterAccess.class, "invokePatternDimension", ignored);
            return -1;
        }
    }

    /**
     * Walks the recipe class hierarchy looking for any declared field
     * whose type is {@code ShapedRecipePattern}. Avoids hard-coding the
     * field name ({@code pattern}) and copes with private visibility.
     */
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
                        CompatibilityDiagnostics.failure(ExtendedCraftingEnderCrafterAccess.class, "findPattern", ignored);
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static void ensureLookup() {
        if (lookupDone) return;
        synchronized (ExtendedCraftingEnderCrafterAccess.class) {
            if (lookupDone) return;

            try {
                var patternClass = Class.forName(SHAPED_RECIPE_PATTERN);
                shapedRecipePatternClass = patternClass;
                shapedWidthMethod = patternClass.getMethod("width");
                shapedHeightMethod = patternClass.getMethod("height");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.warn("EnderCrafter reflection: ShapedRecipePattern dimension methods missing: {}",
                        e.toString());
            }
            lookupDone = true;
        }
    }
}
