package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.neoforged.neoforge.common.crafting.DifferenceIngredient;

/** Small bridge for the recipe manager and recipe preview APIs removed in 26.1. */
public final class RecipeCompat26 {
    // 26.1 removed Ingredient.EMPTY and rejects air/empty holder sets. This
    // in-memory marker has no accepted items, so isEmpty() correctly marks a
    // shaped recipe's vacant cell without ever entering recipe serialization.
    private static final Ingredient EMPTY_SLOT = DifferenceIngredient.of(
            Ingredient.of(Items.STONE), Ingredient.of(Items.STONE));

    private RecipeCompat26() {}

    public static List<RecipeHolder<?>> byType(ServerLevel level, RecipeType<?> type) {
        return level.getServer().getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.value().getType() == type).toList();
    }

    public static ItemStack resultItem(Recipe<?> recipe, ServerLevel level) {
        var displayed = recipe.display().stream()
                .map(display -> display.result().resolveForFirstStack(SlotDisplayContext.fromLevel(level)))
                .filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        if (!displayed.isEmpty()) return displayed;
        // Most 26.1 custom recipes leave Recipe.display() at its empty default.
        // Their immutable result template is the only preview available before
        // we have a concrete crafting input to call assemble() with.
        try {
            var field = recipe.getClass().getDeclaredField("result");
            field.setAccessible(true);
            var result = field.get(recipe);
            if (result instanceof ItemStackTemplate template) return template.create();
            if (result instanceof ItemStack stack) return stack.copy();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // An adapter may still derive a result from a concrete input later.
        }
        return ItemStack.EMPTY;
    }

    /** Reconstruct the shaped slot positions from the new placement map. */
    public static NonNullList<Ingredient> ingredients(Recipe<?> recipe) {
        PlacementInfo placement = recipe.placementInfo();
        if (placement.isImpossibleToPlace()) {
            // Extended Crafting table recipes deliberately report NOT_PLACEABLE
            // while exposing their positioned ingredients through their own API.
            for (String methodName : List.of("getPositionedIngredients", "getIngredients")) {
                try {
                    var value = recipe.getClass().getMethod(methodName).invoke(recipe);
                    if (!(value instanceof List<?> source) || source.isEmpty()) continue;
                    var result = NonNullList.withSize(source.size(), EMPTY_SLOT);
                    for (int i = 0; i < source.size(); i++) {
                        var entry = source.get(i);
                        if (entry instanceof Optional<?> optional) entry = optional.orElse(null);
                        if (entry == null) continue;
                        if (!(entry instanceof Ingredient ingredient)) return NonNullList.create();
                        result.set(i, ingredient);
                    }
                    return result;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try the next optional-mod recipe API.
                }
            }
            return NonNullList.create();
        }
        var indices = placement.slotsToIngredientIndex();
        var source = placement.ingredients();
        var result = NonNullList.withSize(indices.size(), EMPTY_SLOT);
        for (int i = 0; i < indices.size(); i++) {
            int index = indices.getInt(i);
            if (index != PlacementInfo.EMPTY_SLOT) result.set(i, source.get(index));
        }
        return result;
    }

    public static ItemStack[] items(Ingredient ingredient) {
        return ingredient.items().map(holder -> new ItemStack(holder.value())).toArray(ItemStack[]::new);
    }
}
