package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
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
record MekRecipeBinding(MekmmMachineSpec spec, Map<PortGroup, List<Recipe<?>>> recipes) {
    @Nullable
    static MekRecipeBinding find(ServerLevel level, BlockEntity machine, MekmmMachineSpec spec,
            IPatternDetails pattern) {
        Object provider = invoke(machine, "getRecipeType");
        if (provider == null) return null;
        var method = ReflectionSupport.findMethodCached(provider.getClass(), "getRecipes", Level.class).orElse(null);
        Object found = method == null ? null : ReflectionSupport.invoke(method, provider, level).orElse(null);
        if (!(found instanceof List<?> holders)) return null;
        var matching = new LinkedHashMap<PortGroup, List<Recipe<?>>>();
        // Keep every supported mode so changing rotary direction does not leave a stale positive binding.
        for (var mode : spec.modes()) {
            var candidates = new ArrayList<Recipe<?>>();
            for (Object entry : holders) {
                if (!(entry instanceof Recipe<?> holder)) continue;
                Object definitions = invoke(holder, outputMethod(mode, true));
                if (!(definitions instanceof List<?> outputs)) continue;
                if (outputs.stream().anyMatch(output -> matchesOutputs(pattern, stacks(output)))) {
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
        var available = new ArrayList<GenericStack>();
        for (var counter : inputs) {
            for (var entry : counter) {
                if (entry.getLongValue() > 0) available.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        if (available.size() != mode.inputs().size()) return null;
        for (var holder : recipes.getOrDefault(mode, List.of())) {
            // Reject an old cached recipe after datapack reload, even before provider cache invalidation.
            var live = level.getRecipeManager().byKey(holder.getId()).orElse(null);
            if (live == null || live != holder) continue;
            var arranged = arrange(holder, mode, available);
            if (arranged == null) continue;
            Object[] stacks = arranged.stream().map(MekRecipeBinding::stack).toArray();
            Object output = invoke(holder, outputMethod(mode, false), stacks);
            if (matchesOutputs(pattern, stacks(output))) return arranged;
        }
        return null;
    }

    @Nullable
    private static List<GenericStack> arrange(Object recipe, PortGroup mode, List<GenericStack> available) {
        String[] ingredients = ingredientMethods(mode);
        var result = new ArrayList<GenericStack>();
        if (assign(recipe, mode, ingredients, available, new boolean[available.size()], result)) return List.copyOf(result);
        return null;
    }

    private static boolean assign(Object recipe, PortGroup mode, String[] ingredients,
            List<GenericStack> available, boolean[] used, List<GenericStack> result) {
        int slot = result.size();
        if (slot == ingredients.length) return true;
        Object ingredient = invoke(recipe, ingredients[slot]);
        if (ingredient == null) return false;
        for (int i = 0; i < available.size(); i++) {
            var input = available.get(i);
            if (used[i] || type(input.what()) != mode.inputs().get(slot).type()) continue;
            // Native ingredient predicates preserve tags/components even for an ID_ONLY pattern.
            if (!Boolean.TRUE.equals(invoke(ingredient, "testType", stack(input)))) continue;
            used[i] = true;
            result.add(input);
            if (assign(recipe, mode, ingredients, available, used, result)) return true;
            result.remove(result.size() - 1);
            used[i] = false;
        }
        return false;
    }

    private static String[] ingredientMethods(PortGroup mode) {
        if (mode.inputs().size() == 2) {
            return mode.inputs().get(0).type() == PortType.ITEM
                    ? new String[] { "getItemInput", "getChemicalInput" }
                    : new String[] { "getLeftInput", "getRightInput" };
        }
        if (rotary(mode)) {
            return new String[] { mode.inputs().get(0).type() == PortType.FLUID
                    ? "getFluidInput" : "getGasInput" };
        }
        return new String[] { "getInput" };
    }

    private static boolean rotary(PortGroup mode) {
        return mode.inputs().size() == 1 && mode.outputs().size() == 1
                && mode.inputs().get(0).type() != mode.outputs().get(0).type();
    }

    private static String outputMethod(PortGroup mode, boolean definition) {
        String method = rotary(mode)
                ? (mode.outputs().get(0).type() == PortType.FLUID ? "getFluidOutput" : "getGasOutput")
                : "getOutput";
        return definition ? method + "Definition" : method;
    }

    /** The machine must actually produce each declared key. Undeclared byproducts remain native. */
    static boolean matchesOutputs(IPatternDetails pattern, List<GenericStack> actual) {
        var expected = pattern.getOutputs();
        if (expected.length == 0 || actual.isEmpty()) return false;
        for (int i = 0; i < expected.length; i++) {
            var output = expected[i];
            if (output.amount() <= 0L) return false;
            boolean idOnly = OverloadPatternSemantics.isIdOnlyOutput(pattern, i);
            boolean found = actual.stream().anyMatch(candidate -> candidate.amount() > 0L && (idOnly
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
        var method = RecipeMethodAccess.find(target.getClass(), name, types).orElse(null);
        return method == null ? null : ReflectionSupport.invoke(method, target, args).orElse(null);
    }
}
