package com.moakiee.ae2lt.packaged.logic.multiblock.astral;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.IngredientAssignment;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Exact K=1 material assignment and side-effect-free output validation. */
final class AstralRecipeBinding {
    private static final int MAX_UNITS = 256;
    private static final String OUTPUT_PACKAGE = AstralAccess.ROOT + "recipe.altar.output.";
    private static final Set<String> PURE_MODIFIERS = Set.of("AltarOutputReplaceWithInput",
            "AltarOutputSetDataComponent", "AltarOutputCopyDataComponents", "AltarOutputMergeCrystalProperties",
            "AltarOutputSetCrystalCount", "AltarOutputSetFlag");
    private static final Set<String> RANDOM_COMPONENT_MODIFIERS = Set.of("AltarOutputGenerateIdentifier",
            "AltarOutputAddGemModifier", "AltarOutputAddEnchantmentModifier", "AltarOutputIncreaseEnchantments",
            "AltarOutputGenerateArtifactShardLoot");

    record Slot(int gridIndex, Predicate<ItemStack> ingredient) {}
    record Candidate(RecipeHolder<?> holder, List<Slot> slots, boolean altar) {}
    record Match(Candidate candidate, List<AEItemKey> keys) {}

    private AstralRecipeBinding() {}

    static Candidate candidate(RecipeHolder<?> holder, boolean altar) {
        var recipe = holder.value();
        var slots = new ArrayList<Slot>();
        if (!altar) {
            var ingredient = (Ingredient) AstralAccess.get(recipe, "getItemInput");
            slots.add(new Slot(0, ingredient::test));
        } else {
            var grid = AstralAccess.get(recipe, "getGrid");
            addGrid(slots, AstralAccess.list(grid, "getInputs"), 0, 9);
            addGrid(slots, AstralAccess.list(grid, "getRelayInputs"), 9, 25);
            for (Object counted : AstralAccess.list(recipe, "getRequiredAdditionalInputs")) {
                int count = (Integer) AstralAccess.get(counted, "count");
                if (count <= 0 || count > MAX_UNITS - slots.size()) return null;
                var ingredient = (Ingredient) AstralAccess.get(counted, "ingredient");
                for (int i = 0; i < count; i++) slots.add(new Slot(-1, ingredient::test));
            }
        }
        return slots.isEmpty() || slots.size() > MAX_UNITS ? null : new Candidate(holder, List.copyOf(slots), altar);
    }

    private static void addGrid(List<Slot> slots, List<?> ingredients, int offset, int size) {
        if (ingredients.size() != size) throw new IllegalStateException("Unexpected Astral grid size");
        for (int i = 0; i < size; i++) {
            var ingredient = ingredients.get(i);
            Object empty = AstralAccess.get(ingredient, "isEmpty");
            if (!(empty instanceof Boolean)) throw new IllegalStateException("Unknown Astral ingredient");
            if (Boolean.TRUE.equals(empty)) continue;
            slots.add(new Slot(offset + i, stack -> Boolean.TRUE.equals(AstralAccess.call(ingredient,
                    "test", new Class<?>[]{ItemStack.class}, stack))));
        }
    }

