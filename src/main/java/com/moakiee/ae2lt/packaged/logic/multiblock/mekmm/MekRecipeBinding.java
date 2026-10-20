package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekmmMachineSpec.PortGroup;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekmmMachineSpec.PortType;

/** Uses each controller's native recipe type; a shared core does not imply shared recipes. */
record MekRecipeBinding(MekmmMachineSpec spec, Map<PortGroup, List<RecipeHolder<?>>> recipes) {
    @Nullable
    static MekRecipeBinding find(ServerLevel level, BlockEntity machine, MekmmMachineSpec spec,
            IPatternDetails pattern) {
        var inputs = patternInputs(pattern);
        if (inputs == null) return null;
        Object provider = invoke(machine, "getRecipeType");
        if (provider == null) return null;
        var method = ReflectionSupport.findMethodCached(provider.getClass(), "getRecipes", Level.class).orElse(null);
        Object found = method == null ? null : ReflectionSupport.invoke(method, provider, level).orElse(null);
        if (!(found instanceof List<?> holders)) return null;
        var matching = new LinkedHashMap<PortGroup, List<RecipeHolder<?>>>();
        // Keep every supported mode so changing rotary direction does not leave a stale positive binding.
        for (var mode : spec.modes()) {
            var candidates = new ArrayList<RecipeHolder<?>>();
            for (Object entry : holders) {
                if (!(entry instanceof RecipeHolder<?> holder)) continue;
                if (arrange(holder.value(), mode, pattern, inputs) != null) {
                    candidates.add(holder);
                }
            }
            if (!candidates.isEmpty()) matching.put(mode, List.copyOf(candidates));
        }
        return matching.isEmpty() ? null : new MekRecipeBinding(spec, Map.copyOf(matching));
    }

    boolean supports(PortGroup mode) {
        return recipes.containsKey(mode);
    }

    /** Returns inputs in the recipe's port order, or rejects before any material is inserted. */
    @Nullable
    List<GenericStack> arrange(ServerLevel level, PortGroup mode, IPatternDetails pattern, KeyCounter[] inputs) {
        var available = new ArrayList<List<GenericStack>>();
        for (var counter : inputs) {
            for (var entry : counter) {
                if (entry.getLongValue() < 0) return null;
                if (entry.getLongValue() > 0) {
                    available.add(List.of(new GenericStack(entry.getKey(), entry.getLongValue())));
                }
            }
        }
        if (available.size() != mode.inputs().size()) return null;
        for (var holder : recipes.getOrDefault(mode, List.of())) {
            // Mekanism's shared recipe cache can hold an equivalent client-side recipe instance.
            // Revalidate against the current server recipe instead of comparing object identity;
            // this also rejects removed recipes, changed recipe types and incompatible reloads.
            var live = level.getRecipeManager().byKey(holder.id()).orElse(null);
            if (live == null || live.value().getType() != holder.value().getType()) continue;
            var arranged = arrange(live.value(), mode, pattern, available);
            if (arranged != null) return arranged;
        }
        return null;
    }

    @Nullable
    private static List<List<GenericStack>> patternInputs(IPatternDetails pattern) {
        var result = new ArrayList<List<GenericStack>>();
        for (var input : pattern.getInputs()) {
            if (input.getMultiplier() <= 0) return null;
            var choices = new ArrayList<GenericStack>();
            for (var possible : input.getPossibleInputs()) {
                if (possible.amount() <= 0 || possible.amount() > Long.MAX_VALUE / input.getMultiplier()) continue;
                choices.add(new GenericStack(possible.what(), possible.amount() * input.getMultiplier()));
            }
            if (choices.isEmpty()) return null;
            result.add(List.copyOf(choices));
        }
        return result;
    }

    @Nullable
    private static List<GenericStack> arrange(Object recipe, PortGroup mode, IPatternDetails pattern,
            List<List<GenericStack>> available) {
        if (available.size() != mode.inputs().size()) return null;
        String[] ingredients = ingredientMethods(mode);
        var result = new ArrayList<GenericStack>();
        if (assign(recipe, mode, pattern, ingredients, available, new boolean[available.size()], result, 0)) {
            return List.copyOf(result);
        }
        return null;
    }

