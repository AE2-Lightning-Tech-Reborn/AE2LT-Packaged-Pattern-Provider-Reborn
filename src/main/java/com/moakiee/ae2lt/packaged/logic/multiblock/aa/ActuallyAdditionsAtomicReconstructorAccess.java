package com.moakiee.ae2lt.packaged.logic.multiblock.aa;

import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Lazy optional-mod API access, separate from recipe planning and dispatch. */
final class ActuallyAdditionsAtomicReconstructorAccess {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("ae2ltpp/aa-reconstructor");

    private static final String RECONSTRUCTOR_CLASS =
            "de.ellpeck.actuallyadditions.mod.tile.TileEntityAtomicReconstructor";
    private static final String LENS_CONVERSION_CLASS =
            "de.ellpeck.actuallyadditions.api.lens.LensConversion";
    private static final String LASER_RECIPE_CLASS =
            "de.ellpeck.actuallyadditions.mod.crafting.LaserRecipe";
    private static final String API_CLASS =
            "de.ellpeck.actuallyadditions.api.ActuallyAdditionsAPI";
    private static final String METHOD_HANDLER_CLASS =
            "de.ellpeck.actuallyadditions.api.internal.IMethodHandler";
    private static final String ENERGY_TILE_INTERFACE =
            "de.ellpeck.actuallyadditions.api.internal.IEnergyTile";
    private static final String ATOMIC_RECONSTRUCTOR_INTERFACE =
            "de.ellpeck.actuallyadditions.api.internal.IAtomicReconstructor";

    private static volatile boolean lookupDone;
    private static volatile @Nullable Class<?> reconstructorClass;
    private static volatile @Nullable Class<?> laserRecipeClass;
    private static volatile @Nullable Field methodHandlerField;
    private static volatile @Nullable Method getLensMethod;
    private static volatile @Nullable Method invokeReconstructorMethod;
    private static volatile @Nullable Method getEnergyMethod;
    private static volatile @Nullable Method extractEnergyMethod;
    private static volatile @Nullable Method getInputMethod;
    private static volatile @Nullable Method getRecipeEnergyMethod;

    static boolean isAtomicReconstructor(Object o) {
        ensureLookup();
        return reconstructorClass != null && reconstructorClass.isInstance(o);
    }

