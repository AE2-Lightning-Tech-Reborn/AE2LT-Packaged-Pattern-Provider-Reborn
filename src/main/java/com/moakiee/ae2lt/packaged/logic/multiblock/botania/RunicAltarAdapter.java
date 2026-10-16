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
 * Hybrid adapter for the Runic Altar &mdash; REAL dispatch (we feed
 * recipe ingredients + catalysts into the altar's actual inventory so
 * it accumulates mana from the player's mana spreaders), <i>virtual</i>
 * extract (once the altar's own state machine signals "ready", we
 * synthesise the output stack from the recipe and reset altar state
 * directly without waiting for the player to right-click with a wand).
 *
 * <p><b>Recipe shape:</b> Botania 1.20.x's {@code RunicAltarRecipe}
 * implements {@code RecipeWithReagent} only. Two logical input
 * groups:
 * <ol>
 *   <li><b>ingredients</b> ({@code getIngredients()}) &mdash; petals,
 *       dusts and any runes the recipe lists. Everything here goes
 *       into the altar inventory; after the craft the tile refunds
 *       every {@code RuneItem} stack (see {@code onUsedByWand}) and
 *       consumes the rest.</li>
 *   <li><b>reagent</b> ({@code getReagent()}, always livingrock)
 *       &mdash; tossed as a player-held item / on-altar
 *       {@code ItemEntity} to trigger the craft. <i>Never</i> placed
 *       in the altar inventory.</li>
 * </ol>
 * <p>Botania's {@code matches()} (via {@code RecipeUtils.matches})
 * fails if the inventory holds a stack no ingredient accepts, and it
 * stops scanning at the first empty slot. Dispatching the reagent
 * into a slot, or writing the inventory non-contiguously from slot
 * 0, would make {@code updateRecipe()} fail and the altar would sit
 * idle forever.
 *
 * <p><b>Reagent semantics in vanilla:</b> the player places the
 * ingredients + catalysts into the altar inventory (right-clicks =
 * {@code addItem} path B), waits for the surrounding spreaders to fill
 * the mana bar, then tosses a livingrock onto the altar (which sits as
 * an {@code ItemEntity} via {@code addItem} path A) and right-clicks
 * with the Wand of the Forest. The wand handler
 * ({@code onUsedByWand}) consumes one livingrock from the entity list,
 * assembles the output, drops it as an {@code ItemEntity} above the
 * altar and clears the inventory + resets state, restoring catalysts
 * via {@code getRemainingItems(input)}.
 *
 * <p><b>Adapter behaviour:</b>
 * <ul>
 *   <li><b>dispatch (REAL)</b>: writes
 *       {@code ingredients + catalysts} into the altar inventory slots
 *       (in that exact order). The reagent gets its own
 *       {@link TargetSlot} with a "void" inserter that debits one
 *       livingrock from the AE {@link KeyCounter} <i>without</i>
 *       placing it anywhere &mdash; this models the player consuming
 *       a livingrock to trigger the craft.</li>
 *   <li><b>extract (VIRTUAL)</b>: when the altar signals ready
 *       ({@code mana >= manaToGet}, with the recipe resolved from the
 *       recipe manager &mdash; 1.20.x's {@code currentRecipe} field
 *       is vestigial and never assigned), we assemble the output,
 *       snapshot the {@code RuneItem} stacks for refund (Botania's
 *       own refund rule), clear the inventory, zero the mana fields
 *       and drop {@code currentRecipe} back to null. The player sees
 *       the altar animate once but never needs to swing a wand.</li>
 * </ul>
 *
 * <p>Batch policy: K=1 only. The altar's internal state machine runs
 * one recipe at a time and the player-visible craft is a single cycle;
 * batching would require multiple sequential dispatches anyway.
 *
 * <p>Anti-duplication path:
 * <ul>
 *   <li>bind validates the pattern's declared output key against the
 *       recipe's real {@code getResultItem(...)} key (NBT-strict under
 *       STRICT, item-id-only under ID_ONLY) and that the pattern's
 *       declared inputs cover every recipe ingredient + catalyst +
 *       reagent &mdash; wrong-machine patterns refuse to bind so the
 *       CPU does not dead-lock on a plan that will never succeed.</li>
 *   <li>plan picks each ingredient + catalyst + reagent stack from
 *       the runtime {@link KeyCounter} via NBT-strict
 *       {@code ingredient.test} and hands the actual key.toStack(1)
 *       either to the altar inventory (ingredients + catalysts) or to
 *       the void inserter (reagent).</li>
 *   <li>Output stacks are {@code AEItemKey.of(...)} verbatim &mdash;
 *       the recipe's primary result plus any catalysts reflected back
 *       by {@code getRemainingItems(...)}.</li>
 * </ul>
 */
