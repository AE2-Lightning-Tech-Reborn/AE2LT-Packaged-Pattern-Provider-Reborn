package com.moakiee.ae2lt.packaged.logic.multiblock.occultism;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

/** Lazy optional-mod API access, using the native Forge 1.20.1 contract. */
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

    private static final ReflectionBootstrap LOOKUP = new ReflectionBootstrap("occultism-ritual");
    private static volatile @Nullable Class<?> goldenBowlClass;
    private static volatile @Nullable Class<?> sacrificialBowlClass;
    private static volatile @Nullable Method getCurrentRitualRecipeMethod;
    private static volatile @Nullable Field itemStackHandlerField;
    private static volatile @Nullable Method getActivationItemMethod;
    private static volatile @Nullable Method getRitualTypeMethod;
    private static volatile @Nullable Method requiresSacrificeMethod;
    private static volatile @Nullable Method getEntityToSacrificeMethod;
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
            return false;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static TagKey<EntityType<?>> getEntityToSacrifice(Object recipe) {
        ensureLookup();
        if (getEntityToSacrificeMethod == null) {
            return null;
        }
        try {
            var value = getEntityToSacrificeMethod.invoke(recipe);
            return value instanceof TagKey<?> tag
                    ? (TagKey<EntityType<?>>) tag
                    : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
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
            return null;
        }
    }

    static boolean canCompleteDeferredRequirements(Object goldenBowl,
                                                   boolean sacrifice, boolean itemUse) {
        ensureLookup();
        return goldenBowl != null
                && (!sacrifice || sacrificeProvidedField != null)
                && (!itemUse || itemUseProvidedField != null);
    }

    static boolean completeDeferredRequirements(Object goldenBowl, boolean sacrifice, boolean itemUse) {
        if (!canCompleteDeferredRequirements(goldenBowl, sacrifice, itemUse)) {
            return false;
        }
        try {
            if (sacrifice) {
                sacrificeProvidedField.setBoolean(goldenBowl, true);
            }
            if (itemUse) {
                itemUseProvidedField.setBoolean(goldenBowl, true);
            }
            return deferredRequirementsComplete(goldenBowl, sacrifice, itemUse);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("completeDeferredRequirements failed (sacrifice={}, itemUse={}): {}",
                    sacrifice, itemUse, e.toString());
            return false;
        }
    }

    static boolean deferredRequirementsComplete(Object goldenBowl, boolean sacrifice, boolean itemUse) {
        ensureLookup();
        try {
            return (!sacrifice || sacrificeProvidedField != null
                    && sacrificeProvidedField.getBoolean(goldenBowl))
                    && (!itemUse || itemUseProvidedField != null
                    && itemUseProvidedField.getBoolean(goldenBowl));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
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
            return value instanceof IItemHandler handler ? handler : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
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
            return null;
        }
    }

    @Nullable
    static List<Ingredient> getIngredients(Object recipe) {
        // Direct vanilla-interface dispatch instead of reflection:
        // getIngredients() overrides a net.minecraft method, so in a
        // production (reobfuscated) jar the implementing method is
        // renamed to its SRG name and lookups by the mojmap name fail
        // even though the method exists. Calling through the erased
        // Recipe interface is compile-time-reobfuscated on our side and
        // therefore always resolves.
        if (!(recipe instanceof Recipe<?> vanillaRecipe)) {
            return null;
        }
        try {
            var value = vanillaRecipe.getIngredients();
            var result = new ArrayList<Ingredient>(value.size());
            for (Ingredient ingredient : value) {
                result.add(ingredient);
            }
            return List.copyOf(result);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    static ResourceLocation getRitualType(Object recipe) {
        ensureLookup();
        if (getRitualTypeMethod == null) {
            return null;
        }
        try {
            var value = getRitualTypeMethod.invoke(recipe);
            return value instanceof ResourceLocation id ? id : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
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
            return null;
        }
    }

    private static void ensureLookup() {
        LOOKUP.run(OccultismRitualAccess::doLookup);
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
            getEntityToSacrificeMethod = tryMethod(ritualRecipeClass, "getEntityToSacrifice");
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
            return null;
        }
    }

    @Nullable
    private static Method tryMethod(Class<?> declaring, String name, Class<?>... params) {
        try {
            return declaring.getMethod(name, params);
        } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static Field tryField(Class<?> declaring, String name) {
        for (Class<?> type = declaring; type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Continue into the superclass: Occultism moved these
                // ritual-state fields between versions.
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }
        return null;
    }
}
