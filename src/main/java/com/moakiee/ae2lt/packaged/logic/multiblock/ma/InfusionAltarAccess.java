package com.moakiee.ae2lt.packaged.logic.multiblock.ma;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchCommitException;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

/** Lazy optional-mod API access, using the native Forge 1.20.1 contract. */
final class InfusionAltarAccess {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("ae2ltpp/ma-infusion-reflection");

    private static final String ALTAR_CLASS =
            "com.blakebr0.mysticalagriculture.tileentity.InfusionAltarTileEntity";
    private static final String PEDESTAL_CLASS =
            "com.blakebr0.mysticalagriculture.tileentity.InfusionPedestalTileEntity";
    private static final String INFUSION_RECIPE_API_CLASS =
            "com.blakebr0.mysticalagriculture.api.crafting.IInfusionRecipe";
    private static final String ACTIVATABLE_CLASS =
            "com.blakebr0.mysticalagriculture.util.IActivatable";
    private static final String BASE_INVENTORY_CLASS =
            "com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity";

    private static final ReflectionBootstrap LOOKUP = new ReflectionBootstrap("ma-infusion");
    private static volatile @Nullable Class<?> altarClass;
    private static volatile @Nullable Class<?> pedestalClass;
    private static volatile @Nullable Class<?> infusionRecipeApiClass;
    private static volatile @Nullable Class<?> activatableClass;
    private static volatile @Nullable Method getInventoryMethod;
    private static volatile @Nullable Method activateMethod;
    private static volatile @Nullable Method isActiveMethod;
    private static volatile @Nullable Field progressField;

    static boolean isReady() {
        ensureLookup();
        return altarClass != null
                && pedestalClass != null
                && infusionRecipeApiClass != null
                && activatableClass != null
                && getInventoryMethod != null
                && activateMethod != null
                && isActiveMethod != null
                && progressField != null;
    }

    static boolean isInfusionAltar(Object o) {
        ensureLookup();
        return isReady() && altarClass.isInstance(o);
    }

    static boolean isInfusionPedestal(Object o) {
        ensureLookup();
        return isReady() && pedestalClass.isInstance(o);
    }

    @Nullable
    static IItemHandler getHandler(BlockEntity be) {
        ensureLookup();
        return com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess.get(be, getInventoryMethod);
    }

    static int getProgress(BlockEntity be) {
        ensureLookup();
        if (progressField == null) {
            return Integer.MAX_VALUE;
        }
        try {
            return progressField.getInt(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return Integer.MAX_VALUE;
        }
    }

    static void activateOrThrow(ServerLevel level, BlockPos pos) {
        ensureLookup();
        var be = level.getBlockEntity(pos);
        if (activateMethod == null
                || isActiveMethod == null
                || activatableClass == null
                || be == null
                || !activatableClass.isInstance(be)) {
            throw new DispatchCommitException("MA infusion activation API is unavailable");
        }
        try {
            activateMethod.invoke(be);
            if (!Boolean.TRUE.equals(isActiveMethod.invoke(be))) {
                throw new DispatchCommitException("MA infusion altar remained inactive");
            }
        } catch (DispatchCommitException e) {
            throw e;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            throw new DispatchCommitException("MA infusion activation failed: " + e);
        }
    }

    static boolean isDefinitelyInactive(BlockEntity be) {
        ensureLookup();
        if (isActiveMethod == null
                || activatableClass == null
                || !activatableClass.isInstance(be)) {
            return false;
        }
        try {
            return Boolean.FALSE.equals(isActiveMethod.invoke(be));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    static List<net.minecraft.world.item.crafting.Ingredient> getIngredients(Object recipe) {
        ensureLookup();
        if (!isReady() || !infusionRecipeApiClass.isInstance(recipe)
                || !(recipe instanceof net.minecraft.world.item.crafting.Recipe<?> vanillaRecipe)) {
            return null;
        }
        try {
            var ingredients = vanillaRecipe.getIngredients();
            return List.copyOf(ingredients);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static void ensureLookup() {
        LOOKUP.run(InfusionAltarAccess::doLookup);
    }

    private static void doLookup() {
        altarClass = tryClass(ALTAR_CLASS);
        pedestalClass = tryClass(PEDESTAL_CLASS);
        activatableClass = tryClass(ACTIVATABLE_CLASS);
        infusionRecipeApiClass = tryClass(INFUSION_RECIPE_API_CLASS);

        var baseInventoryClass = tryClass(BASE_INVENTORY_CLASS);
        if (baseInventoryClass != null) {
            getInventoryMethod = tryMethod(baseInventoryClass, "getInventory");
        }
        if (activatableClass != null) {
            activateMethod = tryMethod(activatableClass, "activate");
            isActiveMethod = tryMethod(activatableClass, "isActive");
        }
        // Recipe#getIngredients() is invoked through the vanilla interface;
        // no nonexistent MA altar-ingredient reflection probe is needed.
        if (altarClass != null) {
            progressField = tryField(altarClass, "progress");
        }

        LOG.info("MA infusion reflection ready: ready={} altar={} pedestal={} activatable={} infusionRecipe={} inventory={} activate={} isActive={} ingredients=vanilla progress={}",
                altarClass != null && pedestalClass != null
                        && activatableClass != null && infusionRecipeApiClass != null
                        && getInventoryMethod != null && activateMethod != null
                        && isActiveMethod != null && progressField != null,
                altarClass != null,
                pedestalClass != null,
                activatableClass != null,
                infusionRecipeApiClass != null,
                getInventoryMethod != null,
                activateMethod != null,
                isActiveMethod != null,
                progressField != null);
    }

    @Nullable
    private static Class<?> tryClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | RuntimeException | LinkageError e) {
            LOG.warn("MA infusion class lookup failed: {} ({})", name, e.toString());
            return null;
        }
    }

    @Nullable
    private static Method tryMethod(Class<?> declaring, String name, Class<?>... params) {
        try {
            return declaring.getMethod(name, params);
        } catch (NoSuchMethodException | RuntimeException | LinkageError e) {
            LOG.warn("MA infusion method lookup failed: {}#{} ({})",
                    declaring.getName(), name, e.toString());
            return null;
        }
    }

    @Nullable
    private static Field tryField(Class<?> declaring, String name) {
        try {
            var field = declaring.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException | LinkageError e) {
            LOG.warn("MA infusion field lookup failed: {}#{} ({})",
                    declaring.getName(), name, e.toString());
            return null;
        }
    }
}
