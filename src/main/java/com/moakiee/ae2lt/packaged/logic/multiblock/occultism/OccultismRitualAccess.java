package com.moakiee.ae2lt.packaged.logic.multiblock.occultism;

import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Lazy optional-mod API access, separate from recipe planning and dispatch. */
final class OccultismRitualAccess {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(OccultismRitualAccess.class);
    private static final String GOLDEN_BOWL_CLASS =
            "com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity";
    private static final String SACRIFICIAL_BOWL_CLASS =
            "com.klikli_dev.occultism.common.blockentity.SacrificialBowlBlockEntity";
    private static final String RITUAL_RECIPE_CLASS =
            "com.klikli_dev.occultism.crafting.recipe.RitualRecipe";
    private static final String RITUAL_CLASS =
            "com.klikli_dev.occultism.common.ritual.Ritual";

    private static volatile boolean lookupDone;
    private static volatile @Nullable Class<?> goldenBowlClass;
    private static volatile @Nullable Class<?> sacrificialBowlClass;
    private static volatile @Nullable Method getCurrentRitualRecipeMethod;
    private static volatile @Nullable Field itemStackHandlerField;
    private static volatile @Nullable Method getActivationItemMethod;
    private static volatile @Nullable Method getRitualTypeMethod;
    private static volatile @Nullable Method requiresSacrificeMethod;
    private static volatile @Nullable Method requiresItemUseMethod;
    private static volatile @Nullable Method getItemToUseMethod;
    private static volatile @Nullable Method getPentacleMethod;
    private static volatile @Nullable Method getRitualMethod;
    private static volatile @Nullable Method getSacrificialBowlsMethod;
    private static volatile @Nullable Field sacrificeProvidedField;
    private static volatile @Nullable Field itemUseProvidedField;

    static boolean isGoldenBowl(Object object) {
        ensureLookup();
        return goldenBowlClass != null && goldenBowlClass.isInstance(object);
    }

    static boolean isSacrificialBowl(Object object) {
        ensureLookup();
        return sacrificialBowlClass != null && sacrificialBowlClass.isInstance(object);
    }

