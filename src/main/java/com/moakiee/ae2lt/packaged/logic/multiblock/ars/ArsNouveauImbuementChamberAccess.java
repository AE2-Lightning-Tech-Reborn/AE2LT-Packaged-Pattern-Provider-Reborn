package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

/** Lazy optional-mod API access, separate from recipe planning and dispatch. */
final class ArsNouveauImbuementChamberAccess {
    private static final String IMBUEMENT_TILE_CLASS =
            "com.hollingsworth.arsnouveau.common.block.tile.ImbuementTile";
    private static final String ARCANE_PEDESTAL_TILE_CLASS =
            "com.hollingsworth.arsnouveau.common.block.tile.ArcanePedestalTile";
    private static final String IMBUEMENT_REGISTRY_CLASS =
            "com.hollingsworth.arsnouveau.api.registry.ImbuementRecipeRegistry";

    private static volatile boolean lookupDone;
    private static volatile @Nullable Class<?> imbuementTileClass;
    private static volatile @Nullable Class<?> arcanePedestalTileClass;
    private static volatile @Nullable Field registryInstanceField;
    private static volatile @Nullable Method getRecipesMethod;
    private static volatile @Nullable Method getInputMethod;
    private static volatile @Nullable Method getPedestalItemsMethod;

    static boolean isImbuementTile(Object value) {
        ensureLookup();
        return imbuementTileClass != null && imbuementTileClass.isInstance(value);
    }

    static boolean isArcanePedestal(Object value) {
        ensureLookup();
        return arcanePedestalTileClass != null && arcanePedestalTileClass.isInstance(value);
    }

    static List<RecipeHolder<?>> getImbuementRecipes() {
        if (!net.neoforged.fml.ModList.get().isLoaded("ars_nouveau")) {
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
            var recipes = new ArrayList<RecipeHolder<?>>();
            for (var item : iterable) {
                if (item instanceof RecipeHolder<?> holder && holder.value() instanceof Recipe<?>) {
                    recipes.add(holder);
                }
            }
            return List.copyOf(recipes);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ArsNouveauImbuementChamberAccess.class, "getImbuementRecipes", ignored);
            return List.of();
        }
    }

    @Nullable
    static Ingredient getInput(Object recipe) {
        ensureLookup();
        if (getInputMethod == null) {
            return null;
        }
        try {
            var value = getInputMethod.invoke(recipe);
            return value instanceof Ingredient ingredient ? ingredient : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(ArsNouveauImbuementChamberAccess.class, "getInput", ignored);
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static List<Ingredient> getPedestalItems(Object recipe) {
        ensureLookup();
        if (getPedestalItemsMethod == null) {
            return null;
        }
        try {
            var value = getPedestalItemsMethod.invoke(recipe);
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
            CompatibilityDiagnostics.failure(ArsNouveauImbuementChamberAccess.class, "getPedestalItems", ignored);
            return null;
        }
    }

    private static void ensureLookup() {
        if (lookupDone) {
            return;
        }
        synchronized (ArsNouveauImbuementChamberAccess.class) {
            if (lookupDone) {
                return;
            }
            try {
                doLookup();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                if (!(ignored instanceof ClassNotFoundException) || net.neoforged.fml.ModList.get().isLoaded("ars_nouveau")) {
                    CompatibilityDiagnostics.failure(ArsNouveauImbuementChamberAccess.class, "ensureLookup", ignored);
                }
            } finally {
                lookupDone = true;
            }
        }
    }

    private static void doLookup() throws ReflectiveOperationException {
        imbuementTileClass = Class.forName(IMBUEMENT_TILE_CLASS);
        arcanePedestalTileClass = Class.forName(ARCANE_PEDESTAL_TILE_CLASS);

        var registryClass = Class.forName(IMBUEMENT_REGISTRY_CLASS);
        registryInstanceField = registryClass.getField("INSTANCE");
        getRecipesMethod = registryClass.getMethod("getRecipes");

        try {
            var recipeClass = Class.forName(
                    "com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe");
            getInputMethod = recipeClass.getMethod("getInput");
            getPedestalItemsMethod = recipeClass.getMethod("getPedestalItems");
        } catch (ReflectiveOperationException ignored) {
            if (!(ignored instanceof ClassNotFoundException) || net.neoforged.fml.ModList.get().isLoaded("ars_nouveau")) {
                CompatibilityDiagnostics.failure(ArsNouveauImbuementChamberAccess.class, "doLookup", ignored);
            }
            getInputMethod = null;
            getPedestalItemsMethod = null;
        }
    }
}