    static boolean hasDefaultConversionLens(BlockEntity be) {
        ensureLookup();
        if (getLensMethod == null || !isAtomicReconstructor(be)) {
            return false;
        }
        try {
            var lens = getLensMethod.invoke(be);
            if (lens == null) {
                return false;
            }
            // Class-name string compare (instead of Class.forName-based
            // isInstance) so we don't depend on the LensConversion class
            // being loadable from our classloader. AA's
            // {@code TileEntityAtomicReconstructor.getLens()} returns
            // either the static {@code ActuallyAdditionsAPI.lensDefaultConversion}
            // singleton (empty slot — typed LensConversion) or a custom
            // {@code Lens} subclass when a specialized lens item is installed.
            // Only LensConversion is virtualizable; everything else
            // (LensMining / LensDeath / LensColor / …) has side effects we
            // can't faithfully replay without world interaction.
            return lens.getClass().getName().equals(LENS_CONVERSION_CLASS);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ActuallyAdditionsAtomicReconstructorAccess.class, "hasDefaultConversionLens", ignored);
            return false;
        }
    }

    static boolean invokeReconstructor(BlockEntity be) {
        ensureLookup();
        if (methodHandlerField == null || invokeReconstructorMethod == null || !isAtomicReconstructor(be)) {
            return false;
        }
        try {
            var handler = methodHandlerField.get(null);
            if (handler == null) {
                return false;
            }
            var value = invokeReconstructorMethod.invoke(handler, be);
            return value instanceof Boolean b && b;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ActuallyAdditionsAtomicReconstructorAccess.class, "invokeReconstructor", ignored);
            return false;
        }
    }

    static int getEnergy(BlockEntity be) {
        ensureLookup();
        if (getEnergyMethod == null || !isAtomicReconstructor(be)) {
            return 0;
        }
        try {
            var value = getEnergyMethod.invoke(be);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ActuallyAdditionsAtomicReconstructorAccess.class, "getEnergy", ignored);
            return 0;
        }
    }

    /**
     * Drains {@code amount} from the reconstructor's internal storage. AA
     * routes this through {@code TileEntityAtomicReconstructor.extractEnergy(int)},
     * which delegates to the underlying {@code storage.extractEnergyInternal}.
     *
     * @return false when the lookup or call fails — caller treats this as
     *         "not enough charge / wrong wiring" and aborts.
     */
    static boolean extractEnergy(BlockEntity be, int amount) {
        ensureLookup();
        if (extractEnergyMethod == null || !isAtomicReconstructor(be)) {
            return false;
        }
        if (amount <= 0) {
            return true;
        }
        try {
            extractEnergyMethod.invoke(be, amount);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ActuallyAdditionsAtomicReconstructorAccess.class, "extractEnergy", ignored);
            return false;
        }
    }

    @Nullable
    static Ingredient getInput(Object recipe) {
        ensureLookup();
        if (getInputMethod == null || !isLaserRecipe(recipe)) {
            return null;
        }
        try {
            var value = getInputMethod.invoke(recipe);
            return value instanceof Ingredient ingredient ? ingredient : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ActuallyAdditionsAtomicReconstructorAccess.class, "getInput", ignored);
            return null;
        }
    }

    static int getRecipeEnergy(Object recipe) {
        ensureLookup();
        if (getRecipeEnergyMethod == null || !isLaserRecipe(recipe)) {
            return 0;
        }
        try {
            var value = getRecipeEnergyMethod.invoke(recipe);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ActuallyAdditionsAtomicReconstructorAccess.class, "getRecipeEnergy", ignored);
            return 0;
        }
    }

    private static boolean isLaserRecipe(Object recipe) {
        return laserRecipeClass != null && laserRecipeClass.isInstance(recipe);
    }

    private static void ensureLookup() {
        if (lookupDone) {
            return;
        }
        synchronized (ActuallyAdditionsAtomicReconstructorAccess.class) {
            if (lookupDone) {
                return;
            }
            doLookup();
            lookupDone = true;
        }
    }

    /**
     * Best-effort reflection bootstrap. Each step is independently
     * try/catch'd so a single broken signature (e.g. AA renaming a
     * method in a future release) only disables the affected feature,
     * not the whole adapter. Failures are logged at WARN so an admin
     * can spot mismatches without having to reconfigure log levels.
     */
    private static void doLookup() {
        reconstructorClass = tryClass(RECONSTRUCTOR_CLASS);
        laserRecipeClass = tryClass(LASER_RECIPE_CLASS);
        var apiClass = tryClass(API_CLASS);
        var methodHandlerClass = tryClass(METHOD_HANDLER_CLASS);
        var atomicReconstructorInterface = tryClass(ATOMIC_RECONSTRUCTOR_INTERFACE);
        var energyTileInterface = tryClass(ENERGY_TILE_INTERFACE);

        if (apiClass != null) {
            methodHandlerField = tryField(apiClass, "methodHandler");
        }
        if (methodHandlerClass != null && atomicReconstructorInterface != null) {
            invokeReconstructorMethod = tryMethod(methodHandlerClass, "invokeReconstructor",
                    atomicReconstructorInterface);
        }
        if (atomicReconstructorInterface != null) {
            getLensMethod = tryMethod(atomicReconstructorInterface, "getLens");
        }
        if (energyTileInterface != null) {
            getEnergyMethod = tryMethod(energyTileInterface, "getEnergy");
            extractEnergyMethod = tryMethod(energyTileInterface, "extractEnergy", int.class);
        }
        if (laserRecipeClass != null) {
            getInputMethod = tryMethod(laserRecipeClass, "getInput");
            getRecipeEnergyMethod = tryMethod(laserRecipeClass, "getEnergy");
        }

        LOG.info("AA reflection ready: reconstructor={} laser={} methodHandler={} invoke={} getLens={} getEnergy={} extractEnergy={} getInput={} recipeEnergy={}",
                reconstructorClass != null,
                laserRecipeClass != null,
                methodHandlerField != null,
                invokeReconstructorMethod != null,
                getLensMethod != null,
                getEnergyMethod != null,
                extractEnergyMethod != null,
                getInputMethod != null,
                getRecipeEnergyMethod != null);
    }

    @Nullable
    private static Class<?> tryClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError e) {
            LOG.warn("AA class lookup failed: {} ({})", name, e.getMessage());
            return null;
        }
    }

    @Nullable
    private static Field tryField(Class<?> declaring, String name) {
        try {
            return declaring.getField(name);
        } catch (NoSuchFieldException | LinkageError e) {
            LOG.warn("AA field lookup failed: {}#{} ({})", declaring.getName(), name, e.getMessage());
            return null;
        }
    }

    @Nullable
    private static Method tryMethod(Class<?> declaring, String name, Class<?>... params) {
        try {
            return declaring.getMethod(name, params);
        } catch (NoSuchMethodException | LinkageError e) {
            LOG.warn("AA method lookup failed: {}#{} ({})", declaring.getName(), name, e.getMessage());
            return null;
        }
    }
}
