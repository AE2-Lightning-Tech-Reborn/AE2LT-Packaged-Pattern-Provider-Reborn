package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.util.ArrayList;
import java.util.Arrays;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekmmMachineSpec.PortGroup;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekmmMachineSpec.PortType;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;

/** Binds a packaged pattern to the native MekMM recipe and port order. */
record MekRecipeBinding(MekmmMachineSpec spec, Map<PortGroup, List<Recipe<?>>> recipes) {
    private static final Map<MethodKey, Optional<Method>> METHODS = new ConcurrentHashMap<>();
    private record MethodKey(Class<?> type, String name, List<Class<?>> arguments) {}

    @Nullable
    static MekRecipeBinding find(ServerLevel level, BlockEntity machine, MekmmMachineSpec spec,
                                 IPatternDetails pattern) {
        if (machine == null) return null;
        Object provider = invoke(machine, "getRecipeType");
        if (provider == null) return null;
        var method = ReflectionSupport.findMethodCached(provider.getClass(), "getRecipes", Level.class)
                .orElse(null);
        Object found = method == null ? null : ReflectionSupport.invoke(method, provider, level).orElse(null);
        if (!(found instanceof List<?> holders)) return null;
        var matching = new LinkedHashMap<PortGroup, List<Recipe<?>>>();
        var patternInputs = patternInputs(pattern);
        for (var mode : spec.modes()) {
            var candidates = new ArrayList<Recipe<?>>();
            for (Object entry : holders) {
                if (!(entry instanceof Recipe<?> recipe)) continue;
                if (arrangePattern(recipe, mode, pattern, patternInputs) != null) {
                    candidates.add(recipe);
                }
            }
            if (!candidates.isEmpty()) matching.put(mode, List.copyOf(candidates));
        }
        return matching.isEmpty() ? null : new MekRecipeBinding(spec, Map.copyOf(matching));
    }

    boolean supports(PortGroup mode) { return recipes.containsKey(mode); }

