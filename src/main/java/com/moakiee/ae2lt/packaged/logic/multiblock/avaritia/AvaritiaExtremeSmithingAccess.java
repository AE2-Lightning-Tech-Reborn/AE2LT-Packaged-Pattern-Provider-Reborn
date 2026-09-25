package com.moakiee.ae2lt.packaged.logic.multiblock.avaritia;

import java.lang.reflect.Field;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

import com.moakiee.ae2lt.packaged.logic.multiblock.avaritia.AvaritiaExtremeSmithingAdapter.SmithingMatch;

/** Lazy optional-mod API access, using the native Forge 1.20.1 contract. */
final class AvaritiaExtremeSmithingAccess {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(AvaritiaExtremeSmithingAccess.class);
    private static final String RECIPE_CLASS =
            "committee.nova.mods.avaritia.common.crafting.recipe.ExtremeSmithingRecipe";

    private static final ReflectionBootstrap LOOKUP = new ReflectionBootstrap("avaritia-extreme-smithing");
    private static volatile @Nullable Field templateField;
    private static volatile @Nullable Field baseField;
    private static volatile @Nullable Field additionsField;

    /**
     * 1.20.1 has no {@code ExtremeSmithingRecipeInput} (a 1.20.5+ class);
     * the recipe reads slots 0=template, 1=base, 2..4=additions from any
     * {@code Container}, so a plain {@link SimpleContainer} works.
     */
    static Container createInput(SmithingMatch match) {
        var container = new SimpleContainer(5);
        container.setItem(0, match.template().copy());
        container.setItem(1, match.base().copy());
        container.setItem(2, match.additions().get(0).copy());
        container.setItem(3, match.additions().get(1).copy());
        container.setItem(4, match.additions().get(2).copy());
        return container;
    }

    static boolean matchesTemplate(Object recipe, ItemStack stack) {
        ensureLookup();
        return fieldIngredientTest(templateField, recipe, stack);
    }

    static boolean matchesBase(Object recipe, ItemStack stack) {
        ensureLookup();
        return fieldIngredientTest(baseField, recipe, stack);
    }

    static boolean matchesAddition(Object recipe, ItemStack stack) {
        ensureLookup();
        return fieldIngredientTest(additionsField, recipe, stack);
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
        if (field == null || !field.getDeclaringClass().isInstance(recipe)) {
            return null;
        }
        try {
            var value = field.get(recipe);
            return value instanceof Ingredient ingredient ? ingredient : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * Ingredient.test against the recipe's public {@code template}/{@code
     * base}/{@code additions} field. The isXxxIngredient methods cannot be
     * used here: they override Forge's net.minecraft-hosted SmithingRecipe
     * interface, whose members are part of the SRG mapping, so a
     * production (reobfuscated) jar renames the implementations and
     * reflective lookups by the mojmap name fail (observed as a
     * NoSuchMethodException at runtime). The fields are Avaritia-own
     * members and keep their names; the Avaritia overrides are exactly
     * {@code field.test(stack)}, so testing the field directly preserves
     * the semantics.
     */
    private static boolean fieldIngredientTest(@Nullable Field field, Object recipe, ItemStack stack) {
        var ingredient = readIngredient(field, recipe);
        if (ingredient == null) {
            return false;
        }
        try {
            return ingredient.test(stack);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean ingredientAcceptsItemId(@Nullable Ingredient ingredient, Item item) {
        if (ingredient == null) {
            return false;
        }
        try {
            for (var candidate : ingredient.getItems()) {
                if (candidate.getItem() == item) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return false;
    }

    private static void ensureLookup() {
        LOOKUP.run(AvaritiaExtremeSmithingAccess::doLookup);
    }

    private static void doLookup() {
        try {
            var recipeClass = Class.forName(RECIPE_CLASS);
            templateField = recipeClass.getField("template");
            baseField = recipeClass.getField("base");
            additionsField = recipeClass.getField("additions");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("[ae2ltpp] Avaritia ExtremeSmithingRecipe member lookup failed: {}", e.toString());
        }
    }
}
