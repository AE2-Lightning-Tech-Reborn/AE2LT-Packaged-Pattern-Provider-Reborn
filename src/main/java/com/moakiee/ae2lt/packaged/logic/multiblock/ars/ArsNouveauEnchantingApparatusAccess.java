package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

/** Lazy optional-mod API access, using the native Forge 1.20.1 contract. */
final class ArsNouveauEnchantingApparatusAccess {
    private static final String API_CLASS = "com.hollingsworth.arsnouveau.api.ArsNouveauAPI";
    private static final String SOURCE_UTIL_CLASS = "com.hollingsworth.arsnouveau.api.util.SourceUtil";

    private static final Map<Class<?>, Map<String, Method>> INSTANCE_METHODS = new HashMap<>();

    private static volatile @Nullable Method getInstanceMethod;
    private static volatile @Nullable Method getRecipesMethod;
    private static final ReflectionBootstrap API_LOOKUP = new ReflectionBootstrap("ars-apparatus-api");
    private static volatile @Nullable Method hasSourceNearbyMethod;
    private static final ReflectionBootstrap SOURCE_LOOKUP = new ReflectionBootstrap("ars-apparatus-source");

    /**
     * Result of an {@code IEnchantingRecipe}. On 1.20.x the apparatus
     * recipes return {@link Recipe#getResultItem(RegistryAccess)}
     * unconditionally empty, so the interface's own
     * {@code getResult(pedestalItems, reagent, tile)} hook must be used.
     */
    @Nullable
    static ItemStack recipeResult(Object recipe) {
        var method = instanceMethod(recipe.getClass(), "getResult");
        if (method == null) {
            return null;
        }
        try {
            Object out = method.invoke(recipe, List.of(), ItemStack.EMPTY, null);
            return out instanceof ItemStack stack ? stack.copy() : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /** Apparatus match against explicit pedestal stacks plus the reagent stack. */
    static boolean matchesApparatusRecipe(Object recipe, List<ItemStack> pedestals, ItemStack reagent) {
        var method = instanceMethod(recipe.getClass(), "isMatch");
        if (method == null) {
            return false;
        }
        try {
            Object out = method.invoke(recipe, copyStacks(pedestals), reagent.copy(), null, null);
            return Boolean.TRUE.equals(out);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * Finds a zero-cost or low-shape hook by name: argument types moved
     * between mod versions ({@code getSourceCost}/{@code sourceCost},
     * varying trailing tile/player parameters), so resolution matches on
     * declared parameter kinds instead of exact signatures.
     */
    @Nullable
    private static synchronized Method instanceMethod(Class<?> recipeClass, String name) {
        var perClass = INSTANCE_METHODS.get(recipeClass);
        if (perClass != null && perClass.containsKey(name)) {
            return perClass.get(name);
        }
        Method found = null;
        for (var candidate : recipeClass.getMethods()) {
            if (!candidate.getName().equals(name)) {
                continue;
            }
            var params = candidate.getParameterTypes();
            if (name.equals("isMatch") && params.length == 4
                    && List.class.isAssignableFrom(params[0])
                    && ItemStack.class.isAssignableFrom(params[1])) {
                found = candidate;
                break;
            }
            if (name.equals("getResult") && params.length == 3
                    && List.class.isAssignableFrom(params[0])
                    && ItemStack.class.isAssignableFrom(params[1])) {
                found = candidate;
                break;
            }
        }
        INSTANCE_METHODS.computeIfAbsent(recipeClass, k -> new HashMap<>()).put(name, found);
        return found;
    }

    static List<Recipe<?>> getEnchantingApparatusRecipes(ServerLevel level) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ars_nouveau")) {
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

            var recipes = new ArrayList<Recipe<?>>();
            for (var item : iterable) {
                if (item instanceof Recipe<?> holder) {
                    recipes.add(holder);
                }
            }
            return recipes;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    static boolean hasSourceNearby(BlockPos pos, Level level, int radius, int cost) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ars_nouveau")) {
            return false;
        }
        try {
            var method = hasSourceNearbyMethod();
            return method != null && Boolean.TRUE.equals(method.invoke(null, pos, level, radius, cost));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
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
        API_LOOKUP.run(() -> {
            try {
                var clazz = Class.forName(API_CLASS);
                getInstanceMethod = clazz.getMethod("getInstance");
                getRecipesMethod = clazz.getMethod("getEnchantingApparatusRecipes", Level.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                getInstanceMethod = null;
                getRecipesMethod = null;
            }
        });
    }

    @Nullable
    private static Method hasSourceNearbyMethod() {
        SOURCE_LOOKUP.run(() -> {
            try {
                var clazz = Class.forName(SOURCE_UTIL_CLASS);
                hasSourceNearbyMethod = clazz.getMethod(
                        "hasSourceNearby", BlockPos.class, Level.class, int.class, int.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                hasSourceNearbyMethod = null;
            }
        });
        return hasSourceNearbyMethod;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }
}
