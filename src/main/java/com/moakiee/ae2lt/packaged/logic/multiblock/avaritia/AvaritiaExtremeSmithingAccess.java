package com.moakiee.ae2lt.packaged.logic.multiblock.avaritia;

import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;
import org.jetbrains.annotations.Nullable;

/** Lazy optional-mod API access, separate from recipe planning and dispatch. */
final class AvaritiaExtremeSmithingAccess {
    private static final String RECIPE_CLASS =
            "committee.nova.mods.avaritia.common.crafting.recipe.ExtremeSmithingRecipe";
    private static final String INPUT_CLASS =
            "committee.nova.mods.avaritia.common.crafting.input.ExtremeSmithingRecipeInput";

    private static volatile boolean lookupDone;
    private static volatile @Nullable Constructor<?> inputConstructor;
    private static volatile @Nullable Field templateField;
    private static volatile @Nullable Field baseField;
    private static volatile @Nullable Field additionsField;
    private static volatile @Nullable Method isTemplateIngredientMethod;
    private static volatile @Nullable Method isBaseIngredientMethod;
    private static volatile @Nullable Method isAdditionIngredientMethod;

    @Nullable
    static RecipeInput createInput(ItemStack template, ItemStack base, java.util.List<ItemStack> additions) {
        ensureLookup();
        if (inputConstructor == null) {
            return null;
        }
        try {
            var value = inputConstructor.newInstance(
                    template.copy(),
                    base.copy(),
                    additions.get(0).copy(),
                    additions.get(1).copy(),
                    additions.get(2).copy());
            return value instanceof RecipeInput input ? input : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AvaritiaExtremeSmithingAccess.class, "createInput", ignored);
            return null;
        }
    }

    static boolean matchesTemplate(Object recipe, ItemStack stack) {
        ensureLookup();
        return invokeIngredientMatcher(isTemplateIngredientMethod, recipe, stack);
    }

    static boolean matchesBase(Object recipe, ItemStack stack) {
        ensureLookup();
        return invokeIngredientMatcher(isBaseIngredientMethod, recipe, stack);
    }

    static boolean matchesAddition(Object recipe, ItemStack stack) {
        ensureLookup();
        return invokeIngredientMatcher(isAdditionIngredientMethod, recipe, stack);
    }

    static boolean templateAcceptsItemId(Object recipe, Item item) {
        ensureLookup();
        return ingredientAcceptsItemId(readIngredient(templateField, recipe), item);
    }

    static boolean baseAcceptsItemId(Object recipe, Item item) {
        ensureLookup();
        return ingredientAcceptsItemId(readIngredient(baseField, recipe), item);
    }

    static boolean additionAcceptsItemId(Object recipe, Item item) {
        ensureLookup();
        return ingredientAcceptsItemId(readIngredient(additionsField, recipe), item);
    }

    @Nullable
    private static Ingredient readIngredient(@Nullable Field field, Object recipe) {
        // Callers resolve handles before passing them here; resolving after argument
        // evaluation would leave the first invocation holding a stale null handle.
        if (field == null || !field.getDeclaringClass().isInstance(recipe)) {
            return null;
        }
        try {
            var value = field.get(recipe);
            return value instanceof Ingredient ingredient ? ingredient : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AvaritiaExtremeSmithingAccess.class, "readIngredient", ignored);
            return null;
        }
    }

    private static boolean ingredientAcceptsItemId(@Nullable Ingredient ingredient, Item item) {
        if (ingredient == null) {
            return false;
        }
        try {
            for (var candidate : com.moakiee.ae2lt.packaged.logic.multiblock.shared.RecipeCompat26.items(ingredient)) {
                if (candidate.getItem() == item) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AvaritiaExtremeSmithingAccess.class, "ingredientAcceptsItemId", ignored);
        }
        return false;
    }

    private static boolean invokeIngredientMatcher(@Nullable Method method, Object recipe, ItemStack stack) {
        if (method == null || !method.getDeclaringClass().isInstance(recipe)) {
            return false;
        }
        try {
            var value = method.invoke(recipe, stack);
            return value instanceof Boolean b && b;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(AvaritiaExtremeSmithingAccess.class, "invokeIngredientMatcher", ignored);
            return false;
        }
    }

    private static void ensureLookup() {
        if (lookupDone) {
            return;
        }
        synchronized (AvaritiaExtremeSmithingAccess.class) {
            if (lookupDone) {
                return;
            }
            try {
                doLookup();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                if (!(ignored instanceof ClassNotFoundException) || net.neoforged.fml.ModList.get().isLoaded("avaritia")) {
                    CompatibilityDiagnostics.failure(AvaritiaExtremeSmithingAccess.class, "ensureLookup", ignored);
                }
            } finally {
                lookupDone = true;
            }
        }
    }

    private static void doLookup() throws ReflectiveOperationException {
        var recipeClass = Class.forName(RECIPE_CLASS);
        templateField = recipeClass.getField("template");
        baseField = recipeClass.getField("base");
        additionsField = recipeClass.getField("additions");
        isTemplateIngredientMethod = recipeClass.getMethod("isTemplateIngredient", ItemStack.class);
        isBaseIngredientMethod = recipeClass.getMethod("isBaseIngredient", ItemStack.class);
        isAdditionIngredientMethod = recipeClass.getMethod("isAdditionIngredient", ItemStack.class);

        var inputClass = Class.forName(INPUT_CLASS);
        inputConstructor = inputClass.getConstructor(
                ItemStack.class, ItemStack.class, ItemStack.class, ItemStack.class, ItemStack.class);
    }
}