    /** Returns inputs in native recipe order, rejecting before any insertion. */
    @Nullable
    List<GenericStack> arrange(ServerLevel level, PortGroup mode, IPatternDetails pattern,
                               KeyCounter[] inputs) {
        var available = new ArrayList<GenericStack>();
        if (inputs == null) return null;
        for (var counter : inputs) {
            if (counter == null) continue;
            for (var entry : counter) {
                if (entry.getLongValue() < 0) return null;
                if (entry.getLongValue() > 0) {
                    available.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                }
            }
        }
        if (available.size() != mode.inputs().size()) return null;
        for (var recipe : recipes.getOrDefault(mode, List.of())) {
            var live = level.getRecipeManager().byKey(recipe.getId()).orElse(null);
            if (live == null || live.getType() != recipe.getType()) continue;
            var arranged = arrange(live, mode, pattern, available);
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
                if (possible.amount() <= 0
                        || possible.amount() > Long.MAX_VALUE / input.getMultiplier()) continue;
                choices.add(new GenericStack(possible.what(),
                        possible.amount() * input.getMultiplier()));
            }
            if (choices.isEmpty()) return null;
            result.add(List.copyOf(choices));
        }
        return result;
    }

    @Nullable
    private static List<GenericStack> arrangePattern(Object recipe, PortGroup mode,
                                                      IPatternDetails pattern,
                                                      List<List<GenericStack>> patternInputs) {
        if (patternInputs == null || patternInputs.size() != mode.inputs().size()) return null;
        var result = new ArrayList<GenericStack>();
        return assign(recipe, mode, pattern, ingredientMethods(mode), patternInputs,
                new boolean[patternInputs.size()], result, 0)
                ? List.copyOf(result) : null;
    }

    @Nullable
    private static List<GenericStack> arrange(Object recipe, PortGroup mode,
                                              IPatternDetails pattern,
                                              List<GenericStack> available) {
        var choices = new ArrayList<List<GenericStack>>(available.size());
        for (var stack : available) choices.add(List.of(stack));
        var result = new ArrayList<GenericStack>();
        return assign(recipe, mode, pattern, ingredientMethods(mode), choices,
                new boolean[choices.size()], result, 0)
                ? List.copyOf(result) : null;
    }

    private static boolean assign(Object recipe, PortGroup mode, IPatternDetails pattern,
                                  String[] ingredients, List<List<GenericStack>> available,
                                  boolean[] used, List<GenericStack> result, long copies) {
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
                if (input == null || type(input.what()) != mode.inputs().get(slot).type()) continue;
                Object nativeStack = stack(input);
                if (nativeStack == null || !Boolean.TRUE.equals(invoke(ingredient, "testType", nativeStack))) continue;
                Object needed = invoke(ingredient, "getNeededAmount", nativeStack);
                if (!(needed instanceof Number number) || number.longValue() <= 0) continue;
                long perRecipe = number.longValue();
                long inputCopies;
                if (slot > 0 && mode.inputs().get(slot).type() == PortType.CHEMICAL
                        && Boolean.TRUE.equals(invoke(recipe, "perTickUsage"))) {
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
                result.remove(result.size() - 1);
                used[i] = false;
            }
        }
        return false;
    }

    private static String[] ingredientMethods(PortGroup mode) {
        if (mode.inputs().size() == 2) {
            return mode.inputs().get(0).type() == PortType.ITEM
                    ? new String[] {"getItemInput", "getChemicalInput"}
                    : new String[] {"getLeftInput", "getRightInput"};
        }
        if (rotary(mode)) {
            return new String[] {mode.inputs().get(0).type() == PortType.FLUID
                    ? "getFluidInput" : "getGasInput"};
        }
        return new String[] {"getInput"};
    }

    private static boolean rotary(PortGroup mode) {
        return mode.inputs().size() == 1 && mode.outputs().size() == 1
                && mode.inputs().get(0).type() != mode.outputs().get(0).type();
    }

    private static String outputMethod(PortGroup mode) {
        return rotary(mode)
                ? (mode.outputs().get(0).type() == PortType.FLUID
                        ? "getFluidOutput" : "getGasOutput")
                : "getOutput";
    }

    private static boolean matchesOutputs(IPatternDetails pattern, List<GenericStack> actual,
                                           long copies) {
        var expected = pattern.getOutputs();
        if (copies <= 0 || expected == null || expected.length == 0 || actual.isEmpty()) return false;
        for (int i = 0; i < expected.length; i++) {
            var output = expected[i];
            if (output.amount() <= 0L || output.amount() % copies != 0) return false;
            boolean idOnly = OverloadPatternSemantics.isIdOnlyOutput(pattern, i);
            boolean found = actual.stream().anyMatch(candidate -> candidate.amount() > 0
                    && candidate.amount() == output.amount() / copies
                    && (idOnly ? output.what().dropSecondary().equals(candidate.what().dropSecondary())
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
        if (value.what() instanceof AEItemKey item) {
            return item.toStack((int) Math.min(Integer.MAX_VALUE, value.amount()));
        }
        if (value.what() instanceof AEFluidKey fluid) {
            return fluid.toStack((int) Math.min(Integer.MAX_VALUE, value.amount()));
        }
        return AppmekReflection.toChemicalStackWithAmount(value.what(), value.amount());
    }

    private static List<GenericStack> stacks(@Nullable Object output) {
        if (output instanceof ItemStack item) {
            return item.isEmpty() ? List.of()
                    : List.of(new GenericStack(AEItemKey.of(item), item.getCount()));
        }
        if (output instanceof FluidStack fluid) {
            return fluid.isEmpty() ? List.of()
                    : List.of(new GenericStack(AEFluidKey.of(fluid), fluid.getAmount()));
        }
        if (output == null) return List.of();
        Object left = invoke(output, "left");
        Object right = invoke(output, "right");
        if (left != null && right != null) {
            var result = new ArrayList<GenericStack>(stacks(left));
            result.addAll(stacks(right));
            return result;
        }
        AEKey chemical = AppmekReflection.fromChemicalStack(output);
        long amount = MekReflection.getChemicalAmount(output);
        return chemical == null || amount <= 0 ? List.of()
                : List.of(new GenericStack(chemical, amount));
    }

    @Nullable
    static Object invoke(Object target, String name, Object... args) {
        if (target == null) return null;
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) return null;
            types[i] = args[i].getClass();
        }
        var key = new MethodKey(target.getClass(), name, List.copyOf(Arrays.asList(types)));
        var method = METHODS.computeIfAbsent(key, lookup -> {
            var exact = ReflectionSupport.findMethodCached(lookup.type(), name, types);
            if (exact.isPresent()) return exact;
            // Mekanism's generic recipe methods accept ChemicalStack, not the concrete GasStack.
            for (var candidate : lookup.type().getMethods()) {
                if (!candidate.getName().equals(name) || candidate.isBridge()
                        || candidate.getParameterCount() != types.length) continue;
                var parameters = candidate.getParameterTypes();
                boolean compatible = true;
                for (int i = 0; i < types.length; i++) {
                    if (!parameters[i].isAssignableFrom(types[i])) { compatible = false; break; }
                }
                if (compatible) return ReflectionSupport.findMethodCached(lookup.type(), name, parameters);
            }
            return Optional.empty();
        }).orElse(null);
        return method == null ? null : ReflectionSupport.invoke(method, target, args).orElse(null);
    }
}
