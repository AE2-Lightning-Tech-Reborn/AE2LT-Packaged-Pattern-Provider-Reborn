package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Lazy optional-mod API access, separate from recipe planning and dispatch. */
final class ArsNouveauEnchantingApparatusAccess {
    private static final String APPARATUS_INPUT_CLASS =
            "com.hollingsworth.arsnouveau.common.crafting.recipes.ApparatusRecipeInput";
    private static final String API_CLASS = "com.hollingsworth.arsnouveau.api.ArsNouveauAPI";
    private static final String SOURCE_UTIL_CLASS = "com.hollingsworth.arsnouveau.api.util.SourceUtil";

    private static volatile @Nullable Constructor<?> apparatusInputConstructor;
    private static volatile boolean apparatusInputLookupDone;
    private static volatile @Nullable Method getInstanceMethod;
    private static volatile @Nullable Method getRecipesMethod;
    private static volatile boolean apiLookupDone;
    private static volatile @Nullable Method hasSourceNearbyMethod;
    private static volatile boolean sourceLookupDone;

    @Nullable
    static RecipeInput createApparatusInput(ItemStack reagent, List<ItemStack> pedestals) {
        var constructor = apparatusInputConstructor();
        if (constructor == null) {
            return null;
        }
        try {
            Object input = constructor.newInstance(reagent.copy(), copyStacks(pedestals), null);
            return input instanceof RecipeInput recipeInput ? recipeInput : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ArsNouveauEnchantingApparatusAccess.class, "createApparatusInput", ignored);
            return null;
        }
    }

    static List<RecipeHolder<?>> getEnchantingApparatusRecipes(ServerLevel level) {
        if (!net.neoforged.fml.ModList.get().isLoaded("ars_nouveau")) {
            return List.of();
        }
        try {
            var getInstance = getInstanceMethod();
            var getRecipes = getRecipesMethod();
            if (getInstance == null || getRecipes == null) {
                return List.of();
            }
            Object api = getInstance.invoke(null);
            Object value = getRecipes.invoke(api, level);
            if (!(value instanceof Iterable<?> iterable)) {
                return List.of();
            }

            var recipes = new ArrayList<RecipeHolder<?>>();
            for (var item : iterable) {
                if (item instanceof RecipeHolder<?> holder && holder.value() instanceof Recipe<?>) {
                    recipes.add(holder);
                }
            }
            return recipes;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ArsNouveauEnchantingApparatusAccess.class, "getEnchantingApparatusRecipes", ignored);
            return List.of();
        }
    }

    static boolean hasSourceNearby(BlockPos pos, Level level, int radius, int cost) {
        if (!net.neoforged.fml.ModList.get().isLoaded("ars_nouveau")) {
            return false;
        }
        try {
            var method = hasSourceNearbyMethod();
            return method != null && Boolean.TRUE.equals(method.invoke(null, pos, level, radius, cost));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ArsNouveauEnchantingApparatusAccess.class, "hasSourceNearby", ignored);
            return false;
        }
    }

    @Nullable
    private static Constructor<?> apparatusInputConstructor() {
        if (apparatusInputLookupDone) {
            return apparatusInputConstructor;
        }
        synchronized (ArsNouveauEnchantingApparatusAccess.class) {
            if (apparatusInputLookupDone) {
                return apparatusInputConstructor;
            }
            try {
                var clazz = Class.forName(APPARATUS_INPUT_CLASS);
                apparatusInputConstructor = clazz.getConstructor(ItemStack.class, List.class, Player.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                CompatibilityDiagnostics.failure(ArsNouveauEnchantingApparatusAccess.class, "apparatusInputConstructor", ignored);
                apparatusInputConstructor = null;
            } finally {
                apparatusInputLookupDone = true;
            }
            return apparatusInputConstructor;
        }
    }

    @Nullable
    private static Method getInstanceMethod() {
        ensureApiMethods();
        return getInstanceMethod;
    }

    @Nullable
    private static Method getRecipesMethod() {
        ensureApiMethods();
        return getRecipesMethod;
    }

    private static void ensureApiMethods() {
        if (apiLookupDone) {
            return;
        }
        synchronized (ArsNouveauEnchantingApparatusAccess.class) {
            if (apiLookupDone) {
                return;
            }
            try {
                var clazz = Class.forName(API_CLASS);
                getInstanceMethod = clazz.getMethod("getInstance");
                getRecipesMethod = clazz.getMethod("getEnchantingApparatusRecipes", Level.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                CompatibilityDiagnostics.failure(ArsNouveauEnchantingApparatusAccess.class, "ensureApiMethods", ignored);
                getInstanceMethod = null;
                getRecipesMethod = null;
            } finally {
                apiLookupDone = true;
            }
        }
    }

    @Nullable
    private static Method hasSourceNearbyMethod() {
        if (sourceLookupDone) {
            return hasSourceNearbyMethod;
        }
        synchronized (ArsNouveauEnchantingApparatusAccess.class) {
            if (sourceLookupDone) {
                return hasSourceNearbyMethod;
            }
            try {
                var clazz = Class.forName(SOURCE_UTIL_CLASS);
                hasSourceNearbyMethod = clazz.getMethod(
                        "hasSourceNearby", BlockPos.class, Level.class, int.class, int.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                CompatibilityDiagnostics.failure(ArsNouveauEnchantingApparatusAccess.class, "hasSourceNearbyMethod", ignored);
                hasSourceNearbyMethod = null;
            } finally {
                sourceLookupDone = true;
            }
            return hasSourceNearbyMethod;
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }
}
