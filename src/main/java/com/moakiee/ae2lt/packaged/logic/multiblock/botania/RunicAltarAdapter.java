package com.moakiee.ae2lt.packaged.logic.multiblock.botania;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.AcceptedInsertion;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.BoundedConstraintMatcher;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

/**
 * Dispatches one altar cycle and harvests it after Botania finishes accumulating mana.
 * Ingredients occupy contiguous slots from zero. The reagent is debited separately;
 * putting livingrock in the inventory would invalidate the recipe match.
 *
 * Harvest returns the recipe result and every RuneItem stack, then resets the altar.
 * Persisted provider ownership prevents harvesting manually supplied crafts.
 * Botania 1.20.1 leaves currentRecipe unset, so harvest resolves the live inventory.
 */
public final class RunicAltarAdapter implements MultiblockAdapter {

    /**
     * Provider-owned marker set after reagent payment and cleared after harvest.
     * Keeping it on the provider preserves payment ownership across world reloads.
     */
    private static final String REAGENT_DISPATCHED_FLAG = "botania_runic_altar:reagent_dispatched";
    private static final String HARVEST_STATE = "botania_runic_altar:harvest";

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
        return BotaniaReflection.isRunicAltar(be);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.BOTANIA_RUNIC_ALTAR;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (!recognizesMain(level, mainPos, be)) {
            return null;
        }
        // findRunicAltarByOutput validates the pattern outputs against
        // primary + every catalyst (each amount 1, K=1). It returns
        // null when the pattern misses a catalyst, declares an extra
        // entry, or amounts don't match &mdash; so no separate primary
        // amount check is needed here.
        var holder = BotaniaRecipeLookup.findRunicAltarByOutput(level, pattern);
        if (holder == null) {
            return null;
        }
        // Sanity gate: every recipe ingredient (runes + catalysts +
        // reagent) must be coverable by the pattern's inputs; otherwise
        // refuse to bind so the CPU never schedules a craft that plan
        // can't satisfy.
        if (!patternInputsCoverRecipe(pattern, holder)) {
            return null;
        }
        return new BindingResult(new RunicBindHandle(holder), BindingMode.REAL);
    }

    /**
     * Checks that every ingredient, catalyst and reagent has a matching pattern input.
     * Runtime planning validates quantities.
     */
    private static boolean patternInputsCoverRecipe(IPatternDetails pattern,
                                                    net.minecraft.world.item.crafting.Recipe<?> recipe) {
        var patternInputs = pattern.getInputs();
        if (patternInputs == null || patternInputs.length == 0) {
            return false;
        }
        var allIngredients = new ArrayList<Ingredient>(recipe.getIngredients());
        allIngredients.addAll(BotaniaReflection.recipeCatalysts(recipe));
        var reagent = BotaniaReflection.recipeReagent(recipe);
        if (reagent != null && !reagent.isEmpty()) {
            allIngredients.add(reagent);
        }
        for (var ing : allIngredients) {
            if (ing.isEmpty()) {
                continue;
            }
            boolean satisfied = false;
            for (var patternInput : patternInputs) {
                if (patternInput == null) {
                    continue;
                }
                var possible = patternInput.getPossibleInputs();
                if (possible == null) {
                    continue;
                }
                for (var stack : possible) {
                    if (!(stack.what() instanceof AEItemKey itemKey)) {
                        continue;
                    }
                    if (ing.test(itemKey.toStack(1))) {
                        satisfied = true;
                        break;
                    }
                }
                if (satisfied) {
                    break;
                }
            }
            if (!satisfied) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        return canDispatch(level, mainPos, handle, AdapterPersistentScope.NOOP);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle,
                               AdapterPersistentScope scope) {
        if (!(handle instanceof RunicBindHandle)) {
            return false;
        }
        var be = level.getBlockEntity(mainPos);
        if (!BotaniaReflection.isRunicAltar(be)) {
            return false;
        }
        // Idle = no current recipe + zero mana progress + empty inventory.
        if (BotaniaReflection.altarCurrentRecipe(be) != null) {
            return false;
        }
        if (BotaniaReflection.altarMana(be) != 0 || BotaniaReflection.altarManaToGet(be) != 0) {
            return false;
        }
        var inv = BotaniaReflection.simpleInventoryContainer(be);
        if (inv == null || !containerEmpty(inv)) {
            return false;
        }
        // A persisted harvest record, including malformed legacy data, is not
        // evidence that the empty altar can be reused. Only the old flag by
        // itself is stale and can be cleared after proving the altar is idle.
        if (scope.getState(mainPos, harvestStateKey(level)) != null
                || scope.getState(mainPos, HARVEST_STATE) != null) {
            return false;
        }
        scope.clearFlag(mainPos, REAGENT_DISPATCHED_FLAG);
        return true;
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        return planWithBinding(level, mainPos, pattern, inputs, handle, source, AdapterPersistentScope.NOOP);
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source,
                                        AdapterPersistentScope scope) {
        if (!(handle instanceof RunicBindHandle bind)
                || !canDispatch(level, mainPos, handle, scope)) {
            return null;
        }
        var be = level.getBlockEntity(mainPos);
        if (!BotaniaReflection.isRunicAltar(be)) {
            return null;
        }
        var inv = BotaniaReflection.simpleInventoryContainer(be);
        if (inv == null || !containerEmpty(inv)) {
            return null;
        }

        var recipe = bind.holder();
        // Match all groups against one input pool; only ingredients and
        // catalysts occupy the inventory. The reagent is debited separately.
        var ingredientsList = new ArrayList<Ingredient>(recipe.getIngredients());
        var catalystsList = BotaniaReflection.recipeCatalysts(recipe);
        var reagent = BotaniaReflection.recipeReagent(recipe);
        boolean hasReagent = reagent != null && !reagent.isEmpty();

        var inventorySlotIngredients = new ArrayList<Ingredient>(ingredientsList);
        inventorySlotIngredients.addAll(catalystsList);

        var allList = new ArrayList<Ingredient>(inventorySlotIngredients);
        if (hasReagent) {
            allList.add(reagent);
        }
        var assignment = pickKeyCounterStacks(allList, inputs);
        if (assignment == null || assignment.isEmpty()) {
            return null;
        }

        int nonEmptyInventoryCount = 0;
        for (var ing : inventorySlotIngredients) {
            if (!ing.isEmpty()) {
                nonEmptyInventoryCount++;
            }
        }
        int expectedAssignmentSize = nonEmptyInventoryCount + (hasReagent ? 1 : 0);
        if (assignment.size() != expectedAssignmentSize) {
            return null;
        }
        final int inventoryTargetCount = nonEmptyInventoryCount;
        if (nonEmptyInventoryCount > inv.getContainerSize()) {
            return null;
        }

        final var targets = new ArrayList<TargetSlot>(assignment.size());
        for (int i = 0; i < nonEmptyInventoryCount; i++) {
            var stack = assignment.get(i);
            int slot = i;
            targets.add(new TargetSlot(
                    level, mainPos, null,
                    List.of(new GenericStack(AEItemKey.of(stack), stack.getCount())),
                    InsertionStrategy.CUSTOM,
                    altarSlotInserter(level, mainPos, slot, stack)));
        }
        if (hasReagent) {
            var reagentStack = assignment.get(nonEmptyInventoryCount);
            targets.add(new TargetSlot(
                    level, mainPos, null,
                    List.of(new GenericStack(AEItemKey.of(reagentStack), reagentStack.getCount())),
                    InsertionStrategy.CUSTOM,
                    reagentVoidInserter(reagentStack)));
        }
        var expectedResult = recipe.getResultItem(level.registryAccess());
        if (expectedResult == null || expectedResult.isEmpty()) {
            return null;
        }
        var inventoryFingerprint = assignment.subList(0, nonEmptyInventoryCount).stream()
                .map(ItemStack::copy)
                .toList();
        return new DispatchPlan(List.copyOf(targets), () -> {
            var beAfter = level.getBlockEntity(mainPos);
            if (beAfter == null || !BotaniaReflection.isRunicAltar(beAfter)) {
                throw new IllegalStateException("Runic Altar disappeared before dispatch commit");
            }
            beAfter.setChanged();
            var state = beAfter.getBlockState();
            level.sendBlockUpdated(mainPos, state, state, 3);
            scope.setState(mainPos, harvestStateKey(level), new RunicHarvestState(
                    recipe.getId(), expectedResult, inventoryFingerprint).encode(level.dimension().location()));
            scope.clearState(mainPos, HARVEST_STATE);
            scope.setFlag(mainPos, REAGENT_DISPATCHED_FLAG);
        }, (accepted, recovered) -> recoverPartialDispatch(
                level, mainPos, targets, inventoryTargetCount, accepted, recovered));
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
        if (!BotaniaReflection.isRunicAltar(be)) {
            return List.of();
        }
        var inv = BotaniaReflection.simpleInventoryContainer(be);
        if (inv == null || containerEmpty(inv)) {
            clearHarvestState(level, mainPos, scope);
            return List.of();
        }
        var encodedState = scope.getState(mainPos, harvestStateKey(level));
        if (encodedState == null || encodedState.isBlank() || !scope.hasFlag(mainPos, REAGENT_DISPATCHED_FLAG)) {
            return List.of();
        }
        var ownership = RunicHarvestState.decode(encodedState, level.dimension().location());
        if (ownership == null || !ownership.matchesInventory(inv)) {
            return List.of();
        }
        var currentRecipe = BotaniaReflection.altarCurrentRecipe(be);
        if (currentRecipe == null) {
            currentRecipe = BotaniaRecipeLookup.findRunicAltarMatch(level, inv);
        }
        if (currentRecipe == null || !ownership.recipeId().equals(currentRecipe.getId())) {
            return List.of();
        }
        int mana = BotaniaReflection.altarMana(be);
        int manaToGet = BotaniaReflection.altarManaToGet(be);
        if (manaToGet <= 0 || mana < manaToGet) {
            return List.of();
        }

        ItemStack result;
        try {
            result = currentRecipe.getResultItem(level.registryAccess());
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
        if (result == null || result.isEmpty()
                || !ItemStack.isSameItemSameTags(result, ownership.expectedResult())
                || result.getCount() != ownership.expectedResult().getCount()) {
            return List.of();
        }
        var resultKey = AEItemKey.of(result);
        if (!filter.matches(resultKey)) {
            return List.of();
        }

        // Snapshot the rune refunds (copies) BEFORE clearing the inventory.
        // Botania 1.20.x does not model catalysts via getRemainingItems:
        // its own onUsedByWand hands back every RuneItem stack sitting in
        // the altar and consumes everything else, so replicating that rule
        // exactly is the item-conservation contract for the virtual extract.
        var catalystStacks = collectRuneRefunds(inv);

        inv.clearContent();
        BotaniaReflection.altarResetState(be);
        be.setChanged();
        clearHarvestState(level, mainPos, scope);
        // setChanged only persists; send an update to clear the rendered inventory.
        var altarState = be.getBlockState();
        level.sendBlockUpdated(mainPos, altarState, altarState, 3);

        // Botania's completion events notify listeners, impose the 60-tick cooldown,
        // and play the altar's own particles and sound.
        var altarBlock = altarState.getBlock();
        level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, mainPos);
        level.blockEvent(mainPos, altarBlock, 1, 60);
        level.blockEvent(mainPos, altarBlock, 2, 0);

        var outputs = new ArrayList<GenericStack>(1 + catalystStacks.size());
        outputs.add(new GenericStack(resultKey, result.getCount()));
        for (var stack : catalystStacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            outputs.add(new GenericStack(AEItemKey.of(stack), stack.getCount()));
        }
        return List.copyOf(outputs);
    }

    private static void recoverPartialDispatch(ServerLevel level, BlockPos mainPos,
                                               List<TargetSlot> targets, int inventoryTargetCount,
                                               List<AcceptedInsertion> accepted,
                                               Consumer<GenericStack> recovered) {
        var be = level.getBlockEntity(mainPos);
        if (!BotaniaReflection.isRunicAltar(be)
                || BotaniaReflection.altarMana(be) != 0
                || BotaniaReflection.altarManaToGet(be) != 0) {
            return;
        }
        var inv = BotaniaReflection.simpleInventoryContainer(be);
        if (inv == null) {
            return;
        }
        for (var insertion : accepted) {
            int targetIndex = targets.indexOf(insertion.target());
            if (targetIndex < 0) {
                continue;
            }
            if (targetIndex >= inventoryTargetCount) {
                recovered.accept(insertion.stack());
                continue;
            }
            var restored = recoverExactInventoryStack(inv, targetIndex, insertion.stack());
            if (restored != null) {
                recovered.accept(restored);
            }
        }
    }

    @Nullable
    private static GenericStack recoverExactInventoryStack(Container inventory, int slot,
                                                            GenericStack accepted) {
        if (!(accepted.what() instanceof AEItemKey key)
                || slot < 0 || slot >= inventory.getContainerSize()
                || accepted.amount() <= 0 || accepted.amount() > Integer.MAX_VALUE) {
            return null;
        }
        var current = inventory.getItem(slot);
        var expected = key.toStack((int) accepted.amount());
        if (current.isEmpty() || current.getCount() != expected.getCount()
                || !ItemStack.isSameItemSameTags(current, expected)) {
            return null;
        }
        inventory.setItem(slot, ItemStack.EMPTY);
        if (!inventory.getItem(slot).isEmpty()) {
            return null;
        }
        return new GenericStack(key, accepted.amount());
    }

    private static String harvestStateKey(ServerLevel level) {
        return HARVEST_STATE + "@" + level.dimension().location();
    }

    private static void clearHarvestState(ServerLevel level, BlockPos mainPos,
                                          AdapterPersistentScope scope) {
        scope.clearState(mainPos, harvestStateKey(level));
        scope.clearState(mainPos, HARVEST_STATE);
        scope.clearFlag(mainPos, REAGENT_DISPATCHED_FLAG);
    }

    /** Copies every RuneItem stack for refund, matching Botania 1.20.1's wand handler. */
    private static List<ItemStack> collectRuneRefunds(Container inv) {
        var refunds = new ArrayList<ItemStack>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (!stack.isEmpty() && BotaniaReflection.isRuneItem(stack.getItem())) {
                refunds.add(stack.copy());
            }
        }
        return refunds;
    }

    /**
     * Assigns every runtime input exactly once to a non-empty ingredient with
     * NBT-strict matching. Constraint-first bounded backtracking avoids a
     * broad early ingredient consuming the only item for a later narrow one.
     */
    @Nullable
    private static List<ItemStack> pickKeyCounterStacks(List<Ingredient> ingredients, KeyCounter[] inputs) {
        var units = new ArrayList<AEItemKey>();
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    return null;
                }
                long amount = entry.getLongValue();
                if (amount < 0 || amount > 64 - units.size()) {
                    return null;
                }
                for (long i = 0; i < amount; i++) {
                    units.add(itemKey);
                }
            }
        }
        var constraints = new ArrayList<java.util.function.Predicate<AEItemKey>>();
        for (var ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                constraints.add(key -> ingredient.test(key.toStack(1)));
            }
        }
        var assigned = BoundedConstraintMatcher.matchExactly(units, constraints);
        if (assigned == null) {
            return null;
        }
        return assigned.stream().map(key -> key.toStack(1)).toList();
    }

    private java.util.function.BiFunction<GenericStack, Actionable, Long> altarSlotInserter(
            ServerLevel level, BlockPos mainPos, int slot, ItemStack stack) {
        return (target, actionable) -> {
            if (!(target.what() instanceof AEItemKey key)
                    || !ItemStack.isSameItemSameTags(key.toStack(stack.getCount()), stack)
                    || target.amount() != stack.getCount()) {
                return 0L;
            }
            var be = level.getBlockEntity(mainPos);
            if (!BotaniaReflection.isRunicAltar(be)) {
                return 0L;
            }
            var inv = BotaniaReflection.simpleInventoryContainer(be);
            if (inv == null || slot < 0 || slot >= inv.getContainerSize()
                    || !inv.getItem(slot).isEmpty()) {
                return 0L;
            }
            if (actionable.isSimulate()) {
                return target.amount();
            }
            inv.setItem(slot, stack.copy());
            return ItemStack.isSameItemSameTags(inv.getItem(slot), stack)
                    && inv.getItem(slot).getCount() == stack.getCount()
                    ? target.amount() : 0L;
        };
    }

    /**
     * Debits the reagent without adding it to the altar inventory,
     * where it would prevent the ingredients from matching the recipe.
     */
    private java.util.function.BiFunction<GenericStack, Actionable, Long> reagentVoidInserter(
            ItemStack expected) {
        return (target, actionable) -> {
            if (!(target.what() instanceof AEItemKey key)
                    || target.amount() != expected.getCount()
                    || !ItemStack.isSameItemSameTags(
                            key.toStack(expected.getCount()), expected)) {
                return 0L;
            }
            return target.amount();
        };
    }

    private static boolean containerEmpty(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private record RunicHarvestState(ResourceLocation recipeId, ItemStack expectedResult,
                                     List<ItemStack> inventoryFingerprint) {
        private RunicHarvestState {
            expectedResult = expectedResult.copy();
            inventoryFingerprint = inventoryFingerprint.stream().map(ItemStack::copy).toList();
        }

        private String encode(ResourceLocation dimension) {
            var encoded = new StringBuilder(dimension.toString())
                    .append('|').append(recipeId)
                    .append('|').append(encodeStack(expectedResult));
            for (var stack : inventoryFingerprint) {
                encoded.append('|').append(encodeStack(stack));
            }
            return encoded.toString();
        }

        @Nullable
        private static RunicHarvestState decode(String encoded, ResourceLocation dimension) {
            var parts = encoded.split("\\|", -1);
            if (parts.length < 4 || !dimension.toString().equals(parts[0])) {
                return null;
            }
            try {
                var recipeId = ResourceLocation.tryParse(parts[1]);
                var expected = decodeStack(parts[2]);
                if (recipeId == null || expected.isEmpty()) {
                    return null;
                }
                var fingerprint = new ArrayList<ItemStack>(parts.length - 3);
                for (int i = 3; i < parts.length; i++) {
                    var stack = decodeStack(parts[i]);
                    if (stack.isEmpty()) {
                        return null;
                    }
                    fingerprint.add(stack);
                }
                return new RunicHarvestState(recipeId, expected, fingerprint);
            } catch (Exception ignored) {
                return null;
            }
        }

        private boolean matchesInventory(Container inventory) {
            if (inventoryFingerprint.size() > inventory.getContainerSize()) {
                return false;
            }
            for (int i = 0; i < inventoryFingerprint.size(); i++) {
                var expected = inventoryFingerprint.get(i);
                var current = inventory.getItem(i);
                if (current.getCount() != expected.getCount()
                        || !ItemStack.isSameItemSameTags(current, expected)) {
                    return false;
                }
            }
            for (int i = inventoryFingerprint.size(); i < inventory.getContainerSize(); i++) {
                if (!inventory.getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private static String encodeStack(ItemStack stack) {
            var tag = new CompoundTag();
            stack.save(tag);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    tag.toString().getBytes(StandardCharsets.UTF_8));
        }

        private static ItemStack decodeStack(String encoded) throws Exception {
            var text = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            return ItemStack.of(TagParser.parseTag(text));
        }
    }

    private record RunicBindHandle(Recipe<?> holder) {
    }
}
