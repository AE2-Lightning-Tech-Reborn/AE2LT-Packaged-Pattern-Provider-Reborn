package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.AcceptedInsertion;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterRecipeTypes;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterBlocks;
import com.moakiee.ae2lt.packaged.logic.multiblock.BoundedConstraintMatcher;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.SlotHarvestOwnership;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

/**
 * Runtime adapter for ExtendedCrafting's Combination Crafter — a central
 * {@code crafting_core} surrounded by up to 48 {@code pedestal} blocks
 * within a 7×7 horizontal AOE (same Y level as the core). Pure reflection.
 *
 * <p>The Core stores a catalyst in slot 0 and a {@link
 * net.minecraftforge.energy.EnergyStorage}-backed FE buffer. Pedestals
 * each hold one ingredient. Once enough power has accumulated, the BE
 * consumes the catalyst + pedestal stacks and writes the result back into
 * Core slot 0.
 *
 * <p>Binding mode is {@link BindingMode#REAL} — there's no equivalent
 * virtual evaluation since the recipe demands physical pedestals + tick
 * progress. We deliberately do not gate dispatch on FE balance; external
 * sources can keep refilling the Core while the BE waits.
 */
public final class ExtendedCraftingCombinationAdapter implements MultiblockAdapter {

    private static final Logger LOG = LoggerFactory.getLogger("ae2ltpp/ec-combination");

