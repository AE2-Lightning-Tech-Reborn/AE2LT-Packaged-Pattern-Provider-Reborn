package com.moakiee.ae2lt.packaged.logic.multiblock.avaritia;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

/** Lazy optional-mod API access, using the native Forge 1.20.1 contract. */
final class AvaritiaTableAccess {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(AvaritiaTableAccess.class);
    private static final String[] TABLE_CLASSES = {
            "committee.nova.mods.avaritia.common.tile.TierCraftTile",
            "committee.nova.mods.avaritia.common.tile.tiers.TierCraftTile"
    };
    private static final String BASE_INVENTORY_CLASS =
            "committee.nova.mods.avaritia.api.common.tile.BaseInventoryTileEntity";
    private static final String SHAPED_RECIPE_CLASS =
            "committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe";
    // Re-Avaritia moved this interface between releases: 1.20.1 ships it in
    // common.crafting.recipe, older builds in api.common.crafting (as
    // ITierRecipe). Both declare getTier()/hasRequiredTier().
    private static final String TIER_RECIPE_CLASS =
            "committee.nova.mods.avaritia.common.crafting.recipe.ITierCraftingRecipe";
    private static final String TIER_RECIPE_API_CLASS =
            "committee.nova.mods.avaritia.api.common.crafting.ITierRecipe";
    private static final String[] HANDLER_RECIPE_CLASSES = {
            SHAPED_RECIPE_CLASS,
            TIER_RECIPE_CLASS,
            TIER_RECIPE_API_CLASS
    };

