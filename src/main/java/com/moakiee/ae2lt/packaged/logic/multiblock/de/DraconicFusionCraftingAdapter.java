package com.moakiee.ae2lt.packaged.logic.multiblock.de;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.logic.multiblock.AcceptedInsertion;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterBlocks;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.SlotHarvestOwnership;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;

/**
 * Runtime adapter for Draconic Evolution's Fusion Crafting multiblock.
 * Pure reflection — DE is not a compile dependency.
 */
public final class DraconicFusionCraftingAdapter implements MultiblockAdapter {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(DraconicFusionCraftingAdapter.class);

    private static final String MOD_ID = "draconicevolution";
    private static final ResourceLocation CRAFTING_CORE_BLOCK = deId("crafting_core");
    private static final int MAX_INPUT_UNITS = 128;
    private static final int MAX_OUTPUT_UNITS = 64;
    private static final String HARVEST_STATE = "de_fusion:harvest";

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null && isDeLoaded() && blockId(be.getBlockState()).equals(CRAFTING_CORE_BLOCK);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return com.moakiee.ae2lt.packaged.item.AdapterIds.DE_FUSION;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) return null;
        if (!hasSingleItemOutput(pattern)) return null;

        var recipe = findCandidateRecipe(level, pattern);
        if (recipe == null) return null;
        return new BindingResult(new FusionBindHandle(recipe), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        return canDispatch(level, mainPos, handle, AdapterPersistentScope.NOOP);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle,
                               AdapterPersistentScope scope) {
        if (!(handle instanceof FusionBindHandle bind)) return false;
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)
                || !DraconicFusionCraftingAccess.canStartCraft(be)) return false;
        if (DraconicFusionCraftingAccess.isCrafting(be)) return false;

        var outputStack = DraconicFusionCraftingAccess.getOutputStack(be);
        if (outputStack == null) return false;
        boolean outputEmpty = outputStack.isEmpty();
        if (SlotHarvestOwnership.blocksDispatch(level, mainPos, scope, HARVEST_STATE, outputEmpty)) {
            return false;
        }

        var expected = DraconicFusionCraftingAccess.getResultItem(bind.recipe(), level);
        if (!validOutput(expected) || !outputEmpty) return false;
        var injectors = DraconicFusionCraftingAccess.getInjectors(be, level);
        if (injectors == null) return false;

        var fusionIngredients = DraconicFusionCraftingAccess.getFusionIngredients(bind.recipe());
        var recipeTierIndex = DraconicFusionCraftingAccess.getRecipeTierIndex(bind.recipe());
        if (fusionIngredients == null || recipeTierIndex < 0) return false;
        return canSatisfyInjectors(injectors, recipeTierIndex, fusionIngredients);
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        return planWithBinding(level, mainPos, pattern, inputs, handle, source,
                AdapterPersistentScope.NOOP);
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source,
                                        AdapterPersistentScope scope) {
        if (!(handle instanceof FusionBindHandle bind)
                || !canDispatch(level, mainPos, handle, scope)) return null;
        var be = level.getBlockEntity(mainPos);
        if (be == null) return null;

        var units = expandInputUnits(inputs);
        if (units == null || units.isEmpty()) return null;

        var injectors = DraconicFusionCraftingAccess.getInjectors(be, level);
        if (injectors == null) return null;

        var match = assignInputsToRecipe(bind.recipe(), level, units, injectors);
        if (match == null) return null;

        var targets = new ArrayList<TargetSlot>(match.injectorAssignments().size() + 1);
        targets.add(new TargetSlot(
                level, mainPos, null,
                List.of(match.catalyst().toGenericStack()),
                InsertionStrategy.CUSTOM,
                coreInserter(level, mainPos, match.catalyst())));

        for (var assignment : match.injectorAssignments()) {
            targets.add(new TargetSlot(
                    level, assignment.injectorPos(), null,
                    List.of(assignment.unit().toGenericStack()),
                    InsertionStrategy.CUSTOM,
                    injectorInserter(level, mainPos, assignment.injectorPos(), assignment.unit())));
        }

        return new DispatchPlan(List.copyOf(targets), () -> {
            DraconicFusionCraftingAccess.startCraftIfIdle(be);
            var expected = match.result();
            SlotHarvestOwnership.store(level, mainPos, scope, HARVEST_STATE,
                    expected, recipeId(bind.recipe()));
        }, (accepted, recovered) -> recoverPartialDispatch(
                level, mainPos, targets, accepted, recovered));
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        return extractOutputs(level, mainPos, filter, source, AdapterPersistentScope.NOOP);
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source,
                                             AdapterPersistentScope scope) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)
                || DraconicFusionCraftingAccess.isCrafting(be)) return List.of();
        var ownership = SlotHarvestOwnership.load(level, mainPos, scope, HARVEST_STATE);
        if (ownership == null || SlotHarvestOwnership.expired(level, ownership)) return List.of();
        var stack = DraconicFusionCraftingAccess.getOutputStack(be);
        var expected = ownership.expectedOutput();
        if (stack == null || stack.isEmpty()
                || !ItemStack.isSameItemSameTags(stack, expected)
                || stack.getCount() < expected.getCount()) return List.of();
        var key = AEItemKey.of(stack);
        if (!filter.matches(key) || !allowsAutoReturn(level, mainPos, filter, key)) return List.of();
        var extracted = DraconicFusionCraftingAccess.extractOutput(be, expected.getCount());
        if (extracted.isEmpty() || extracted.getCount() != expected.getCount()
                || !ItemStack.isSameItemSameTags(extracted, expected)) return List.of();
        SlotHarvestOwnership.clear(level, mainPos, scope, HARVEST_STATE);
        return List.of(new GenericStack(AEItemKey.of(extracted), extracted.getCount()));
    }

    // ===== Recipe matching =====

    /**
     * Bind-time search: locks the recipe by pattern.outputs alone, without
     * needing the runtime KeyCounter inputs or injector tiers.
     */
    @Nullable
    private static Object findCandidateRecipe(ServerLevel level, IPatternDetails pattern) {
        var recipes = DraconicFusionCraftingAccess.getFusionRecipes(level);
        if (recipes == null) return null;

        for (var recipe : recipes) {
            var catalyst = DraconicFusionCraftingAccess.getCatalyst(recipe);
            var fusionIngredients = DraconicFusionCraftingAccess.getFusionIngredients(recipe);
            var recipeTierIndex = DraconicFusionCraftingAccess.getRecipeTierIndex(recipe);
            var resultItem = DraconicFusionCraftingAccess.getResultItem(recipe, level);
            if (catalyst == null || fusionIngredients == null
                    || fusionIngredients.stream().anyMatch(info -> !info.consume())
                    || recipeTierIndex < 0 || resultItem == null || resultItem.isEmpty()) {
                continue;
            }
            if (!outputMatches(pattern, resultItem)) continue;
            return recipe;
        }
        return null;
    }

    /**
     * Per-push input assignment against the recipe locked in by {@link #bind}.
     * Splits inputs into catalyst units and ingredient units, then matches
     * ingredient units to injectors that meet the recipe tier.
     */
    @Nullable
    private RecipeMatch assignInputsToRecipe(Object recipe, ServerLevel level,
                                              List<PlannedUnit> units,
                                              List<InjectorInfo> injectors) {
        var catalyst = DraconicFusionCraftingAccess.getCatalyst(recipe);
        var fusionIngredients = DraconicFusionCraftingAccess.getFusionIngredients(recipe);
        var recipeTierIndex = DraconicFusionCraftingAccess.getRecipeTierIndex(recipe);
        var resultItem = DraconicFusionCraftingAccess.getResultItem(recipe, level);
        if (catalyst == null || fusionIngredients == null
                || fusionIngredients.stream().anyMatch(info -> !info.consume())
                || recipeTierIndex < 0 || resultItem == null || resultItem.isEmpty()
                || !validOutput(resultItem)) {
            return null;
        }

        int catalystCount = DraconicFusionCraftingAccess.getCatalystCount(recipe);
        if (catalystCount <= 0) catalystCount = 1;
        final int requiredCatalystCount = catalystCount;

        if (units.size() != requiredCatalystCount + fusionIngredients.size()) return null;

        var unitKeys = new ArrayList<AEItemKey>(units.size());
        for (var unit : units) {
            unitKeys.add(unit.key());
        }

        var ingredientMatchers = new ArrayList<Predicate<AEItemKey>>(fusionIngredients.size());
        for (var fi : fusionIngredients) {
            ingredientMatchers.add(key -> fi.ingredient().test(key.toStack(1)));
        }

        var inputMatch = DraconicFusionInputMatcher.match(
                unitKeys,
                requiredCatalystCount,
                key -> catalyst.test(key.toStack(requiredCatalystCount)),
                ingredientMatchers);
        if (inputMatch == null) return null;

        var assignments = assignIngredientsToInjectors(
                inputMatch.ingredients(), injectors, recipeTierIndex);
        if (assignments == null) return null;

        var catalystKey = inputMatch.catalystKey();
        var catalystPlanned = new PlannedUnit(catalystKey, requiredCatalystCount);

        return new RecipeMatch(catalystPlanned, assignments, resultItem);
    }

    @Nullable
    private List<InjectorAssignment> assignIngredientsToInjectors(
            List<AEItemKey> ingredientKeys,
            List<InjectorInfo> injectors,
            int recipeTierIndex) {
        var available = selectAvailableInjectors(injectors, recipeTierIndex, ingredientKeys.size());
        if (available.isEmpty()) return null;

        var assignments = new ArrayList<InjectorAssignment>(ingredientKeys.size());
        var selected = available.orElseThrow();
        for (int i = 0; i < ingredientKeys.size(); i++) {
            assignments.add(new InjectorAssignment(
                    selected.get(i).pos(),
                    new PlannedUnit(ingredientKeys.get(i), 1)));
        }
        return assignments;
    }

    private static boolean hasAvailableInjectors(
            List<InjectorInfo> injectors,
            int recipeTierIndex,
            int requiredCount) {
        return selectAvailableInjectors(injectors, recipeTierIndex, requiredCount).isPresent();
    }

    private static Optional<List<InjectorInfo>> selectAvailableInjectors(
            List<InjectorInfo> injectors,
            int recipeTierIndex,
            int requiredCount) {
        var candidates = new ArrayList<DraconicFusionInjectorMatcher.Candidate<InjectorInfo>>();
        for (var inj : injectors) {
            candidates.add(new DraconicFusionInjectorMatcher.Candidate<>(
                    inj, inj.tierIndex(), inj.empty()));
        }
        return DraconicFusionInjectorMatcher.selectAvailableTargets(
                candidates, recipeTierIndex, requiredCount);
    }

    // ===== Inserters =====

    private static BiFunction<GenericStack, Actionable, Long> coreInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (stack.amount() != unit.amount() || !unit.key().equals(stack.what())) return 0L;
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(CRAFTING_CORE_BLOCK)) return 0L;
            if (DraconicFusionCraftingAccess.isCrafting(be)
                    || !DraconicFusionCraftingAccess.canStartCraft(be)
                    || !DraconicFusionCraftingAccess.canSetCatalystStack(be)) return 0L;
            var current = DraconicFusionCraftingAccess.getCatalystStack(be);
            if (current == null || !current.isEmpty()) return 0L;
            if (mode == Actionable.MODULATE) {
                DraconicFusionCraftingAccess.setCatalystStack(be, unit.stack());
            }
            return (long) unit.amount();
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> injectorInserter(
            ServerLevel level, BlockPos corePos, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (stack.amount() != 1 || !unit.key().equals(stack.what())) return 0L;
            var core = level.getBlockEntity(corePos);
            if (core == null || !blockId(core.getBlockState()).equals(CRAFTING_CORE_BLOCK)
                    || DraconicFusionCraftingAccess.isCrafting(core)) {
                return 0L;
            }
            var be = level.getBlockEntity(pos);
            if (be == null
                    || !DraconicFusionCraftingAccess.canStartCraft(core)
                    || !DraconicFusionCraftingAccess.canSetInjectorStack(core, be)) return 0L;
            var injStack = DraconicFusionCraftingAccess.getInjectorStack(be);
            if (injStack == null || !injStack.isEmpty()) return 0L;
            if (mode == Actionable.MODULATE) {
                DraconicFusionCraftingAccess.setInjectorStack(be, unit.stack());
            }
            return 1L;
        };
    }

    // ===== Helpers =====

    @Nullable
    private static List<PlannedUnit> expandInputUnits(KeyCounter[] inputs) {
        var units = new ArrayList<PlannedUnit>();
        long total = 0;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) return null;
                long amount = entry.getLongValue();
                if (amount <= 0 || amount > MAX_INPUT_UNITS - total) return null;
                total += amount;
                for (long i = 0; i < amount; i++) {
                    units.add(new PlannedUnit(itemKey, 1));
                }
            }
        }
        return units;
    }

    private static boolean validOutput(@Nullable ItemStack output) {
        return output != null && !output.isEmpty()
                && output.getCount() > 0 && output.getCount() <= MAX_OUTPUT_UNITS;
    }

    private static boolean canSatisfyInjectors(List<InjectorInfo> injectors,
                                                int tier,
                                                List<IngredientInfo> ingredients) {
        int required = 0;
        for (var ingredient : ingredients) {
            if (ingredient == null || ingredient.ingredient().isEmpty()
                    || !ingredient.consume()) {
                return false;
            }
            required++;
        }
        return selectAvailableInjectors(injectors, tier, required).isPresent();
    }

    private static ResourceLocation recipeId(Object recipe) {
        if (recipe instanceof Recipe<?> typed) {
            return typed.getId();
        }
        return new ResourceLocation(MOD_ID, "unknown");
    }

    private static void recoverPartialDispatch(ServerLevel level, BlockPos mainPos,
                                               List<TargetSlot> targets,
                                               List<AcceptedInsertion> accepted,
                                               Consumer<GenericStack> recovered) {
        var core = level.getBlockEntity(mainPos);
        if (core == null || !blockId(core.getBlockState()).equals(CRAFTING_CORE_BLOCK)
                || DraconicFusionCraftingAccess.isCrafting(core)) return;
        for (var insertion : accepted) {
            int index = targets.indexOf(insertion.target());
            if (index < 0 || insertion.stack().amount() > Integer.MAX_VALUE
                    || !(insertion.stack().what() instanceof AEItemKey key)) continue;
            var target = level.getBlockEntity(insertion.target().pos());
            if (target == null) continue;
            var expected = key.toStack((int) insertion.stack().amount());
            var current = index == 0
                    ? DraconicFusionCraftingAccess.getCatalystStack(target)
                    : DraconicFusionCraftingAccess.getInjectorStack(target);
            if (current == null || !ItemStack.isSameItemSameTags(current, expected)
                    || current.getCount() != expected.getCount()) continue;
            if (index == 0) {
                if (!DraconicFusionCraftingAccess.canSetCatalystStack(target)) continue;
                if (!DraconicFusionCraftingAccess.clearCatalystIfExact(target, expected, Actionable.SIMULATE)) continue;
                if (!DraconicFusionCraftingAccess.clearCatalystIfExact(target, expected, Actionable.MODULATE)) continue;
            } else {
                if (!DraconicFusionCraftingAccess.canSetInjectorStack(core, target)) continue;
                if (!DraconicFusionCraftingAccess.clearInjectorIfExact(target, expected, Actionable.SIMULATE)) continue;
                if (!DraconicFusionCraftingAccess.clearInjectorIfExact(target, expected, Actionable.MODULATE)) continue;
            }
            recovered.accept(insertion.stack());
        }
    }

    private static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.length == 1 && outputs[0].what() instanceof AEItemKey;
    }

    private static boolean outputMatches(IPatternDetails pattern, ItemStack result) {
        var outputs = pattern.getOutputs();
        if (outputs.length != 1) return false;
        var expected = outputs[0];
        if (!(expected.what() instanceof AEItemKey expectedKey) || expected.amount() != result.getCount()) {
            return false;
        }
        var actual = AEItemKey.of(result);
        return OverloadPatternSemantics.isIdOnlyOutput(pattern, 0)
                ? expectedKey.dropSecondary().equals(actual.dropSecondary())
                : expectedKey.equals(actual);
    }

    private static boolean isDeLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation blockId(BlockState state) { return AdapterBlocks.idOf(state); }

    private static ResourceLocation deId(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    // ===== Records =====

    private record PlannedUnit(AEItemKey key, int amount) {
        PlannedUnit {
            Objects.requireNonNull(key, "key");
        }

        ItemStack stack() {
            return key.toStack(amount);
        }

        GenericStack toGenericStack() {
            return new GenericStack(key, amount);
        }
    }

    record InjectorInfo(BlockPos pos, int tierIndex, boolean empty) {}

    private record InjectorAssignment(BlockPos injectorPos, PlannedUnit unit) {}

    record IngredientInfo(Ingredient ingredient, boolean consume) {}

    private record RecipeMatch(PlannedUnit catalyst, List<InjectorAssignment> injectorAssignments, ItemStack result) {}

    /** Opaque binding handle returned from {@link #bind}. */
    private record FusionBindHandle(Object recipe) {}

    // ===== DraconicFusionCraftingAccess =====

}
