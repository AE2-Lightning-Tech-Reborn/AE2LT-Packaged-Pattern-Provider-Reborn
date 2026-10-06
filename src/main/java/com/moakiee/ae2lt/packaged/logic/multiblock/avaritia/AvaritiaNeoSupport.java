package com.moakiee.ae2lt.packaged.logic.multiblock.avaritia;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.Container;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;

final class AvaritiaNeoSupport {
    private static final String TABLE_CLASS = "net.byAqua3.avaritia.tile.TileExtremeCraftingTable";
    private static final String SHAPED_RECIPE_CLASS = "net.byAqua3.avaritia.recipe.RecipeExtremeShaped";

    private AvaritiaNeoSupport() {
    }

    static boolean isAvailable() {
        return ReflectionSupport.findClassCached(TABLE_CLASS).isPresent();
    }

    static boolean isSupportedTable(Object table) {
        return ReflectionSupport.findClassCached(TABLE_CLASS)
                .map(type -> type.isInstance(table)).orElse(false);
    }

    @Nullable
    static IItemHandler getHandler(Object table) {
        var matrix = container(table, "matrix");
        return matrix != null && matrix.getContainerSize() == 81 ? new InvWrapper(matrix) : null;
    }

    static boolean resultIsEmpty(Object table) {
        var result = container(table, "result");
        return result != null && result.getContainerSize() == 1 && result.isEmpty();
    }

    @Nullable
    private static Container container(Object table, String name) {
        if (!isSupportedTable(table)) {
            return null;
        }
        var field = ReflectionSupport.findFieldCached(table.getClass(), name).orElse(null);
        if (field == null) {
            return null;
        }
        try {
            return field.get(table) instanceof Container container ? container : null;
        } catch (IllegalAccessException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    static Integer recipeDimension(Object recipe, String name) {
        var type = ReflectionSupport.findClassCached(SHAPED_RECIPE_CLASS).orElse(null);
        if (type == null || !type.isInstance(recipe)) {
            return null;
        }
        var method = ReflectionSupport.findMethodCached(type, name).orElse(null);
        if (method == null) {
            return null;
        }
        return ReflectionSupport.invoke(method, recipe)
                .filter(Integer.class::isInstance).map(Integer.class::cast).orElse(null);
    }
}
