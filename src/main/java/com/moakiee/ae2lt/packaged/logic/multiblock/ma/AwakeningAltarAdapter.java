package com.moakiee.ae2lt.packaged.logic.multiblock.ma;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

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
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.logic.multiblock.AcceptedInsertion;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterRecipeTypes;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterBlocks;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.SlotHarvestOwnership;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;

/**
 * Runtime adapter for Mystical Agriculture's Awakening Altar.
 * Pure reflection: MA is not a compile dependency.
 *
 * Layout: 1 altar + 8 sub-positions. Each sub-position must hold either an
 * AwakeningPedestal (item slot, capacity 1) or an EssenceVessel (essence slot,
 * capacity 40). The recipe requires exactly 4 pedestals + 4 vessels; their
 * physical placement among the 8 positions does not matter.
 */
public final class AwakeningAltarAdapter implements MultiblockAdapter {

    private static final String MOD_ID = "mysticalagriculture";
    private static final ResourceLocation ALTAR_BLOCK = maId("awakening_altar");
    private static final ResourceLocation PEDESTAL_BLOCK = maId("awakening_pedestal");
    private static final ResourceLocation VESSEL_BLOCK = maId("essence_vessel");
    private static final ResourceLocation RECIPE_TYPE_ID = maId("awakening");
    private static final String HARVEST_STATE = "ma_awakening_altar:harvest";
    private static final int VESSEL_CAPACITY = 40;
    private static final int MAX_INPUT_UNITS = 256;

