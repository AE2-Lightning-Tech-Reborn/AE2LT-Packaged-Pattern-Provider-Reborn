package com.moakiee.ae2lt.packaged.logic.multiblock.ma;

import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchCommitException;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Lazy optional-mod API access, separate from recipe planning and dispatch. */
final class AwakeningAltarAccess {
    private static final String ALTAR_CLASS =
            "com.blakebr0.mysticalagriculture.tileentity.AwakeningAltarTileEntity";
    private static final String PEDESTAL_CLASS =
            "com.blakebr0.mysticalagriculture.tileentity.AwakeningPedestalTileEntity";
    private static final String VESSEL_CLASS =
            "com.blakebr0.mysticalagriculture.tileentity.EssenceVesselTileEntity";
    private static final String AWAKENING_RECIPE_API_CLASS =
            "com.blakebr0.mysticalagriculture.api.crafting.IAwakeningRecipe";
    private static final String ACTIVATABLE_CLASS =
            "com.blakebr0.mysticalagriculture.util.IActivatable";

    private static volatile boolean lookupDone;
    private static volatile @Nullable Class<?> altarClass;
    private static volatile @Nullable Class<?> pedestalClass;
    private static volatile @Nullable Class<?> vesselClass;
    private static volatile @Nullable Class<?> recipeApiClass;
    private static volatile @Nullable Class<?> activatableClass;
    private static volatile @Nullable Method activateMethod;
    private static volatile @Nullable Method isActiveMethod;
    private static volatile @Nullable Method getAltarIngredientMethod;
    private static volatile @Nullable Method getEssencesMethod;
    private static volatile @Nullable Field progressField;

    static boolean isAwakeningAltar(Object o) {
        ensureLookup();
        return altarClass != null && altarClass.isInstance(o);
    }

    static boolean isAwakeningPedestal(Object o) {
        ensureLookup();
        return pedestalClass != null && pedestalClass.isInstance(o);
    }

    static boolean isEssenceVessel(Object o) {
        ensureLookup();
        return vesselClass != null && vesselClass.isInstance(o);
    }

    static int getProgress(BlockEntity be) {
        ensureLookup();
        if (progressField == null) {
            return 0;
        }
        try {
            return progressField.getInt(be);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AwakeningAltarAccess.class, "getProgress", ignored);
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
            throw new DispatchCommitException("MA awakening activation API is unavailable");
        }
        try {
            activateMethod.invoke(be);
            if (!Boolean.TRUE.equals(isActiveMethod.invoke(be))) {
                throw new DispatchCommitException("MA awakening altar remained inactive");
            }
        } catch (DispatchCommitException e) {
            throw e;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            throw new DispatchCommitException("MA awakening activation failed: " + e);
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
    static Ingredient getAltarIngredient(Object recipe) {
        ensureLookup();
        if (getAltarIngredientMethod == null || recipeApiClass == null
                || !recipeApiClass.isInstance(recipe)) {
            return null;
        }
        try {
            var value = getAltarIngredientMethod.invoke(recipe);
            return value instanceof Ingredient ing ? ing : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AwakeningAltarAccess.class, "getAltarIngredient", ignored);
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static List<ItemStack> getEssences(Object recipe) {
        ensureLookup();
        if (getEssencesMethod == null || recipeApiClass == null
                || !recipeApiClass.isInstance(recipe)) {
            return null;
        }
        try {
            var value = getEssencesMethod.invoke(recipe);
            if (!(value instanceof List<?> list)) {
                return null;
            }
            var result = new ArrayList<ItemStack>(list.size());
            for (var item : list) {
                if (!(item instanceof ItemStack stack)) {
                    return null;
                }
                result.add(stack);
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AwakeningAltarAccess.class, "getEssences", ignored);
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static List<Ingredient> getIngredients(Object recipe) {
        if (!(recipe instanceof net.minecraft.world.item.crafting.Recipe<?> typed)) {
            return null;
        }
        try {
            Object value = typed.getIngredients();
            if (!(value instanceof List<?> list)) {
                return null;
            }
            var result = new ArrayList<Ingredient>(list.size());
            for (var item : list) {
                if (!(item instanceof Ingredient ingredient)) {
                    return null;
                }
                result.add(ingredient);
            }
            return result;
        } catch (RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AwakeningAltarAccess.class, "getIngredients", ignored);
            return null;
        }
    }

    private static void ensureLookup() {
        if (lookupDone) {
            return;
        }
        synchronized (AwakeningAltarAccess.class) {
            if (lookupDone) {
                return;
            }
            try {
                doLookup();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                if (!(ignored instanceof ClassNotFoundException) || net.neoforged.fml.ModList.get().isLoaded("mysticalagriculture")) {
                    CompatibilityDiagnostics.failure(AwakeningAltarAccess.class, "ensureLookup", ignored);
                }
            } finally {
                lookupDone = true;
            }
        }
    }

    private static void doLookup() throws ReflectiveOperationException {
        altarClass = Class.forName(ALTAR_CLASS);
        pedestalClass = Class.forName(PEDESTAL_CLASS);
        vesselClass = Class.forName(VESSEL_CLASS);
        recipeApiClass = Class.forName(AWAKENING_RECIPE_API_CLASS);
        activatableClass = Class.forName(ACTIVATABLE_CLASS);

        activateMethod = activatableClass.getMethod("activate");
        isActiveMethod = activatableClass.getMethod("isActive");
        getAltarIngredientMethod = recipeApiClass.getMethod("getAltarIngredient");
        getEssencesMethod = recipeApiClass.getMethod("getEssences");

        progressField = altarClass.getDeclaredField("progress");
        progressField.setAccessible(true);
    }
}
