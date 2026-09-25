package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

/** Lazy optional-mod API access, using the native Forge 1.20.1 contract. */
final class ArsNouveauImbuementChamberAccess {
    private static final String IMBUEMENT_TILE_CLASS =
            "com.hollingsworth.arsnouveau.common.block.tile.ImbuementTile";
    private static final String ARCANE_PEDESTAL_TILE_CLASS =
            "com.hollingsworth.arsnouveau.common.block.tile.ArcanePedestalTile";
    private static final String IMBUEMENT_REGISTRY_CLASS =
            "com.hollingsworth.arsnouveau.api.registry.ImbuementRecipeRegistry";

    private static final ReflectionBootstrap LOOKUP = new ReflectionBootstrap("ars-imbuement");
    private static volatile @Nullable Class<?> imbuementTileClass;
    private static volatile @Nullable Class<?> arcanePedestalTileClass;
    private static volatile @Nullable Field registryInstanceField;
    private static volatile @Nullable Method getRecipesMethod;
    private static volatile @Nullable Method getInputMethod;
    private static volatile @Nullable Method getPedestalItemsMethod;
    // 1.20.x exposes these as public fields instead of getter methods.
    private static volatile @Nullable Field inputField;
    private static volatile @Nullable Field pedestalItemsField;
    private static volatile @Nullable Field outputField;

    static boolean isImbuementTile(Object value) {
        ensureLookup();
        return imbuementTileClass != null && imbuementTileClass.isInstance(value);
    }

    static boolean isArcanePedestal(Object value) {
        ensureLookup();
        return arcanePedestalTileClass != null && arcanePedestalTileClass.isInstance(value);
    }

    static List<Recipe<?>> getImbuementRecipes() {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ars_nouveau")) {
            return List.of();
        }
        ensureLookup();
        if (registryInstanceField == null || getRecipesMethod == null) {
            return List.of();
        }
        try {
            Object registry = registryInstanceField.get(null);
            Object value = getRecipesMethod.invoke(registry);
            if (!(value instanceof Iterable<?> iterable)) {
                return List.of();
            }
            var recipes = new ArrayList<Recipe<?>>();
            for (var item : iterable) {
                if (item instanceof Recipe<?> holder) {
                    recipes.add(holder);
                }
            }
            return List.copyOf(recipes);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    /** Output stack; vanilla {@code getResultItem} is overridden empty on 1.20.x. */
    @Nullable
    static ItemStack output(Object recipe) {
        ensureLookup();
        var field = outputField;
        if (field == null) {
            return null;
        }
        try {
            return field.get(recipe) instanceof ItemStack stack ? stack.copy() : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    static Ingredient getInput(Object recipe) {
        ensureLookup();
        if (getInputMethod != null) {
            try {
                var value = getInputMethod.invoke(recipe);
                if (value instanceof Ingredient ingredient) {
                    return ingredient;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // fall through to the field fallback
            }
        }
        var field = inputField;
        if (field == null) {
            return null;
        }
        try {
            return field.get(recipe) instanceof Ingredient ingredient ? ingredient : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static List<Ingredient> getPedestalItems(Object recipe) {
        ensureLookup();
        if (getPedestalItemsMethod != null) {
            try {
                var value = getPedestalItemsMethod.invoke(recipe);
                if (value instanceof List<?> list) {
                    var result = new ArrayList<Ingredient>(list.size());
                    for (var item : list) {
                        if (!(item instanceof Ingredient ingredient)) {
                            return null;
                        }
                        result.add(ingredient);
                    }
                    return List.copyOf(result);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // fall through to the field fallback
            }
        }
        var field = pedestalItemsField;
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(recipe);
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
        imbuementTileClass = Class.forName(IMBUEMENT_TILE_CLASS);
        arcanePedestalTileClass = Class.forName(ARCANE_PEDESTAL_TILE_CLASS);

        // Steps below resolve independently so a missing member on one mod
        // version cannot disable the remaining lookups.
        try {
            var registryClass = Class.forName(IMBUEMENT_REGISTRY_CLASS);
            registryInstanceField = registryClass.getField("INSTANCE");
            getRecipesMethod = registryClass.getMethod("getRecipes");
        } catch (ReflectiveOperationException ignored) {
            registryInstanceField = null;
            getRecipesMethod = null;
        }

        try {
            var recipeClass = Class.forName(
                    "com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe");
            getInputMethod = recipeClass.getMethod("getInput");
        } catch (ReflectiveOperationException ignored) {
            getInputMethod = null;
        }
        try {
            var recipeClass = Class.forName(
                    "com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe");
            getPedestalItemsMethod = recipeClass.getMethod("getPedestalItems");
        } catch (ReflectiveOperationException ignored) {
            getPedestalItemsMethod = null;
        }

        try {
            var recipeClass = Class.forName(
                    "com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe");
            inputField = recipeClass.getField("input");
            pedestalItemsField = recipeClass.getField("pedestalItems");
            outputField = recipeClass.getField("output");
        } catch (ReflectiveOperationException ignored) {
            inputField = null;
            pedestalItemsField = null;
            outputField = null;
        }
    }
}