    private static final String MOD_ID = "extendedcrafting";
    private static final ResourceLocation CORE_BLOCK = ecId("crafting_core");
    private static final ResourceLocation PEDESTAL_BLOCK = ecId("pedestal");
    private static final ResourceLocation RECIPE_TYPE_ID = ecId("combination");
    private static final int CORE_SLOT = 0;
    private static final int PEDESTAL_SLOT = 0;
    private static final int AOE_RADIUS = 3;
    private static final int MAX_INGREDIENTS = 48;
    private static final String HARVEST_STATE = "ec_combination:harvest";
    private static final String RUN_OBSERVED_FLAG = "ec_combination:run_observed";
    private static volatile boolean recipeTypeDiagnosticLogged;

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null && isEcLoaded() && blockId(be.getBlockState()).equals(CORE_BLOCK);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.EC_COMBINATION;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) return null;
        if (!hasSingleItemOutput(pattern)) return null;

        var recipe = findCandidateRecipe(level, pattern);
        if (recipe == null) return null;
        return new BindingResult(new CombinationBindHandle(recipe), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        return canDispatch(level, mainPos, handle, AdapterPersistentScope.NOOP);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle,
                               AdapterPersistentScope scope) {
        if (!(handle instanceof CombinationBindHandle bind)) return false;
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) return false;

        var inv = ExtendedCraftingCombinationAccess.getInventory(be);
        if (inv == null || inv.getSlots() <= CORE_SLOT) return false;
        if (!inv.getStackInSlot(CORE_SLOT).isEmpty()) return false;
        if (ExtendedCraftingCombinationAccess.getProgress(be) != 0) return false;

        var ingredients = ExtendedCraftingCombinationAccess.getRecipeIngredients(bind.recipe());
        if (ingredients == null) return false;
        int needed = ingredients.size();
        if (needed <= 0 || needed > MAX_INGREDIENTS) return false;
        var pedestals = findEmptyPedestals(level, mainPos);
        if (pedestals == null || pedestals.size() < needed) return false;
        if (SlotHarvestOwnership.blocksDispatch(level, mainPos, scope, HARVEST_STATE, true)) {
            return false;
        }
        scope.clearFlag(mainPos, runObservedFlagKey(level));
        return true;
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
        if (!(handle instanceof CombinationBindHandle bind)
                || !canDispatch(level, mainPos, handle, scope)) return null;
        var be = level.getBlockEntity(mainPos);
        if (be == null) return null;
        var catalyst = ExtendedCraftingCombinationAccess.getCatalyst(bind.recipe());
        var ingredients = ExtendedCraftingCombinationAccess.getRecipeIngredients(bind.recipe());
        var expectedOutput = ExtendedCraftingCombinationAccess.getResultItem(bind.recipe(), level);
        if (catalyst == null || ingredients == null || expectedOutput == null || expectedOutput.isEmpty()) return null;
        var match = matchInputs(catalyst, ingredients, inputs);
        if (match == null) return null;
        var emptyPedestals = findEmptyPedestals(level, mainPos);
        if (emptyPedestals == null || emptyPedestals.size() < ingredients.size()) return null;

        var targets = new ArrayList<TargetSlot>(ingredients.size() + 1);
        targets.add(new TargetSlot(
                level, mainPos, null,
                List.of(match.catalyst().toGenericStack()),
                InsertionStrategy.CUSTOM,
                coreInserter(level, mainPos, match.catalyst())));
        for (int i = 0; i < match.ingredients().size(); i++) {
            var unit = match.ingredients().get(i);
            var pedestalPos = emptyPedestals.get(i);
            targets.add(new TargetSlot(
                    level, pedestalPos, null,
                    List.of(unit.toGenericStack()),
                    InsertionStrategy.CUSTOM,
                    pedestalInserter(level, pedestalPos, unit)));
        }
        return new DispatchPlan(
                List.copyOf(targets),
                () -> {
                    scope.clearFlag(mainPos, runObservedFlagKey(level));
                    SlotHarvestOwnership.store(level, mainPos, scope, HARVEST_STATE,
                            expectedOutput, recipeId(bind.recipe()));
                    ExtendedCraftingCombinationAccess.refreshRecipe(be);
                },
                (accepted, recovered) -> recoverPartialDispatch(
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
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return List.of();
        }
        var ownership = SlotHarvestOwnership.load(level, mainPos, scope, HARVEST_STATE);
        if (ownership == null || SlotHarvestOwnership.expired(level, ownership)) return List.of();
        var inv = ExtendedCraftingCombinationAccess.getInventory(be);
        if (inv == null || inv.getSlots() <= CORE_SLOT) return List.of();
        var stack = inv.getStackInSlot(CORE_SLOT);
        if (stack.isEmpty()) return List.of();
        var key = AEItemKey.of(stack);
        if (!filter.matches(key)) return List.of();
        int expectedCount = ownership.expectedOutput().getCount();
        int attributable = ownership.attributableCount(stack);
        if (attributable != expectedCount || !(inv instanceof IItemHandlerModifiable modifiable)) {
            return List.of();
        }
        // The center slot holds inputs as well as products. Consult the live
        // recipe even when progress is zero, including before the first tick.
        if (!ExtendedCraftingCombinationAccess.isIdleWithoutRecipe(be)) return List.of();
        var extracted = stack.copy();
        extracted.setCount(expectedCount);
        if (stack.getCount() == expectedCount) {
            modifiable.setStackInSlot(CORE_SLOT, ItemStack.EMPTY);
        } else {
            var remainder = stack.copy();
            remainder.shrink(expectedCount);
            modifiable.setStackInSlot(CORE_SLOT, remainder);
        }
        be.setChanged();
        SlotHarvestOwnership.clear(level, mainPos, scope, HARVEST_STATE);
        scope.clearFlag(mainPos, runObservedFlagKey(level));
        return List.of(new GenericStack(AEItemKey.of(extracted), extracted.getCount()));
    }

    // ===== Recipe matching =====

    @Nullable
    private static Object findCandidateRecipe(ServerLevel level, IPatternDetails pattern) {
        for (var recipe : recipes(level)) {
            var result = ExtendedCraftingCombinationAccess.getResultItem(recipe, level);
            if (result == null || result.isEmpty()) continue;
            if (!outputMatches(pattern, result)) continue;
            return recipe;
        }
        return null;
    }

    /** Assigns catalyst and pedestal inputs together to avoid first-fit conflicts. */
    @Nullable
    private static InputMatch matchInputs(Ingredient catalyst, List<Ingredient> ingredients,
                                          KeyCounter[] inputs) {
        var units = expandUnits(inputs, 1 + ingredients.size());
        if (units == null || units.size() != 1 + ingredients.size()) return null;
        var constraints = new ArrayList<java.util.function.Predicate<PlannedUnit>>(1 + ingredients.size());
        constraints.add(unit -> catalyst.test(unit.stack()));
        for (var ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                return null;
            }
            constraints.add(unit -> ingredient.test(unit.stack()));
        }
        var assigned = BoundedConstraintMatcher.matchExactly(units, constraints);
        if (assigned == null || assigned.size() != constraints.size()) {
            return null;
        }
        return new InputMatch(assigned.get(0), List.copyOf(assigned.subList(1, assigned.size())));
    }

    // ===== Inserters =====

    private static BiFunction<GenericStack, Actionable, Long> coreInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (stack.amount() != 1 || !unit.key().equals(stack.what())) return 0L;
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(CORE_BLOCK)) return 0L;
            if (ExtendedCraftingCombinationAccess.getProgress(be) != 0) return 0L;
            var inv = ExtendedCraftingCombinationAccess.getInventory(be);
            if (!(inv instanceof IItemHandlerModifiable modifiable)) return 0L;
            if (modifiable.getSlots() <= CORE_SLOT) return 0L;
            if (!modifiable.getStackInSlot(CORE_SLOT).isEmpty()) return 0L;
            if (mode == Actionable.MODULATE) {
                modifiable.setStackInSlot(CORE_SLOT, unit.stack());
                be.setChanged();
            }
            return 1L;
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> pedestalInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (stack.amount() != 1 || !unit.key().equals(stack.what())) return 0L;
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(PEDESTAL_BLOCK)) return 0L;
            var inv = ExtendedCraftingCombinationAccess.getInventory(be);
            if (!(inv instanceof IItemHandlerModifiable modifiable)) return 0L;
            if (modifiable.getSlots() <= PEDESTAL_SLOT) return 0L;
            if (!modifiable.getStackInSlot(PEDESTAL_SLOT).isEmpty()) return 0L;
            if (mode == Actionable.MODULATE) {
                modifiable.setStackInSlot(PEDESTAL_SLOT, unit.stack());
                be.setChanged();
            }
            return 1L;
        };
    }

    private static void recoverPartialDispatch(ServerLevel level, BlockPos mainPos,
                                               List<TargetSlot> targets,
                                               List<AcceptedInsertion> accepted,
                                               Consumer<GenericStack> recovered) {
        var core = level.getBlockEntity(mainPos);
        if (core == null || !blockId(core.getBlockState()).equals(CORE_BLOCK)
                || ExtendedCraftingCombinationAccess.getProgress(core) != 0) return;
        for (var insertion : accepted) {
            int targetIndex = targets.indexOf(insertion.target());
            if (targetIndex < 0 || !(insertion.stack().what() instanceof AEItemKey key)
                    || insertion.stack().amount() != 1) continue;
            var targetBe = level.getBlockEntity(insertion.target().pos());
            if (targetBe == null) continue;
            ResourceLocation expectedBlock = targetIndex == 0 ? CORE_BLOCK : PEDESTAL_BLOCK;
            if (!blockId(targetBe.getBlockState()).equals(expectedBlock)) continue;
            var inv = ExtendedCraftingCombinationAccess.getInventory(targetBe);
            if (inv == null || inv.getSlots() <= 0) continue;
            var expected = key.toStack(1);
            var current = inv.getStackInSlot(0);
            if (current.getCount() != 1 || !ItemStack.isSameItemSameTags(current, expected)) continue;
            var simulated = inv.extractItem(0, 1, true);
            if (simulated.getCount() != 1 || !ItemStack.isSameItemSameTags(simulated, expected)) continue;
            var extracted = inv.extractItem(0, 1, false);
            if (extracted.getCount() != 1 || !ItemStack.isSameItemSameTags(extracted, expected)) continue;
            targetBe.setChanged();
            recovered.accept(insertion.stack());
        }
    }

    // ===== World scanning =====

    /**
     * Walks the {@code [-3,0,-3]..[+3,0,+3]} AOE in X→Z order (same iteration
     * as {@code BlockPos.betweenClosedStream}) collecting empty pedestals.
     * The Core's own ingredient scan uses identical order, so the i-th
     * assigned ingredient maps cleanly to the i-th BE-side pedestal at
     * completion. Occupied or unreadable pedestal areas return null so leftover
     * inputs cannot be mixed with another recipe.
     */
    @Nullable
    private static List<BlockPos> findEmptyPedestals(ServerLevel level, BlockPos mainPos) {
        var result = new ArrayList<BlockPos>();
        var min = mainPos.offset(-AOE_RADIUS, 0, -AOE_RADIUS);
        var max = mainPos.offset(AOE_RADIUS, 0, AOE_RADIUS);
        var cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                cursor.set(x, mainPos.getY(), z);
                if (cursor.equals(mainPos)) continue;
                if (!level.isLoaded(cursor)) return null;
                if (!blockId(level.getBlockState(cursor)).equals(PEDESTAL_BLOCK)) continue;
                var be = level.getBlockEntity(cursor);
                if (be == null) return null;
                var inv = ExtendedCraftingCombinationAccess.getInventory(be);
                if (inv == null || inv.getSlots() <= PEDESTAL_SLOT) return null;
                if (!inv.getStackInSlot(PEDESTAL_SLOT).isEmpty()) return null;
                result.add(cursor.immutable());
            }
        }
        return result;
    }

    // ===== Helpers =====

    @Nullable
    private static List<PlannedUnit> expandUnits(KeyCounter[] inputs, int cap) {
        var units = new ArrayList<PlannedUnit>();
        long total = 0;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) return null;
                long amount = entry.getLongValue();
                if (amount <= 0) continue;
                total += amount;
                if (total > cap) return null;
                for (long i = 0; i < amount; i++) {
                    units.add(new PlannedUnit(itemKey));
                }
            }
        }
        return units;
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Recipe<?>> recipes(ServerLevel level) {
        var type = AdapterRecipeTypes.find(RECIPE_TYPE_ID);
        if (type == null) {
            if (!recipeTypeDiagnosticLogged) {
                recipeTypeDiagnosticLogged = true;
                LOG.warn("Combination recipe type {} is not registered; binding is unavailable", RECIPE_TYPE_ID);
            }
            return List.of();
        }
        return (List<Recipe<?>>) (List<?>) level.getRecipeManager()
                .getAllRecipesFor((RecipeType) type);
    }

    private static boolean isEcLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation blockId(BlockState state) { return AdapterBlocks.idOf(state); }

    private static ResourceLocation ecId(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private static ResourceLocation recipeId(Object recipe) {
        return recipe instanceof Recipe<?> r ? r.getId() : ecId("unknown");
    }

    private static String runObservedFlagKey(ServerLevel level) {
        return RUN_OBSERVED_FLAG + "@" + level.dimension().location();
    }

    // ===== Records =====

    private record PlannedUnit(AEItemKey key) {
        PlannedUnit {
            Objects.requireNonNull(key, "key");
        }

        ItemStack stack() {
            return key.toStack(1);
        }

        GenericStack toGenericStack() {
            return new GenericStack(key, 1);
        }
    }

    private record InputMatch(PlannedUnit catalyst, List<PlannedUnit> ingredients) {}

    private record CombinationBindHandle(Object recipe) {}

    // ===== Reflection =====

}