public final class RunicAltarAdapter implements MultiblockAdapter {

    /**
     * Namespaced flag key, stored on the provider via the
     * {@link AdapterPersistentScope} handed in by
     * {@link #planWithBinding(ServerLevel, BlockPos, IPatternDetails, KeyCounter[], Object, IActionSource, AdapterPersistentScope)}
     * / {@link #extractOutputs(ServerLevel, BlockPos, AllowedOutputFilter, IActionSource, AdapterPersistentScope)}.
     * Set when the dispatch successfully debits a livingrock from AE; cleared
     * by {@code extractOutputs} after it finishes harvesting the recipe.
     *
     * <p>We deliberately store this on the provider rather than on the altar
     * BE: Botania's {@code SimpleInventoryBlockEntity#saveAdditional} doesn't
     * call {@code super}, which silently drops NeoForge's {@code NeoForgeData}
     * + {@code attachments} blobs on disk save &mdash; any flag we tried to
     * stash on the altar would survive memory only, not the next world load.
     *
     * <p>Anchoring the flag on the provider also captures the right ownership:
     * "this craft was paid for by <i>us</i>" is a fact about the provider's
     * accounting, not about the altar; the altar by itself can't tell whether
     * its ingredients came from AE or a player's hand.
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
     * Per-ingredient existence check: each recipe ingredient (runes +
     * catalysts + reagent) must have at least one pattern input whose
     * item key passes {@code ingredient.test(key.toStack(1))}.
     * Quantities are enforced later at plan time.
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
        // Build the assignment in two parts:
        //   inventorySlotItems = ingredients ++ catalysts
        //     - go into the altar inventory slots (matches() requires
        //       input.size() == ingredients.size() + catalysts.size()).
        //   reagent (livingrock)
        //     - routed through a void inserter so AE deducts one copy
        //       from the network without ever placing it into the wrong
        //       inventory slot.
        // All three halves share the same KeyCounter pool so the strict
        // "no swallowing" invariant in pickKeyCounterStacks still holds.
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

        // Virtual extract: clear inventory + reset state instead of
        // waiting for the player to right-click. The altar's animation
        // had already played for the player to see the cycle.
        inv.clearContent();
        BotaniaReflection.altarResetState(be);
        be.setChanged();
        // Clear the dispatch flag now that this craft is fully harvested,
        // freeing the (provider, altarPos) slot in the scope so the next
        // dispatch's onCommit can flip it back on cleanly.
        clearHarvestState(level, mainPos, scope);
        // Force a BE update packet to the client so the rendered
        // inventory contents (the petals + runes hovering above the
        // altar) clear in the same tick the craft "finished".
        // setChanged() alone only marks the chunk dirty; client
        // rendering doesn't refresh until a sendBlockUpdated. Botania
        // itself does the same after addItem path B.
        var altarState = be.getBlockState();
        level.sendBlockUpdated(mainPos, altarState, altarState, 3);

        // Replay the audio/visual cues that Botania's own onUsedByWand
        // emits after finishing a craft:
        //   - BLOCK_ACTIVATE game event so vibration / Allay listeners
        //     pick up the moment of completion.
        //   - blockEvent(eventId=1, data=60): triggerEvent sets
        //     cooldown=60, matching the post-craft "rest" period before
        //     the altar can be used again. Without this, automation
        //     could re-dispatch into a 'still settling' altar.
        //   - blockEvent(eventId=2, data=0): triggerEvent dispatches
        //     25 sparkle particles + the runeAltarCraft subtitle to
        //     every client tracking this chunk. We deliberately delegate
        //     to Botania's existing client-side trigger so any future
        //     particle/sound retune in upstream Botania flows through
        //     unchanged.
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

    /**
     * Snapshots (as copies) every {@code RuneItem} stack currently sitting
     * in the altar inventory. Botania 1.20.x's {@code onUsedByWand} refunds
     * exactly those after a craft and consumes the rest, so this is the
     * refund rule the virtual extract has to honour &mdash; e.g. the
     * higher-tier runes that lower-tier rune recipes list as inputs flow
     * back to the AE network instead of being voided when we clear.
     */
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

    // allowsAutoReturn intentionally not overridden. Default {@code true}
    // applies: the altar is a closed BE and {@link #extractOutputs} only
    // emits stacks derived from the live recipe + getRemainingItems, both
    // of which are pinned by the bind-time validation.

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
     * "Void" inserter for the reagent slot: tells the dispatch
     * executor the slot accepted the full stack so AE debits the
     * livingrock from the network, but never actually writes it
     * anywhere. The altar's inventory therefore stays {@code matches}
     * against the recipe's {@code getIngredients()}, and the reagent
     * counts as "the player held it in their hand and consumed it" for
     * the virtual extract.
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