    static KeyCounter[] patternInputs(IPatternDetails pattern) {
        var result = new ArrayList<KeyCounter>();
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length == 0 || input.getMultiplier() <= 0) return null;
            long amount = Math.multiplyExact(possible[0].amount(), input.getMultiplier());
            if (amount <= 0 || amount > MAX_UNITS) return null;
            var counter = new KeyCounter(); counter.add(possible[0].what(), amount); result.add(counter);
        }
        return result.toArray(KeyCounter[]::new);
    }

    static Match match(Candidate candidate, KeyCounter[] inputs, IPatternDetails pattern, ServerLevel level) {
        if (inputs == null) return null;
        var materials = new ArrayList<IngredientAssignment.Input<AEItemKey>>();
        for (var counter : inputs) for (var entry : counter) {
            if (entry.getLongValue() == 0) continue;
            if (!(entry.getKey() instanceof AEItemKey item) || entry.getLongValue() < 0) return null;
            materials.add(new IngredientAssignment.Input<>(item, entry.getLongValue()));
        }
        var keys = IngredientAssignment.assign(candidate.slots(), materials, 1,
                (slot, key) -> slot.ingredient().test(key.toStack(1)));
        if (keys == null) return null;
        var match = new Match(candidate, keys);
        var outputs = preview(match, level, pattern);
        return outputs != null && outputsMatch(pattern, outputs) ? match : null;
    }

    private static List<ItemStack> preview(Match match, ServerLevel level, IPatternDetails pattern) {
        Object recipe = match.candidate().holder().value();
        if (!match.candidate().altar()) return List.of((ItemStack) AstralAccess.get(recipe, "getOutput"));
        var main = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        var relays = new ArrayList<>(Collections.nCopies(25, ItemStack.EMPTY));
        for (int i = 0; i < match.keys().size(); i++) {
            int slot = match.candidate().slots().get(i).gridIndex();
            if (slot >= 0 && slot < 9) main.set(slot, match.keys().get(i).toStack(1));
            if (slot >= 9) relays.set(slot - 9, match.keys().get(i).toStack(1));
        }
        var inputType = AstralAccess.type("recipe.altar.AltarCraftingInput");
        Object input = AstralAccess.call(inputType, "createDisplay", new Class<?>[]{
                AstralAccess.type("constellation.BaseConstellation"), List.class, List.class}, null, main, relays);
        var outputs = new ArrayList<ItemStack>();
        for (var output : AstralAccess.list(recipe, "getOutputs")) outputs.add(((ItemStack) output).copy());
        for (Object modifier : AstralAccess.list(recipe, "getOutputModifiers")) {
            String name = modifier.getClass().getName();
            if (!name.startsWith(OUTPUT_PACKAGE)) return null;
            name = name.substring(OUTPUT_PACKAGE.length());
            boolean random = RANDOM_COMPONENT_MODIFIERS.contains(name);
            // World-changing/unknown modifiers are not processing outputs. Never execute them while binding.
            if (!PURE_MODIFIERS.contains(name) && !random) return null;
            if (random) {
                for (int i = 0; i < pattern.getOutputs().size(); i++) {
                    if (!OverloadPatternSemantics.isIdOnlyOutput(pattern, i)) return null;
                }
            }
            for (int i = 0; i < outputs.size(); i++) {
                Object result = AstralAccess.call(modifier, random ? "modifyOutputForDisplay" : "modifyOutput",
                        new Class<?>[]{ItemStack.class, inputType, HolderLookup.Provider.class},
                        outputs.get(i).copy(), input, level.registryAccess());
                if (!(result instanceof ItemStack stack)) return null;
                outputs.set(i, stack);
            }
        }
        outputs.removeIf(ItemStack::isEmpty);
        return outputs;
    }

    static boolean outputsMatch(IPatternDetails pattern, List<ItemStack> outputs) {
        // Assign entire stacks, including amounts, to avoid ambiguous strict/ID-only output overlap.
        var expected = pattern.getOutputs();
        if (expected.isEmpty() || outputs.size() != expected.size()) return false;
        var indexed = new ArrayList<Integer>();
        var actual = new ArrayList<IngredientAssignment.Input<ItemStack>>();
        for (int i = 0; i < expected.size(); i++) indexed.add(i);
        for (var stack : outputs) actual.add(new IngredientAssignment.Input<>(stack, 1));
        return IngredientAssignment.assign(indexed, actual, 1, (index, stack) -> {
            GenericStack wanted = expected.get(index);
            if (!(wanted.what() instanceof AEItemKey key) || stack.isEmpty() || wanted.amount() != stack.getCount()) return false;
            var result = AEItemKey.of(stack);
            return OverloadPatternSemantics.isIdOnlyOutput(pattern, index)
                    ? key.dropSecondary().equals(result.dropSecondary()) : key.equals(result);
        }) != null;
    }
}
