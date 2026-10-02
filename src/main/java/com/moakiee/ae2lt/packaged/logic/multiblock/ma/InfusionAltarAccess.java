package com.moakiee.ae2lt.packaged.logic.multiblock.ma;

import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchCommitException;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Lazy optional-mod API access, separate from recipe planning and dispatch. */
final class InfusionAltarAccess {
    private static final String ALTAR_CLASS =
            "com.blakebr0.mysticalagriculture.tileentity.InfusionAltarTileEntity";
    private static final String PEDESTAL_CLASS =
            "com.blakebr0.mysticalagriculture.tileentity.InfusionPedestalTileEntity";
    private static final String INFUSION_RECIPE_API_CLASS =
            "com.blakebr0.mysticalagriculture.api.crafting.IInfusionRecipe";
    private static final String AWAKENING_RECIPE_API_CLASS =
            "com.blakebr0.mysticalagriculture.api.crafting.IAwakeningRecipe";
    private static final String ACTIVATABLE_CLASS =
            "com.blakebr0.mysticalagriculture.util.IActivatable";

    private static volatile boolean lookupDone;
    private static volatile @Nullable Class<?> altarClass;
    private static volatile @Nullable Class<?> pedestalClass;
    private static volatile @Nullable Class<?> infusionRecipeApiClass;
    private static volatile @Nullable Class<?> awakeningRecipeApiClass;
    private static volatile @Nullable Class<?> activatableClass;
    private static volatile @Nullable Method activateMethod;
    private static volatile @Nullable Method isActiveMethod;
    private static volatile @Nullable Method getAltarIngredientMethod;
    private static volatile @Nullable Field progressField;

    static boolean isInfusionAltar(Object o) {
        ensureLookup();
        return altarClass != null && altarClass.isInstance(o);
    }

    static boolean isInfusionPedestal(Object o) {
        ensureLookup();
        return pedestalClass != null && pedestalClass.isInstance(o);
    }

    static int getProgress(BlockEntity be) {
        ensureLookup();
        if (progressField == null) {
            return 0;
        }
        try {
            return progressField.getInt(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(InfusionAltarAccess.class, "getProgress", ignored);
            return 0;
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
    static net.minecraft.world.item.crafting.Ingredient getAltarIngredient(Object recipe) {
        ensureLookup();
        if (getAltarIngredientMethod == null || !appliesToRecipe(recipe)) {
            return null;
        }
        try {
            var value = getAltarIngredientMethod.invoke(recipe);
            return value instanceof net.minecraft.world.item.crafting.Ingredient ing ? ing : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(InfusionAltarAccess.class, "getAltarIngredient", ignored);
            return null;
        }
    }

    private static boolean appliesToRecipe(Object recipe) {
        if (infusionRecipeApiClass != null && infusionRecipeApiClass.isInstance(recipe)) {
            return true;
        }
        return awakeningRecipeApiClass != null && awakeningRecipeApiClass.isInstance(recipe);
    }

    private static void ensureLookup() {
        if (lookupDone) {
            return;
        }
        synchronized (InfusionAltarAccess.class) {
            if (lookupDone) {
                return;
            }
            try {
                doLookup();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                if (!(ignored instanceof ClassNotFoundException) || net.neoforged.fml.ModList.get().isLoaded("mysticalagriculture")) {
                    CompatibilityDiagnostics.failure(InfusionAltarAccess.class, "ensureLookup", ignored);
                }
            } finally {
                lookupDone = true;
            }
        }
    }

    private static void doLookup() throws ReflectiveOperationException {
        altarClass = Class.forName(ALTAR_CLASS);
        pedestalClass = Class.forName(PEDESTAL_CLASS);
        activatableClass = Class.forName(ACTIVATABLE_CLASS);
        infusionRecipeApiClass = Class.forName(INFUSION_RECIPE_API_CLASS);
        try {
            awakeningRecipeApiClass = Class.forName(AWAKENING_RECIPE_API_CLASS);
        } catch (ClassNotFoundException ignored) {
            awakeningRecipeApiClass = null;
        }

        activateMethod = activatableClass.getMethod("activate");
        isActiveMethod = activatableClass.getMethod("isActive");
        getAltarIngredientMethod = infusionRecipeApiClass.getMethod("getAltarIngredient");

        progressField = altarClass.getDeclaredField("progress");
        progressField.setAccessible(true);
    }
}
