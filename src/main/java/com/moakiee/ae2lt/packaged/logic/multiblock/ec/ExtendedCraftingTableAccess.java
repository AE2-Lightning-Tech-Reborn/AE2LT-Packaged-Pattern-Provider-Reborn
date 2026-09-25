package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

/** Lazy optional-mod API access, using the native Forge 1.20.1 contract. */
final class ExtendedCraftingTableAccess {
    private static final String BASIC_TABLE_CLASS =
            "com.blakebr0.extendedcrafting.tileentity.BasicTableTileEntity";
    private static final String ADVANCED_TABLE_CLASS =
            "com.blakebr0.extendedcrafting.tileentity.AdvancedTableTileEntity";
    private static final String ELITE_TABLE_CLASS =
            "com.blakebr0.extendedcrafting.tileentity.EliteTableTileEntity";
    private static final String ULTIMATE_TABLE_CLASS =
            "com.blakebr0.extendedcrafting.tileentity.UltimateTableTileEntity";
    private static final String BASE_INVENTORY_CLASS =
            "com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity";

    /** Menu owner stub; the container is never opened as a screen. */
    private static final AbstractContainerMenu DUMMY_MENU = new AbstractContainerMenu(null, 0) {
        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    };

    private static final ReflectionBootstrap LOOKUP = new ReflectionBootstrap("ec-table");
    private static volatile @Nullable Set<Class<?>> tableClasses;
    private static volatile @Nullable Method getInventoryMethod;
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
    static IItemHandler getHandler(BlockEntity be) {
        ensureLookup();
        return com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess.get(be, getInventoryMethod);
    }

    /**
     * 1.20.x has no {@code TableCraftingInput} API class; the tiles hand
     * their full {@code gridSize x gridSize} inventory straight to
     * {@code matches(IItemHandler)}. A vanilla {@link CraftingContainer}
     * of the same shape reproduces that exactly (offset is always the
     * grid origin), and Cucumber's {@code ISpecialRecipe} defaults bridge
     * {@code Container} to {@code IItemHandler} for the generic
     * {@code matches/assemble/getRemainingItems} entry points.
     */
    @Nullable
    static CraftingContainer createTableInput(int size, List<ItemStack> stacks, int tier) {
        if (stacks.size() != size * size) {
            return null;
        }
        var container = new TransientCraftingContainer(DUMMY_MENU, size, size);
        for (int i = 0; i < stacks.size(); i++) {
            container.setItem(i, stacks.get(i).copy());
        }
        return container;
    }

    // The container covers the whole grid, so a matched recipe's window
    // starts at the table origin.
    static int top(CraftingContainer input) {
        return 0;
    }

    static int left(CraftingContainer input) {
        return 0;
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
            return null;
        }
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
        tableClasses = Set.of(
                Class.forName(BASIC_TABLE_CLASS),
                Class.forName(ADVANCED_TABLE_CLASS),
                Class.forName(ELITE_TABLE_CLASS),
                Class.forName(ULTIMATE_TABLE_CLASS));

        var baseInventoryClass = Class.forName(BASE_INVENTORY_CLASS);
        getInventoryMethod = baseInventoryClass.getMethod("getInventory");

        var shapedRecipeClass = Class.forName("com.blakebr0.extendedcrafting.crafting.recipe.ShapedTableRecipe");
        shapedRecipeWidthMethod = shapedRecipeClass.getMethod("getWidth");
    }
}