    /**
     * Menu shell for offline grid containers: {@link CraftingContainer}
     * calls back into {@code slotsChanged} on every write, which is a
     * no-op on the vanilla base class.
     */
    private static final AbstractContainerMenu NOOP_MENU = new AbstractContainerMenu(null, 0) {
        @Override
        public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return true;
        }
    };

    private static final ReflectionBootstrap LOOKUP = new ReflectionBootstrap("avaritia-table");
    private static volatile @Nullable Class<?> tableClass;
    private static volatile @Nullable Method getInventoryMethod;
    private static volatile @Nullable Method shapedRecipeWidthMethod;
    private static volatile @Nullable Method shapedRecipeHeightMethod;
    private static volatile @Nullable Method handlerRemainingItemsMethod;
    private static volatile List<TierRecipeAccess> tierRecipeAccesses = List.of();

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
            // 1.20.1: getInventory() returns ItemStackWrapper, which
            // extends ItemStackHandler and therefore IS an IItemHandler.
            return value instanceof IItemHandler handler ? handler : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * Builds the craft grid directly instead of going through the 1.21
     * {@code TierInput} API, which does not exist on 1.20.1. The table
     * recipes accept any {@code Container} ({@code ISpecialRecipe} wraps
     * it in an {@code InvWrapper}) and derive the tier themselves from
     * the slot count, so a full square grid reproduces the real table
     * exactly.
     */
    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    static NonNullList<ItemStack> remainingItems(Object recipe, CraftingContainer input) {
        ensureLookup();
        if (!(recipe instanceof Recipe<?>)) {
            return null;
        }
        Method method = handlerRemainingItemsMethod;
        if (method == null || !method.getDeclaringClass().isInstance(recipe)) {
            try {
                method = recipe.getClass().getMethod("getRemainingItems", IItemHandler.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }
        try {
            var handler = new InvWrapper(input);
            var value = method.invoke(recipe, handler);
            return value instanceof NonNullList<?> list
                    ? (NonNullList<ItemStack>) (NonNullList) list
                    : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * Builds the craft grid directly instead of going through the 1.21
     * {@code TierInput} API, which does not exist on 1.20.1. The table
     * recipes accept any {@code Container} ({@code ISpecialRecipe} wraps
     * it in an {@code InvWrapper}) and derive the tier themselves from
     * the slot count, so a full square grid reproduces the real table
     * exactly.
     */
    @Nullable
    static CraftingContainer createGridInput(int size, List<ItemStack> stacks) {
        ensureLookup();
        if (tableClass == null || stacks.size() != size * size) {
            return null;
        }
        try {
            // CraftingContainer is an interface on 1.20.1; this is its
            // concrete vanilla implementation.
            var container = new TransientCraftingContainer(NOOP_MENU, size, size);
            for (int i = 0; i < container.getContainerSize(); i++) {
                container.setItem(i, stacks.get(i).copy());
            }
            return container;
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("[ae2ltpp] Failed to build Avaritia table grid input: {}", e.toString());
            return null;
        }
    }

    @Nullable
    static Integer recipeWidth(Object recipe) {
        ensureLookup();
        return invokeDimension(shapedRecipeWidthMethod, recipe);
    }

    static Integer recipeHeight(Object recipe) {
        ensureLookup();
        return invokeDimension(shapedRecipeHeightMethod, recipe);
    }

    @Nullable
    private static Integer invokeDimension(@Nullable Method method, Object recipe) {
        if (method == null || !method.getDeclaringClass().isInstance(recipe)) {
            return null;
        }
        try {
            var value = method.invoke(recipe);
            return value instanceof Integer i ? i : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static AvaritiaTableSpecs.RecipeTier recipeTier(Object recipe) {
        ensureLookup();
        for (var access : tierRecipeAccesses) {
            if (access.recipeClass().isInstance(recipe)) {
                return invokeTier(access.tierMethod(), access.requiredTierMethod(), recipe);
            }
        }

        Method tierMethod = publicMethod(recipe.getClass(), "getTier");
        Method requiredTierMethod = publicMethod(recipe.getClass(), "hasRequiredTier");
        if (tierMethod == null && requiredTierMethod == null) {
            return AvaritiaTableSpecs.RecipeTier.untiered();
        }
        return invokeTier(tierMethod, requiredTierMethod, recipe);
    }

    private static AvaritiaTableSpecs.RecipeTier invokeTier(
            @Nullable Method tierMethod, @Nullable Method requiredTierMethod, Object recipe) {
        if (tierMethod == null || requiredTierMethod == null) {
            return AvaritiaTableSpecs.RecipeTier.unknownTiered();
        }
        try {
            var tierValue = tierMethod.invoke(recipe);
            var requiredTierValue = requiredTierMethod.invoke(recipe);
            if (tierValue instanceof Integer tier && tier > 0
                    && requiredTierValue instanceof Boolean requiredTier) {
                return AvaritiaTableSpecs.RecipeTier.known(tier, requiredTier);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        return AvaritiaTableSpecs.RecipeTier.unknownTiered();
    }

    @Nullable
    private static Method publicMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static String matchedTableClassName() {
        ensureLookup();
        return tableClass == null ? "unresolved" : tableClass.getName();
    }

    static String inventoryMethodName() {
        ensureLookup();
        return methodName(getInventoryMethod);
    }

    static String shapedWidthMethodName() {
        ensureLookup();
        return methodName(shapedRecipeWidthMethod);
    }

    static String tierMethodNames() {
        ensureLookup();
        if (tierRecipeAccesses.isEmpty()) {
            return "unresolved,unresolved";
        }
        var names = new ArrayList<String>(tierRecipeAccesses.size());
        for (var access : tierRecipeAccesses) {
            names.add(methodName(access.tierMethod()) + "," + methodName(access.requiredTierMethod()));
        }
        return String.join(";", names);
    }

    private static String methodName(@Nullable Method method) {
        return method == null
                ? "unresolved"
                : method.getDeclaringClass().getName() + "#" + method.getName();
    }

    private static void ensureLookup() {
        // Per-member resolution: one drifted name must not disable
        // the whole adapter (the pre-1.20 single try-block did).
        LOOKUP.run(AvaritiaTableAccess::doLookup);
    }

    private static void doLookup() {
        for (String candidate : TABLE_CLASSES) {
            try {
                tableClass = Class.forName(candidate);
                break;
            } catch (ClassNotFoundException | RuntimeException | LinkageError e) {
                LOG.warn("[ae2ltpp] Avaritia table tile {} not found: {}", candidate, e.toString());
            }
        }
        if (tableClass == null) {
            LOG.warn("[ae2ltpp] No supported Avaritia table tile class was found");
        }

        try {
            var baseInventoryClass = Class.forName(BASE_INVENTORY_CLASS);
            getInventoryMethod = baseInventoryClass.getMethod("getInventory");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[ae2ltpp] Avaritia BaseInventoryTileEntity#getInventory lookup failed: {}", e.toString());
        }

        try {
            shapedRecipeWidthMethod = Class.forName(SHAPED_RECIPE_CLASS).getMethod("getWidth");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[ae2ltpp] Avaritia ShapedTableCraftingRecipe#getWidth lookup failed: {}", e.toString());
        }
        try {
            shapedRecipeHeightMethod = Class.forName(SHAPED_RECIPE_CLASS).getMethod("getHeight");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[ae2ltpp] Avaritia ShapedTableCraftingRecipe#getHeight lookup failed: {}", e.toString());
        }
        for (String candidate : HANDLER_RECIPE_CLASSES) {
            try {
                handlerRemainingItemsMethod = Class.forName(candidate)
                        .getMethod("getRemainingItems", IItemHandler.class);
                break;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.debug("[ae2ltpp] Avaritia handler remainder method unavailable on {}: {}",
                        candidate, e.toString());
            }
        }
        if (handlerRemainingItemsMethod == null) {
            LOG.warn("[ae2ltpp] No Avaritia handler-based getRemainingItems(IItemHandler) method found");
        }

        var tierAccesses = new ArrayList<TierRecipeAccess>();
        for (String candidate : new String[] {TIER_RECIPE_CLASS, TIER_RECIPE_API_CLASS}) {
            try {
                var tierRecipeClass = Class.forName(candidate);
                var tierMethod = publicMethod(tierRecipeClass, "getTier");
                var requiredTierMethod = publicMethod(tierRecipeClass, "hasRequiredTier");
                tierAccesses.add(new TierRecipeAccess(
                        tierRecipeClass, tierMethod, requiredTierMethod));
                if (tierMethod == null || requiredTierMethod == null) {
                    LOG.warn("[ae2ltpp] Avaritia tier recipe interface {} has incomplete tier methods", candidate);
                }
            } catch (ClassNotFoundException | RuntimeException | LinkageError e) {
                LOG.warn("[ae2ltpp] Avaritia tier recipe interface {} unavailable: {}", candidate, e.toString());
            }
        }
        tierRecipeAccesses = List.copyOf(tierAccesses);
    }

    private record TierRecipeAccess(Class<?> recipeClass,
                                    @Nullable Method tierMethod,
                                    @Nullable Method requiredTierMethod) {
    }
}