    static boolean isIdle(Object goldenBowl) {
        ensureLookup();
        if (getCurrentRitualRecipeMethod == null) {
            return false;
        }
        try {
            return getCurrentRitualRecipeMethod.invoke(goldenBowl) == null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "isIdle", ignored);
            return false;
        }
    }

    static boolean requiresSacrifice(Object recipe) {
        ensureLookup();
        if (requiresSacrificeMethod == null) {
            return false;
        }
        try {
            var value = requiresSacrificeMethod.invoke(recipe);
            return value instanceof Boolean b && b;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "requiresSacrifice", ignored);
            return false;
        }
    }

    static boolean requiresItemUse(Object recipe) {
        ensureLookup();
        if (requiresItemUseMethod == null) {
            return false;
        }
        try {
            var value = requiresItemUseMethod.invoke(recipe);
            return value instanceof Boolean b && b;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "requiresItemUse", ignored);
            return false;
        }
    }

    @Nullable
    static Ingredient getItemToUse(Object recipe) {
        ensureLookup();
        if (getItemToUseMethod == null) {
            return null;
        }
        try {
            var value = getItemToUseMethod.invoke(recipe);
            return value instanceof Ingredient ingredient && !ingredient.isEmpty() ? ingredient : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "getItemToUse", ignored);
            return null;
        }
    }

    static void completeDeferredRequirements(Object goldenBowl, boolean sacrifice, boolean itemUse) {
        ensureLookup();
        try {
            if (sacrifice) {
                if (sacrificeProvidedField != null) {
                    sacrificeProvidedField.setBoolean(goldenBowl, true);
                    LOG.debug("completeDeferredRequirements: sacrificeProvided=true on {}", goldenBowl);
                } else {
                    LOG.warn("completeDeferredRequirements: sacrificeProvidedField is null; "
                            + "ritual will stall on sacrifice gate");
                }
            }
            if (itemUse) {
                if (itemUseProvidedField != null) {
                    itemUseProvidedField.setBoolean(goldenBowl, true);
                    LOG.debug("completeDeferredRequirements: itemUseProvided=true on {}", goldenBowl);
                } else {
                    LOG.warn("completeDeferredRequirements: itemUseProvidedField is null; "
                            + "ritual will stall on item-use gate");
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("completeDeferredRequirements failed (sacrifice={}, itemUse={}): {}",
                    sacrifice, itemUse, e.toString());
        }
    }

    @Nullable
    static IItemHandler itemHandler(Object bowl) {
        ensureLookup();
        if (itemStackHandlerField == null) {
            return null;
        }
        try {
            var value = itemStackHandlerField.get(bowl);
            return com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess.wrap(value);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "itemHandler", ignored);
            return null;
        }
    }

    @Nullable
    static Ingredient getActivationItem(Object recipe) {
        ensureLookup();
        if (getActivationItemMethod == null) {
            return null;
        }
        try {
            var value = getActivationItemMethod.invoke(recipe);
            return value instanceof Ingredient ingredient ? ingredient : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "getActivationItem", ignored);
            return null;
        }
    }

    @Nullable
    static List<Ingredient> getIngredients(Object recipe) {
        if (!(recipe instanceof net.minecraft.world.item.crafting.Recipe<?> typed)) {
            return null;
        }
        try {
            Object value = com.moakiee.ae2lt.packaged.logic.multiblock.shared.RecipeCompat26.ingredients(typed);
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
            return List.copyOf(result);
        } catch (RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "getIngredients", ignored);
            return null;
        }
    }

    @Nullable
    static Identifier getRitualType(Object recipe) {
        ensureLookup();
        if (getRitualTypeMethod == null) {
            return null;
        }
        try {
            var value = getRitualTypeMethod.invoke(recipe);
            return value instanceof Identifier id ? id : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "getRitualType", ignored);
            return null;
        }
    }

    static boolean hasValidPentacle(Object recipe, Level level, BlockPos pos) {
        var pentacle = getPentacle(recipe);
    if (pentacle == null) {
        return false;
    }
    try {
        var validate = ReflectionSupport.findMethodCached(pentacle.getClass(), "validate", Level.class, BlockPos.class)
                .orElse(null);
        if (validate == null) {
            return false;
        }
        return validate.invoke(pentacle, level, pos) != null;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "hasValidPentacle", ignored);
        return false;
    }
}

    @Nullable
    static List<?> getSacrificialBowls(Object recipe, Level level, BlockPos pos) {
        ensureLookup();
        if (getRitualMethod == null || getSacrificialBowlsMethod == null) {
            return null;
        }
        try {
            var ritual = getRitualMethod.invoke(recipe);
            var value = getSacrificialBowlsMethod.invoke(ritual, level, pos);
            return value instanceof List<?> list ? list : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "getSacrificialBowls", ignored);
            return null;
        }
    }

    @Nullable
    private static Object getPentacle(Object recipe) {
        ensureLookup();
        if (getPentacleMethod == null) {
            return null;
        }
        try {
            return getPentacleMethod.invoke(recipe);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "getPentacle", ignored);
            return null;
        }
    }

    private static void ensureLookup() {
        if (lookupDone) {
            return;
        }
        synchronized (OccultismRitualAccess.class) {
            if (lookupDone) {
                return;
            }
            doLookup();
            lookupDone = true;
        }
    }

    private static void doLookup() {
        goldenBowlClass = tryClass(GOLDEN_BOWL_CLASS);
        sacrificialBowlClass = tryClass(SACRIFICIAL_BOWL_CLASS);
        var ritualRecipeClass = tryClass(RITUAL_RECIPE_CLASS);
        var ritualClass = tryClass(RITUAL_CLASS);

        if (goldenBowlClass != null) {
            getCurrentRitualRecipeMethod = tryMethod(goldenBowlClass, "getCurrentRitualRecipe");
            sacrificeProvidedField = tryField(goldenBowlClass, "sacrificeProvided");
            itemUseProvidedField = tryField(goldenBowlClass, "itemUseProvided");
        }
        if (sacrificialBowlClass != null) {
            itemStackHandlerField = tryField(sacrificialBowlClass, "itemStackHandler");
        }
        if (ritualRecipeClass != null) {
            getActivationItemMethod = tryMethod(ritualRecipeClass, "getActivationItem");
            getRitualTypeMethod = tryMethod(ritualRecipeClass, "getRitualType");
            requiresSacrificeMethod = tryMethod(ritualRecipeClass, "requiresSacrifice");
            requiresItemUseMethod = tryMethod(ritualRecipeClass, "requiresItemUse");
            getItemToUseMethod = tryMethod(ritualRecipeClass, "getItemToUse");
            getPentacleMethod = tryMethod(ritualRecipeClass, "getPentacle");
            getRitualMethod = tryMethod(ritualRecipeClass, "getRitual");
        }
        if (ritualClass != null) {
            getSacrificialBowlsMethod = tryMethod(ritualClass, "getSacrificialBowls",
                    Level.class, BlockPos.class);
        }
    }

    @Nullable
    private static Class<?> tryClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "tryClass", ignored);
            return null;
        }
    }

    @Nullable
    private static Method tryMethod(Class<?> declaring, String name, Class<?>... params) {
        try {
            return declaring.getMethod(name, params);
        } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "tryMethod", ignored);
            return null;
        }
    }

    @Nullable
    private static Field tryField(Class<?> declaring, String name) {
        try {
            return declaring.getField(name);
        } catch (NoSuchFieldException | SecurityException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(OccultismRitualAccess.class, "tryField", ignored);
            return null;
        }
    }
}
