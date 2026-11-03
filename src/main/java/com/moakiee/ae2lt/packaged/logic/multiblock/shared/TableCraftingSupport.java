package com.moakiee.ae2lt.packaged.logic.multiblock.shared;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.logic.multiblock.CompatibilityDiagnostics;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/** Shared exact-key matching, recipe calls and grid remainder handling for crafting tables. */
public final class TableCraftingSupport {
    private TableCraftingSupport() {}

    /** Preserve exact component-bearing stacks and empty cells in shaped recipes. */
    @Nullable
    public static List<ItemStack> layoutGrid(List<Ingredient> ingredients, List<ItemStack> perCraftStacks,
                                             int gridSize, @Nullable Integer width) {
        if (gridSize <= 0 || perCraftStacks.size() > gridSize * gridSize) return null;
        var grid = NonNullList.withSize(gridSize * gridSize, ItemStack.EMPTY);
        if (width == null) {
            for (int i = 0; i < perCraftStacks.size(); i++) grid.set(i, perCraftStacks.get(i).copy());
            return grid;
        }
        if (width <= 0 || width > gridSize) return null;
        var cells = new ArrayList<ItemStack>(ingredients.size());
        int next = 0;
        for (var ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                cells.add(null);
            } else {
                if (next >= perCraftStacks.size()) return null;
                cells.add(perCraftStacks.get(next++));
            }
        }
        if (next != perCraftStacks.size()) return null;
        var placements = CraftingGridPlacement.place(width, gridSize, cells);
        if (placements.isEmpty() && !perCraftStacks.isEmpty()) return null;
        for (var placement : placements) grid.set(placement.slot(), placement.value().copy());
        return grid;
    }

    public static List<GenericStack> virtualOutputs(ItemStack result, NonNullList<ItemStack> remaining,
                                                     int craftRatio) {
        var outputs = new ArrayList<GenericStack>();
        outputs.add(new GenericStack(AEItemKey.of(result), (long) craftRatio * result.getCount()));
        for (var stack : remaining) {
            if (!stack.isEmpty()) {
                outputs.add(new GenericStack(AEItemKey.of(stack), (long) craftRatio * stack.getCount()));
            }
        }
        return List.copyOf(outputs);
    }

    public static boolean canAssignIngredientsStrict(NonNullList<Ingredient> ingredients,
                                                      LinkedHashMap<AEItemKey, Long> available,
                                                      long k) {
        return assignIngredientKeys(ingredients, available, k,
                (ingredient, key) -> ingredient.test(key.toStack(1))) != null;
    }

    public static boolean canAssignIngredientsByItemId(NonNullList<Ingredient> ingredients,
                                                        LinkedHashMap<AEItemKey, Long> available,
                                                        long k) {
        return assignIngredientKeys(ingredients, available, k,
                (ingredient, key) -> ingredientAcceptsItemId(ingredient, key.getItem())) != null;
    }

    /**
     * Bind each declared input using its own match mode. Output matching cannot grant an input
     * permission to ignore components. Actual delivery is still checked by the recipe itself.
     */
    public static boolean canAssignPatternIngredients(NonNullList<Ingredient> ingredients,
                                                       IPatternDetails pattern, long craftRatio) {
        var provided = new LinkedHashMap<DeclaredInput, Long>();
        var patternInputs = pattern.getInputs();
        try {
            for (int slot = 0; slot < patternInputs.length; slot++) {
                var input = patternInputs[slot];
                var possible = input.getPossibleInputs();
                if (possible.length == 0 || !(possible[0].what() instanceof AEItemKey key)) return false;
                long amount = Math.multiplyExact(Math.max(1L, possible[0].amount()),
                        Math.max(1L, input.getMultiplier()));
                var declared = new DeclaredInput(key, OverloadPatternSemantics.isIdOnlyInput(pattern, slot));
                provided.merge(declared, amount, Math::addExact);
            }
        } catch (ArithmeticException ignored) {
            return false;
        }
        var nonEmpty = new ArrayList<Ingredient>();
        for (var ingredient : ingredients) if (!ingredient.isEmpty()) nonEmpty.add(ingredient);
        if (nonEmpty.isEmpty()) return false;
        var inputs = new ArrayList<IngredientAssignment.Input<DeclaredInput>>(provided.size());
        provided.forEach((key, amount) -> inputs.add(new IngredientAssignment.Input<>(key, amount)));
        return IngredientAssignment.assign(nonEmpty, inputs, craftRatio, (ingredient, declared) ->
                declared.idOnly() ? ingredientAcceptsItemId(ingredient, declared.key().getItem())
                        : ingredient.test(declared.key().toStack(1))) != null;
    }

    private record DeclaredInput(AEItemKey key, boolean idOnly) {}

    /** A recipe candidate retained until the concrete input components are known. */
    public record BoundRecipe(ResourceLocation id, Object recipe, int craftRatio) {}

    /** Recipe objects can outlive their manager entry after script removal or replacement. */
    public static boolean isCurrentRecipe(ServerLevel level, BoundRecipe candidate) {
        return level.getRecipeManager().byKey(candidate.id())
                .map(holder -> holder.value() == candidate.recipe()).orElse(false);
    }

    @Nullable
    public static List<ItemStack> assignIngredientsStrict(NonNullList<Ingredient> ingredients,
                                                           LinkedHashMap<AEItemKey, Long> available,
                                                           long k) {
        var assignedKeys = assignIngredientKeys(ingredients, available, k,
                (ingredient, key) -> ingredient.test(key.toStack(1)));
        if (assignedKeys == null) {
            return null;
        }

        var stacks = new ArrayList<ItemStack>(assignedKeys.size());
        for (var key : assignedKeys) {
            stacks.add(key.toStack(1));
        }
        return List.copyOf(stacks);
    }

    @Nullable
    private static List<AEItemKey> assignIngredientKeys(NonNullList<Ingredient> ingredients,
                                                        LinkedHashMap<AEItemKey, Long> available,
                                                        long k,
                                                        java.util.function.BiPredicate<Ingredient, AEItemKey> matcher) {
        var nonEmptyIngredients = new ArrayList<Ingredient>();
        for (var ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                nonEmptyIngredients.add(ingredient);
            }
        }
        if (nonEmptyIngredients.isEmpty()) {
            return null;
        }

        var inputs = new ArrayList<IngredientAssignment.Input<AEItemKey>>(available.size());
        for (var entry : available.entrySet()) {
            inputs.add(new IngredientAssignment.Input<>(entry.getKey(), entry.getValue()));
        }
        return IngredientAssignment.assign(nonEmptyIngredients, inputs, k, matcher);
    }

    private static boolean ingredientAcceptsItemId(Ingredient ing, Item item) {
        try {
            for (var candidate : ing.getItems()) {
                if (candidate.getItem() == item) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(TableCraftingSupport.class, "ingredientAcceptsItemId", ignored);
        }
        return false;
    }

    public static int countNonEmpty(NonNullList<Ingredient> ingredients) {
        int count = 0;
        for (var ing : ingredients) {
            if (!ing.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.size() == 1 && outputs.getFirst().what() instanceof AEItemKey;
    }

    public static boolean outputMatchesBatch(IPatternDetails pattern, ItemStack result,
                                               long expectedTotalCount) {
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1) {
            return false;
        }
        var expected = outputs.getFirst();
        if (!(expected.what() instanceof AEItemKey expectedKey)
                || expected.amount() != expectedTotalCount) {
            return false;
        }
        var actual = AEItemKey.of(result);
        return OverloadPatternSemantics.isIdOnlyOutput(pattern, 0)
                ? expectedKey.dropSecondary().equals(actual.dropSecondary())
                : expectedKey.equals(actual);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean recipeMatches(Object recipe, CraftingInput input, ServerLevel level) {
        try {
            return recipe instanceof Recipe<?> r && ((Recipe) r).matches(input, level);
        } catch (RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(TableCraftingSupport.class, "recipeMatches", ignored);
            return false;
        }
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ItemStack assemble(Object recipe, CraftingInput input, ServerLevel level) {
        try {
            return recipe instanceof Recipe<?> r
                    ? ((Recipe) r).assemble(input, level.registryAccess())
                    : null;
        } catch (RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(TableCraftingSupport.class, "assemble", ignored);
            return null;
        }
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static NonNullList<ItemStack> remainingItems(Object recipe, CraftingInput input) {
        try {
            return recipe instanceof Recipe<?> r
                    ? ((Recipe) r).getRemainingItems(input)
                    : null;
        } catch (RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(TableCraftingSupport.class, "remainingItems", ignored);
            return null;
        }
    }

    @Nullable
    public static ItemStack resultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> r)) {
            return null;
        }
        try {
            return r.getResultItem(level.registryAccess());
        } catch (RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(TableCraftingSupport.class, "resultItem", ignored);
            return null;
        }
    }

    @Nullable
    public static NonNullList<Ingredient> ingredients(Object recipe) {
        if (!(recipe instanceof Recipe<?> r)) {
            return null;
        }
        try {
            return r.getIngredients();
        } catch (RuntimeException | LinkageError ignored) {
            CompatibilityDiagnostics.failure(TableCraftingSupport.class, "ingredients", ignored);
            return null;
        }
    }

    public static boolean gridIsEmpty(IItemHandler handler, int slots) {
        for (int i = 0; i < slots; i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static List<ItemStack> currentStacks(IItemHandler handler, int slots) {
        var stacks = NonNullList.withSize(slots, ItemStack.EMPTY);
        for (int i = 0; i < slots; i++) {
            stacks.set(i, handler.getStackInSlot(i).copy());
        }
        return stacks;
    }

    public static boolean canApplyRemaining(IItemHandler handler, int gridSize, CraftingInput input,
                                             NonNullList<ItemStack> remaining, int top, int left) {
        if (remaining.size() < input.size()) {
            return false;
        }
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int inputIndex = x + y * input.width();
                int slot = x + left + (y + top) * gridSize;
                var current = handler.getStackInSlot(slot);
                if (current.isEmpty()) {
                    continue;
                }

                var remainder = remaining.get(inputIndex);
                if (remainder.isEmpty()) {
                    continue;
                }

                int afterConsume = current.getCount() - 1;
                if (afterConsume <= 0) {
                    continue;
                }
                if (!ItemStack.isSameItemSameComponents(current, remainder)) {
                    return false;
                }
                if (afterConsume + remainder.getCount() > current.getMaxStackSize()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void applyCraftRemainders(IItemHandlerModifiable handler, int gridSize, CraftingInput input,
                                             NonNullList<ItemStack> remaining, int top, int left) {
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int inputIndex = x + y * input.width();
                int slot = x + left + (y + top) * gridSize;
                var current = handler.getStackInSlot(slot);
                if (!current.isEmpty()) {
                    handler.extractItem(slot, 1, false);
                }

                var remainder = remaining.get(inputIndex);
                if (remainder.isEmpty()) {
                    continue;
                }

                var after = handler.getStackInSlot(slot);
                if (after.isEmpty()) {
                    handler.setStackInSlot(slot, remainder.copy());
                } else if (ItemStack.isSameItemSameComponents(after, remainder)) {
                    var combined = after.copy();
                    combined.grow(remainder.getCount());
                    handler.setStackInSlot(slot, combined);
                }
            }
        }
    }
}
