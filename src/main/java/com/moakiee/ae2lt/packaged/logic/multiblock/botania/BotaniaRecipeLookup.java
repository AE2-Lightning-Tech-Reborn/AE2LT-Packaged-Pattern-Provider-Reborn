package com.moakiee.ae2lt.packaged.logic.multiblock.botania;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;

import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;

/** Botania recipe lookup and validation against the pattern's declared outputs. */
public final class BotaniaRecipeLookup {

    private BotaniaRecipeLookup() {
    }

    // Generic helpers

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Recipe<?>> recipesOf(ServerLevel level, @Nullable RecipeType<?> type) {
        if (type == null) {
            return List.of();
        }
        try {
            return (List<Recipe<?>>) (List<?>) level.getRecipeManager()
                    .getAllRecipesFor((RecipeType) type);
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    @Nullable
    private static ItemStack safeResult(Recipe<?> recipe, ServerLevel level) {
        try {
            return recipe.getResultItem();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /** Returns the sole item output key, or empty for any other output shape. */
    public static Optional<AEItemKey> patternPrimaryOutputKey(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        if (outputs.length != 1) {
            return Optional.empty();
        }
        var first = outputs[0];
        return first.what() instanceof AEItemKey key ? Optional.of(key) : Optional.empty();
    }

    /** Returns the sole output amount, or zero unless exactly one output is declared. */
    public static long patternPrimaryOutputAmount(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        if (outputs.length != 1) {
            return 0L;
        }
        return outputs[0].amount();
    }

    /**
     * Checks the result key with strict NBT, or item id under ID_ONLY.
     * Batch amount validation is deferred until the craft ratio is known.
     */
    public static boolean validatePatternOutputKey(IPatternDetails pattern, ItemStack actualResult) {
        var outputs = pattern.getOutputs();
        if (outputs.length != 1) {
            return false;
        }
        var expected = outputs[0];
        if (!(expected.what() instanceof AEItemKey expectedKey)) {
            return false;
        }
        var actualKey = AEItemKey.of(actualResult);
        return OverloadPatternSemantics.isIdOnlyOutput(pattern, 0)
                ? expectedKey.dropSecondary().equals(actualKey.dropSecondary())
                : expectedKey.equals(actualKey);
    }

    /** Checks the result key and exact total batch count. */
    public static boolean validatePatternBatchOutput(IPatternDetails pattern, ItemStack actualResult,
                                                     long expectedTotalCount) {
        var outputs = pattern.getOutputs();
        return outputs.length == 1 && outputs[0].amount() == expectedTotalCount
                && validatePatternOutputKey(pattern, actualResult);
    }

    /** Matches every recipe output exactly once, including counts, and rejects extra outputs. */
    public static boolean validatePatternMultiOutput(IPatternDetails pattern, List<ItemStack> actualResults) {
        var patternOutputs = pattern.getOutputs();
        if (patternOutputs.length != actualResults.size()) {
            return false;
        }
        var expectedKeys = new ArrayList<AEItemKey>(actualResults.size());
        for (var stack : actualResults) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            expectedKeys.add(AEItemKey.of(stack));
        }
        var matched = new boolean[actualResults.size()];
        boolean idOnly = OverloadPatternSemantics.isIdOnlyOutput(pattern, 0);
        for (var patternOut : patternOutputs) {
            if (!(patternOut.what() instanceof AEItemKey patternKey)) {
                return false;
            }
            int hitIdx = -1;
            for (int i = 0; i < expectedKeys.size(); i++) {
                if (matched[i]) {
                    continue;
                }
                var expectedKey = expectedKeys.get(i);
                boolean keyOk = idOnly
                        ? patternKey.dropSecondary().equals(expectedKey.dropSecondary())
                        : patternKey.equals(expectedKey);
                if (keyOk && patternOut.amount() == actualResults.get(i).getCount()) {
                    hitIdx = i;
                    break;
                }
            }
            if (hitIdx < 0) {
                return false;
            }
            matched[hitIdx] = true;
        }
        return true;
    }

    // Mana Infusion (Mana Pool)

    /** Finds a mana-infusion recipe with a compatible output key. */
    @Nullable
    public static Recipe<?> findManaInfusionByOutput(ServerLevel level, IPatternDetails pattern) {
        return findSingleOutputRecipe(level, BotaniaReflection.manaInfusionType(), pattern);
    }

    @Nullable
    private static Recipe<?> findSingleOutputRecipe(ServerLevel level, @Nullable RecipeType<?> type,
                                                     IPatternDetails pattern) {
        var patternKey = patternPrimaryOutputKey(pattern).orElse(null);
        if (patternKey == null) {
            return null;
        }
        for (var recipe : recipesOf(level, type)) {
            var result = safeResult(recipe, level);
            if (result == null || result.isEmpty()) {
                continue;
            }
            if (result.getItem() != patternKey.getItem()) {
                continue;
            }
            if (!validatePatternOutputKey(pattern, result)) {
                continue;
            }
            return recipe;
        }
        return null;
    }

    // Petal Apothecary

    @Nullable
    public static Recipe<?> findPetalApothecaryByOutput(ServerLevel level, IPatternDetails pattern) {
        return findPetalApothecaryByOutput(
                recipesOf(level, BotaniaReflection.petalType()), level.registryAccess(), pattern);
    }

    /** Select the first output-compatible candidate whose batch inputs are covered. */
    @Nullable
    static Recipe<?> findPetalApothecaryByOutput(Iterable<Recipe<?>> recipes,
                                               RegistryAccess registries, IPatternDetails pattern) {
        var budget = new PetalApothecaryAdapter.SearchBudget();
        var patternKey = patternPrimaryOutputKey(pattern).orElse(null);
        if (patternKey == null) {
            return null;
        }
        for (var recipe : recipes) {
            ItemStack result;
            try {
                result = recipe.getResultItem();
            } catch (RuntimeException | LinkageError ignored) {
                continue;
            }
            if (result == null || result.isEmpty()) {
                continue;
            }
            if (result.getItem() != patternKey.getItem()) {
                continue;
            }
            if (!validatePatternOutputKey(pattern, result)) {
                continue;
            }
            long amount = patternPrimaryOutputAmount(pattern);
            int resultCount = result.getCount();
            if (amount <= 0 || resultCount <= 0 || amount % resultCount != 0) {
                continue;
            }
            long craftRatio = amount / resultCount;
            if (craftRatio > Integer.MAX_VALUE
                    || !PetalApothecaryAdapter.patternInputsCoverRecipe(pattern, recipe, craftRatio, budget)) {
                if (budget.exhausted()) {
                    return null;
                }
                continue;
            }
            return recipe;
        }
        return null;
    }

    // Runic Altar

    /**
     * Matches the primary result and all catalyst refunds against pattern outputs.
     * Catalysts are dispatched with ingredients and returned after crafting.
     */
    @Nullable
    public static Recipe<?> findRunicAltarByOutput(ServerLevel level, IPatternDetails pattern) {
        var patternOutputs = pattern.getOutputs();
        if (patternOutputs.length == 0) {
            return null;
        }
        for (var recipe : recipesOf(level, BotaniaReflection.runeType())) {
            var result = safeResult(recipe, level);
            if (result == null || result.isEmpty()) {
                continue;
            }
            var catalysts = BotaniaReflection.recipeCatalysts(recipe);
            if (validatePatternRunicOutputs(pattern, result, catalysts)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Resolves the live altar inventory because Botania 1.20.1 never sets currentRecipe.
     * The direct Recipe.matches call is reobfuscated with the vanilla interface.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Recipe<?> findRunicAltarMatch(ServerLevel level, Container inventory) {
        for (var recipe : recipesOf(level, BotaniaReflection.runeType())) {
            try {
                if (((Recipe) recipe).matches(inventory, level)) {
                    return recipe;
                }
            } catch (RuntimeException | LinkageError ignored) {
                // Malformed third-party recipe; try the next one.
            }
        }
        return null;
    }

    /**
     * Requires the exact primary count and one output per non-empty catalyst.
     * Primary keys follow the output match mode; catalyst matching always uses
     * Ingredient.test so ID_ONLY cannot substitute a cheaper catalyst variant.
     */
    public static boolean validatePatternRunicOutputs(IPatternDetails pattern,
                                                      ItemStack primaryResult,
                                                      List<Ingredient> catalysts) {
        var patternOutputs = pattern.getOutputs();
        int nonEmptyCatalystCount = 0;
        for (var c : catalysts) {
            if (!c.isEmpty()) {
                nonEmptyCatalystCount++;
            }
        }
        if (patternOutputs.length != 1 + nonEmptyCatalystCount) {
            return false;
        }
        boolean idOnly = OverloadPatternSemantics.isIdOnlyOutput(pattern, 0);
        var primaryKey = AEItemKey.of(primaryResult);
        boolean primaryMatched = false;
        var usedCatalysts = new boolean[catalysts.size()];

        for (var patOut : patternOutputs) {
            if (!(patOut.what() instanceof AEItemKey patKey)) {
                return false;
            }
            // Prefer the primary slot. Catalysts and primary share an id
            // space in theory but RunicAltarRecipe forbids overlap, so
            // first-come-first-served on primary is unambiguous here.
            if (!primaryMatched) {
                boolean primaryKeyOk = idOnly
                        ? patKey.dropSecondary().equals(primaryKey.dropSecondary())
                        : patKey.equals(primaryKey);
                if (primaryKeyOk && patOut.amount() == primaryResult.getCount()) {
                    primaryMatched = true;
                    continue;
                }
            }
            // Try assigning to a still-free catalyst slot.
            boolean catalystMatched = false;
            for (int i = 0; i < catalysts.size(); i++) {
                if (usedCatalysts[i]) {
                    continue;
                }
                var cat = catalysts.get(i);
                if (cat.isEmpty()) {
                    continue;
                }
                if (patOut.amount() != 1L) {
                    // Catalysts are always returned 1-for-1; any other
                    // amount means the pattern doesn't model this recipe.
                    continue;
                }
                if (!cat.test(patKey.toStack(1))) {
                    continue;
                }
                usedCatalysts[i] = true;
                catalystMatched = true;
                break;
            }
            if (!catalystMatched) {
                return false;
            }
        }
        return primaryMatched;
    }

    // Terra Plate (Terrestrial Agglomeration)

    @Nullable
    public static Recipe<?> findTerraPlateByOutput(ServerLevel level, IPatternDetails pattern) {
        return findSingleOutputRecipe(level, BotaniaReflection.terraPlateType(), pattern);
    }

    // Elven Trade (Alfheim Portal)

    /** Finds an elven-trade recipe whose full output list matches the pattern. */
    @Nullable
    public static Recipe<?> findElvenTradeByOutputs(ServerLevel level, IPatternDetails pattern) {
        var patternOutputs = pattern.getOutputs();
        if (patternOutputs.length == 0) {
            return null;
        }
        for (var recipe : recipesOf(level, BotaniaReflection.elvenTradeType())) {
            var outputs = elvenTradeOutputs(recipe);
            if (outputs.isEmpty()) {
                continue;
            }
            if (!validatePatternMultiOutput(pattern, outputs)) {
                continue;
            }
            return recipe;
        }
        return null;
    }

    /** Reads ElvenTradeRecipe outputs without a compile dependency on Botania. */
    @SuppressWarnings("unchecked")
    public static List<ItemStack> elvenTradeOutputs(Object recipe) {
        if (!BotaniaReflection.isElvenTradeRecipe(recipe)) {
            return List.of();
        }
        try {
            var m = ReflectionSupport.findMethodCached(recipe.getClass(), "getOutputs").orElse(null);
            if (m == null) {
                return List.of();
            }
            var v = m.invoke(recipe);
            return v instanceof List<?> list ? (List<ItemStack>) list : List.of();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    // Recipe-specific scalar getters

    public static int manaInfusionCost(Object recipe) {
        return BotaniaReflection.invokeRecipeIntMethod(recipe, "getManaToConsume", 0);
    }
}