    private static boolean assign(Object recipe, PortGroup mode, IPatternDetails pattern, String[] ingredients,
            List<List<GenericStack>> available, boolean[] used, List<GenericStack> result, long copies) {
        int slot = result.size();
        if (slot == ingredients.length) {
            Object[] stacks = result.stream().map(MekRecipeBinding::stack).toArray();
            Object output = invoke(recipe, outputMethod(mode), stacks);
            return matchesOutputs(pattern, stacks(output), copies);
        }
        Object ingredient = invoke(recipe, ingredients[slot]);
        if (ingredient == null) return false;
        for (int i = 0; i < available.size(); i++) {
            if (used[i]) continue;
            for (var input : available.get(i)) {
                if (type(input.what()) != mode.inputs().get(slot).type()) continue;
                // Native predicates preserve tags/components even for an ID_ONLY pattern.
                Object stack = stack(input);
                if (!Boolean.TRUE.equals(invoke(ingredient, "testType", stack))) continue;
                Object needed = invoke(ingredient, "getNeededAmount", stack);
                if (!(needed instanceof Number amount) || amount.longValue() <= 0) continue;
                long perRecipe = amount.longValue();
                long inputCopies;
                if (slot > 0 && mode.inputs().get(slot).type() == PortType.CHEMICAL
                        && Boolean.TRUE.equals(invoke(recipe, "perTickUsage"))) {
                    // Per-tick nucleosynthesis consumes a chemical supply over time. Its item
                    // input determines the recipe count; the chemical stack is not a fixed total.
                    if (copies <= 0 || input.amount() / perRecipe < copies) continue;
                    inputCopies = copies;
                } else {
                    if (input.amount() % perRecipe != 0) continue;
                    inputCopies = input.amount() / perRecipe;
                    if (inputCopies <= 0 || (copies != 0 && inputCopies != copies)) continue;
                }
                used[i] = true;
                result.add(input);
                if (assign(recipe, mode, pattern, ingredients, available, used, result, inputCopies)) return true;
                result.removeLast();
                used[i] = false;
            }
        }
        return false;
    }

    private static String[] ingredientMethods(PortGroup mode) {
        if (mode.inputs().size() == 2) {
            return mode.inputs().getFirst().type() == PortType.ITEM
                    ? new String[] { "getItemInput", "getChemicalInput" }
                    : new String[] { "getLeftInput", "getRightInput" };
        }
        if (rotary(mode)) {
            return new String[] { mode.inputs().getFirst().type() == PortType.FLUID
                    ? "getFluidInput" : "getChemicalInput" };
        }
        return new String[] { "getInput" };
    }

    private static boolean rotary(PortGroup mode) {
        return mode.inputs().size() == 1 && mode.outputs().size() == 1
                && mode.inputs().getFirst().type() != mode.outputs().getFirst().type();
    }

    private static String outputMethod(PortGroup mode) {
        return rotary(mode)
                ? (mode.outputs().getFirst().type() == PortType.FLUID ? "getFluidOutput" : "getChemicalOutput")
                : "getOutput";
    }

    /** Declared amounts must agree with the input multiplier. Undeclared byproducts remain native. */
    private static boolean matchesOutputs(IPatternDetails pattern, List<GenericStack> actual, long copies) {
        var expected = pattern.getOutputs();
        if (copies <= 0 || expected.isEmpty() || actual.isEmpty()) return false;
        for (int i = 0; i < expected.size(); i++) {
            var output = expected.get(i);
            if (output.amount() <= 0L || output.amount() % copies != 0) return false;
            boolean idOnly = OverloadPatternSemantics.isIdOnlyOutput(pattern, i);
            // Divide instead of multiplying native output amounts, which may overflow for chemical batches.
            boolean found = actual.stream().anyMatch(candidate -> candidate.amount() == output.amount() / copies && (idOnly
                    ? output.what().dropSecondary().equals(candidate.what().dropSecondary())
                    : output.what().equals(candidate.what())));
            if (!found) return false;
        }
        return true;
    }

    private static PortType type(AEKey key) {
        if (key instanceof AEItemKey) return PortType.ITEM;
        if (key instanceof AEFluidKey) return PortType.FLUID;
        return AppmekReflection.isMekanismKey(key) ? PortType.CHEMICAL : null;
    }

    @Nullable
    private static Object stack(GenericStack value) {
        if (value.what() instanceof AEItemKey item) return item.toStack((int) Math.min(Integer.MAX_VALUE, value.amount()));
        if (value.what() instanceof AEFluidKey fluid) return fluid.toStack((int) Math.min(Integer.MAX_VALUE, value.amount()));
        return AppmekReflection.toChemicalStackWithAmount(value.what(), value.amount());
    }

    private static List<GenericStack> stacks(@Nullable Object output) {
        if (output instanceof ItemStack item) {
            return item.isEmpty() ? List.of() : List.of(new GenericStack(AEItemKey.of(item), item.getCount()));
        }
        if (output instanceof FluidStack fluid) {
            return fluid.isEmpty() ? List.of() : List.of(new GenericStack(AEFluidKey.of(fluid), fluid.getAmount()));
        }
        if (output == null) return List.of();
        // Electrolysis has two real outputs, not a single ChemicalStack.
        Object left = invoke(output, "left");
        Object right = invoke(output, "right");
        if (left != null && right != null) {
            var result = new ArrayList<>(stacks(left));
            result.addAll(stacks(right));
            return result;
        }
        AEKey chemical = AppmekReflection.fromChemicalStack(output);
        long amount = MekReflection.getChemicalAmount(output);
        return chemical == null || amount <= 0L ? List.of() : List.of(new GenericStack(chemical, amount));
    }

    @Nullable
    private static Object invoke(Object target, String name, Object... args) {
        if (target == null) return null;
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) return null;
            types[i] = args[i].getClass();
        }
        var method = ReflectionSupport.findMethodCached(target.getClass(), name, types).orElse(null);
        return method == null ? null : ReflectionSupport.invoke(method, target, args).orElse(null);
    }
}