    private static final BlockPos[] SUB_OFFSETS = {
            new BlockPos(-3, 0, 0),
            new BlockPos(2, 0, 2),
            new BlockPos(3, 0, 0),
            new BlockPos(-2, 0, -2),
            new BlockPos(0, 0, -3),
            new BlockPos(2, 0, -2),
            new BlockPos(0, 0, 3),
            new BlockPos(-2, 0, 2),
    };

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null
                && isMaLoaded()
                && AwakeningAltarAccess.isReady()
                && blockId(be.getBlockState()).equals(ALTAR_BLOCK)
                && AwakeningAltarAccess.isAwakeningAltar(be);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return com.moakiee.ae2lt.packaged.item.AdapterIds.MA_AWAKENING;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return null;
        }
        if (!hasSingleItemOutput(pattern)) {
            return null;
        }
        var recipes = findCandidateRecipes(level, pattern);
        if (recipes.isEmpty()) {
            return null;
        }
        return new BindingResult(new AwakeningBindHandle(recipes), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        return canDispatch(level, mainPos, handle, AdapterPersistentScope.NOOP);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle,
                               AdapterPersistentScope scope) {
        if (!(handle instanceof AwakeningBindHandle) || !AwakeningAltarAccess.isReady()) {
            return false;
        }
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return false;
        }
        if (AwakeningAltarAccess.getProgress(be) > 0) {
            return false;
        }
        var altarInv = AwakeningAltarAccess.getHandler(be);
        if (altarInv == null || altarInv.getSlots() < 2) {
            return false;
        }
        if (!altarInv.getStackInSlot(0).isEmpty() || !altarInv.getStackInSlot(1).isEmpty()) {
            return false;
        }
        var slots = scanSubSlots(level, mainPos);
        if (slots == null || slots.pedestals().size() != 4 || slots.vessels().size() != 4) {
            return false;
        }
        return !SlotHarvestOwnership.blocksDispatch(level, mainPos, scope, HARVEST_STATE, true);
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
        if (!(handle instanceof AwakeningBindHandle bind)
                || !canDispatch(level, mainPos, handle, scope)) {
            return null;
        }
        var slots = scanSubSlots(level, mainPos);
        if (slots == null || slots.pedestals().size() != 4 || slots.vessels().size() != 4) {
            return null;
        }

        var keyTotals = aggregateInputs(inputs);
        if (keyTotals == null || keyTotals.isEmpty()) {
            return null;
        }

        var selection = CandidateRecipeSelector.firstSelection(
                bind.recipes(), recipe -> assignInputsToRecipe(recipe, keyTotals));
        if (selection == null) {
            return null;
        }
        var match = selection.match();
        var expectedOutput = resultItem(selection.candidate(), level);
        var expectedRecipeId = recipeResourceId(selection.candidate());
        if (expectedOutput == null || expectedOutput.isEmpty() || expectedRecipeId == null) {
            return null;
        }

        var targets = new ArrayList<TargetSlot>(9);
        targets.add(new TargetSlot(
                level, mainPos, null,
                List.of(new GenericStack(match.altarKey(), 1)),
                InsertionStrategy.CUSTOM,
                altarInserter(level, mainPos, match.altarKey())));

        for (int i = 0; i < match.essenceAssignments().size(); i++) {
            var assignment = match.essenceAssignments().get(i);
            var vesselPos = slots.vessels().get(i);
            targets.add(new TargetSlot(
                    level, vesselPos, null,
                    List.of(new GenericStack(assignment.key(), assignment.count())),
                    InsertionStrategy.CUSTOM,
                    vesselInserter(level, vesselPos, assignment.key(), assignment.count())));
        }

        for (int i = 0; i < match.pedestalKeys().size(); i++) {
            var key = match.pedestalKeys().get(i);
            var pedestalPos = slots.pedestals().get(i);
            targets.add(new TargetSlot(
                    level, pedestalPos, null,
                    List.of(new GenericStack(key, 1)),
                    InsertionStrategy.CUSTOM,
                    pedestalInserter(level, pedestalPos, key)));
        }

        return new DispatchPlan(
                List.copyOf(targets),
                () -> {
                    AwakeningAltarAccess.activateOrThrow(level, mainPos);
                    SlotHarvestOwnership.store(level, mainPos, scope, HARVEST_STATE,
                            expectedOutput, expectedRecipeId);
                },
                (accepted, recovered) -> recoverUnstartedDispatch(
                        level, mainPos, targets, accepted, recovered));
    }

    private static void recoverUnstartedDispatch(
            ServerLevel level,
            BlockPos mainPos,
            List<TargetSlot> targets,
            List<AcceptedInsertion> acceptedInsertions,
            Consumer<GenericStack> recovered) {
        var altar = level.getBlockEntity(mainPos);
        if (altar == null || !AwakeningAltarAccess.isDefinitelyInactive(altar)) {
            return;
        }
        for (var accepted : acceptedInsertions) {
            int targetIndex = targets.indexOf(accepted.target());
            if (targetIndex < 0) {
                continue;
            }
            var be = level.getBlockEntity(accepted.target().pos());
            if (!isExpectedRecoveryTarget(be, targetIndex)) {
                continue;
            }
            var stack = recoverExactInsertion(
                    AwakeningAltarAccess.getHandler(be), 0, accepted.stack());
            if (stack != null) {
                recovered.accept(stack);
            }
        }
    }

    private static boolean isExpectedRecoveryTarget(@Nullable BlockEntity be, int targetIndex) {
        if (be == null) {
            return false;
        }
        var id = blockId(be.getBlockState());
        if (targetIndex == 0) {
            return id.equals(ALTAR_BLOCK) && AwakeningAltarAccess.isAwakeningAltar(be);
        }
        if (targetIndex <= 4) {
            return id.equals(VESSEL_BLOCK) && AwakeningAltarAccess.isEssenceVessel(be);
        }
        return targetIndex <= 8
                && id.equals(PEDESTAL_BLOCK)
                && AwakeningAltarAccess.isAwakeningPedestal(be);
    }

    @Nullable
    private static GenericStack recoverExactInsertion(
            @Nullable IItemHandler inventory, int slot, GenericStack accepted) {
        if (inventory == null
                || slot < 0
                || slot >= inventory.getSlots()
                || !(accepted.what() instanceof AEItemKey expectedKey)
                || accepted.amount() <= 0
                || accepted.amount() > Integer.MAX_VALUE) {
            return null;
        }
        var current = inventory.getStackInSlot(slot);
        var expected = expectedKey.toStack((int) accepted.amount());
        if (current.isEmpty()
                || current.getCount() != expected.getCount()
                || !ItemStack.isSameItemSameTags(current, expected)) {
            return null;
        }
        var simulated = inventory.extractItem(slot, current.getCount(), true);
        if (simulated.isEmpty()
                || simulated.getCount() != current.getCount()
                || !ItemStack.isSameItemSameTags(simulated, current)) {
            return null;
        }
        var extracted = inventory.extractItem(slot, current.getCount(), false);
        if (extracted.isEmpty() || !ItemStack.isSameItemSameTags(extracted, current)) {
            return null;
        }
        return new GenericStack(AEItemKey.of(extracted), extracted.getCount());
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
                || AwakeningAltarAccess.getProgress(be) > 0) {
            return List.of();
        }
        var ownership = SlotHarvestOwnership.load(level, mainPos, scope, HARVEST_STATE);
        if (ownership == null || SlotHarvestOwnership.expired(level, ownership)) {
            return List.of();
        }
        var handler = AwakeningAltarAccess.getHandler(be);
        if (handler == null || handler.getSlots() < 2) {
            return List.of();
        }
        var stack = handler.getStackInSlot(1);
        var expected = ownership.expectedOutput();
        if (stack.isEmpty() || stack.getCount() != expected.getCount()
                || !ItemStack.isSameItemSameTags(stack, expected)
                || !filter.matches(AEItemKey.of(stack))) {
            return List.of();
        }
        var simulated = handler.extractItem(1, expected.getCount(), true);
        if (simulated.getCount() != expected.getCount()
                || !ItemStack.isSameItemSameTags(simulated, expected)) {
            return List.of();
        }
        var extracted = handler.extractItem(1, expected.getCount(), false);
        if (extracted.getCount() != expected.getCount()
                || !ItemStack.isSameItemSameTags(extracted, expected)) {
            return List.of();
        }
        SlotHarvestOwnership.clear(level, mainPos, scope, HARVEST_STATE);
        return List.of(new GenericStack(AEItemKey.of(extracted), extracted.getCount()));
    }

    /** Bind-time recipe search retains every structurally valid output match. */
    private static List<Object> findCandidateRecipes(ServerLevel level, IPatternDetails pattern) {
        var candidates = new ArrayList<Object>();
        for (var recipe : recipes(level)) {
            var result = resultItem(recipe, level);
            if (result == null || result.isEmpty() || !outputMatches(pattern, result)) {
                continue;
            }
            candidates.add(recipe);
        }
        candidates.sort(java.util.Comparator.comparing(AwakeningAltarAdapter::recipeId));
        return List.copyOf(candidates);
    }

    /** Per-push input assignment chooses one compatible retained recipe. */
    @Nullable
    private static RecipeMatch assignInputsToRecipe(Object recipe, Map<AEItemKey, Long> keyTotals) {
        var essenceStacks = AwakeningAltarAccess.getEssences(recipe);
        var ingredients = AwakeningAltarAccess.getIngredients(recipe);
        if (essenceStacks == null || ingredients == null
                || essenceStacks.size() != 4 || ingredients.size() != 9) {
            return null;
        }
        var altarIngredient = ingredients.get(0);
        var pedestalIngredients = pedestalIngredients(ingredients);
        if (altarIngredient.isEmpty() || pedestalIngredients == null) {
            return null;
        }

        var altarKeys = new ArrayList<>(keyTotals.keySet());
        altarKeys.sort(java.util.Comparator.comparing(AwakeningAltarAdapter::keyOrder));
        for (var altarKey : altarKeys) {
            if (keyTotals.getOrDefault(altarKey, 0L) < 1
                    || !altarIngredient.test(altarKey.toStack())) {
                continue;
            }

            var working = new HashMap<>(keyTotals);
            if (!consume(working, altarKey, 1)) {
                continue;
            }

            var essenceAssignments = consumeEssences(working, essenceStacks);
            if (essenceAssignments == null) {
                continue;
            }

            var pedestalKeys = consumePedestals(working, pedestalIngredients);
            if (pedestalKeys == null || anyRemaining(working)) {
                continue;
            }

            return new RecipeMatch(altarKey, essenceAssignments, pedestalKeys);
        }
        return null;
    }

    @Nullable
    private static List<EssenceAssignment> consumeEssences(Map<AEItemKey, Long> working,
                                                           List<ItemStack> essenceStacks) {
        var assignments = new ArrayList<EssenceAssignment>(essenceStacks.size());
        for (var essence : essenceStacks) {
            if (essence.isEmpty()) {
                return null;
            }
            var key = AEItemKey.of(essence);
            int count = essence.getCount();
            if (count <= 0 || count > VESSEL_CAPACITY || !consume(working, key, count)) {
                return null;
            }
            assignments.add(new EssenceAssignment(key, count));
        }
        return List.copyOf(assignments);
    }

    @Nullable
    private static List<AEItemKey> consumePedestals(Map<AEItemKey, Long> working,
                                                    List<Ingredient> ingredients) {
        var units = new ArrayList<AEItemKey>();
        var keys = new ArrayList<>(working.keySet());
        keys.sort(java.util.Comparator.comparing(AwakeningAltarAdapter::keyOrder));
        for (var key : keys) {
            long count = working.getOrDefault(key, 0L);
            for (long i = 0; i < count; i++) {
                units.add(key);
            }
        }

        var constraints = new ArrayList<java.util.function.Predicate<AEItemKey>>(ingredients.size());
        for (var ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                return null;
            }
            constraints.add(key -> ingredient.test(key.toStack()));
        }

        var matched = ConstraintFirstMatcher.match(units, constraints);
        if (matched == null) {
            return null;
        }
        for (var key : matched) {
            if (!consume(working, key, 1)) {
                return null;
            }
        }
        return matched;
    }

    @Nullable
    private static List<Ingredient> pedestalIngredients(List<Ingredient> alternating) {
        // AwakeningRecipe#getIngredients() is [altar, essence, pedestal, ...].
        // The even indexes 2,4,6,8 are the four pedestal ingredients.
        if (alternating.size() != 9) {
            return null;
        }
        var result = new ArrayList<Ingredient>(4);
        for (int i = 2; i < alternating.size(); i += 2) {
            var ing = alternating.get(i);
            if (ing == null) {
                return null;
            }
            result.add(ing);
        }
        return result.size() == 4 ? result : null;
    }

    private static boolean consume(Map<AEItemKey, Long> working, AEItemKey key, int count) {
        var current = working.getOrDefault(key, 0L);
        if (current < count) {
            return false;
        }
        long next = current - count;
        if (next == 0) {
            working.remove(key);
        } else {
            working.put(key, next);
        }
        return true;
    }

    private static boolean anyRemaining(Map<AEItemKey, Long> working) {
        for (var v : working.values()) {
            if (v > 0) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static SubSlots scanSubSlots(ServerLevel level, BlockPos mainPos) {
        var pedestals = new ArrayList<BlockPos>(4);
        var vessels = new ArrayList<BlockPos>(4);
        for (var offset : SUB_OFFSETS) {
            var pos = mainPos.offset(offset.getX(), offset.getY(), offset.getZ());
            if (!level.isLoaded(pos)) {
                return null;
            }
            var be = level.getBlockEntity(pos);
            if (be == null) {
                return null;
            }
            var id = blockId(be.getBlockState());
            if (id.equals(PEDESTAL_BLOCK) && AwakeningAltarAccess.isAwakeningPedestal(be)) {
                var handler = AwakeningAltarAccess.getHandler(be);
                if (handler == null || handler.getSlots() < 1 || !handler.getStackInSlot(0).isEmpty()) {
                    return null;
                }
                if (pedestals.size() >= 4) {
                    return null;
                }
                pedestals.add(pos);
            } else if (id.equals(VESSEL_BLOCK) && AwakeningAltarAccess.isEssenceVessel(be)) {
                var handler = AwakeningAltarAccess.getHandler(be);
                if (handler == null || handler.getSlots() < 1 || !handler.getStackInSlot(0).isEmpty()) {
                    return null;
                }
                if (vessels.size() >= 4) {
                    return null;
                }
                vessels.add(pos);
            } else {
                return null;
            }
        }
        return new SubSlots(List.copyOf(pedestals), List.copyOf(vessels));
    }

    private static BiFunction<GenericStack, Actionable, Long> altarInserter(
            ServerLevel level, BlockPos pos, AEItemKey key) {
        return (stack, mode) -> {
            if (stack.amount() != 1 || !key.equals(stack.what())) {
                return 0L;
            }
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(ALTAR_BLOCK)
                    || !AwakeningAltarAccess.isAwakeningAltar(be)) {
                return 0L;
            }
            var handler = AwakeningAltarAccess.getHandler(be);
            if (!(handler instanceof IItemHandlerModifiable modifiable)
                    || modifiable.getSlots() < 2
                    || !modifiable.getStackInSlot(0).isEmpty()
                    || !modifiable.getStackInSlot(1).isEmpty()) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                modifiable.setStackInSlot(0, key.toStack(1));
            }
            return 1L;
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> pedestalInserter(
            ServerLevel level, BlockPos pos, AEItemKey key) {
        return (stack, mode) -> {
            if (stack.amount() != 1 || !key.equals(stack.what())) {
                return 0L;
            }
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(PEDESTAL_BLOCK)
                    || !AwakeningAltarAccess.isAwakeningPedestal(be)) {
                return 0L;
            }
            var handler = AwakeningAltarAccess.getHandler(be);
            if (!(handler instanceof IItemHandlerModifiable modifiable)
                    || modifiable.getSlots() < 1
                    || !modifiable.getStackInSlot(0).isEmpty()) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                modifiable.setStackInSlot(0, key.toStack(1));
            }
            return 1L;
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> vesselInserter(
            ServerLevel level, BlockPos pos, AEItemKey key, int count) {
        return (stack, mode) -> {
            if (stack.amount() != count || !key.equals(stack.what())) {
                return 0L;
            }
            if (count <= 0 || count > VESSEL_CAPACITY) {
                return 0L;
            }
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(VESSEL_BLOCK)
                    || !AwakeningAltarAccess.isEssenceVessel(be)) {
                return 0L;
            }
            var handler = AwakeningAltarAccess.getHandler(be);
            if (!(handler instanceof IItemHandlerModifiable modifiable)
                    || modifiable.getSlots() < 1
                    || !modifiable.getStackInSlot(0).isEmpty()) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                modifiable.setStackInSlot(0, key.toStack(count));
            }
            return (long) count;
        };
    }

    @Nullable
    private static Map<AEItemKey, Long> aggregateInputs(KeyCounter[] inputs) {
        var totals = new HashMap<AEItemKey, Long>();
        long grandTotal = 0;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    return null;
                }
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                grandTotal += amount;
                if (grandTotal > MAX_INPUT_UNITS) {
                    return null;
                }
                totals.merge(itemKey, amount, Long::sum);
            }
        }
        return totals;
    }

    private static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.length == 1 && outputs[0].what() instanceof AEItemKey;
    }

    private static boolean outputMatches(IPatternDetails pattern, ItemStack result) {
        var outputs = pattern.getOutputs();
        if (outputs.length != 1) {
            return false;
        }
        var expected = outputs[0];
        if (!(expected.what() instanceof AEItemKey expectedKey) || expected.amount() != result.getCount()) {
            return false;
        }
        var actual = AEItemKey.of(result);
        return OverloadPatternSemantics.isIdOnlyOutput(pattern, 0)
                ? expectedKey.dropSecondary().equals(actual.dropSecondary())
                : expectedKey.equals(actual);
    }

    @Nullable
    private static ItemStack resultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof net.minecraft.world.item.crafting.Recipe<?> r)) {
            return null;
        }
        try {
            return r.getResultItem(level.registryAccess());
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static ResourceLocation recipeResourceId(Object recipe) {
        if (!(recipe instanceof Recipe<?> r)) {
            return null;
        }
        try {
            return r.getId();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String recipeId(Object recipe) {
        var id = recipeResourceId(recipe);
        return id == null ? recipe.getClass().getName() : id.toString();
    }

    private static String keyOrder(AEItemKey key) {
        return key.getId() + "\u0000" + key;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Recipe<?>> recipes(ServerLevel level) {
        var type = AdapterRecipeTypes.find(RECIPE_TYPE_ID);
        if (type == null) {
            return List.of();
        }
        return (List<Recipe<?>>) (List<?>) level.getRecipeManager()
                .getAllRecipesFor((RecipeType) type);
    }

    private static boolean isMaLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation blockId(BlockState state) { return AdapterBlocks.idOf(state); }

    private static ResourceLocation maId(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private record EssenceAssignment(AEItemKey key, int count) {
        EssenceAssignment {
            Objects.requireNonNull(key, "key");
        }
    }

    private record RecipeMatch(AEItemKey altarKey,
                               List<EssenceAssignment> essenceAssignments,
                               List<AEItemKey> pedestalKeys) {
    }

    /** Opaque binding handle returned from {@link #bind}. */
    private record AwakeningBindHandle(List<Object> recipes) {
    }

    private record SubSlots(List<BlockPos> pedestals, List<BlockPos> vessels) {
    }

}
