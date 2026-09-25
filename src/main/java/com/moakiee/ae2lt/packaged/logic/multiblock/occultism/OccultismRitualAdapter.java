package com.moakiee.ae2lt.packaged.logic.multiblock.occultism;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.AcceptedInsertion;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterRecipeTypes;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.PatternInputAlternatives;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
/**
 * Runtime adapter for Occultism rituals.
 *
 * <p>Occultism is intentionally optional, so all Occultism and Modonomicon access
 * stays reflection-based.
 *
 * <p>All Occultism rituals run as {@link BindingMode#REAL}. Even summon/use-item
 * rituals have visible time and world-side side effects; this adapter only
 * automates item placement plus optional proxy costs for sacrifice/use steps.
 */
public final class OccultismRitualAdapter implements MultiblockAdapter {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("ae2ltpp/occultism-ritual");

    private static final String MOD_ID = "occultism";
    private static final String RITUAL_DISPATCHED_FLAG =
            "occultism_ritual:dispatch_owned";
    private static final String RITUAL_HARVEST_STATE =
            "occultism_ritual:harvest_state";
    private static final long STALE_STATE_AGE = 20L * 60L;
    private static final Set<String> STALE_STATE_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final ResourceLocation RITUAL_RECIPE_TYPE = occultismId("ritual");
    private static final int MAX_INPUT_UNITS = 128;

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null && isOccultismLoaded() && OccultismRitualAccess.isGoldenBowl(be);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return com.moakiee.ae2lt.packaged.item.AdapterIds.OCCULTISM_RITUAL;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            LOG.debug("bind: skipped at {} (be={}, recognized={})",
                    mainPos, be == null ? "null" : be.getClass().getSimpleName(),
                    be != null && recognizesMain(level, mainPos, be));
            return null;
        }
        var candidate = findCandidateRecipe(level, mainPos, pattern);
        if (candidate == null) {
            LOG.debug("bind: no ritual recipe matched pattern inputs at {}", mainPos);
            return null;
        }
        LOG.debug("bind: matched ritual at {} -> {}", mainPos, candidate.recipe().getClass().getSimpleName());
        return new BindingResult(candidate, BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle,
                               AdapterPersistentScope scope) {
        tickPending(level, mainPos, scope);
        var stateKey = harvestStateKey(level);
        var encoded = scope.getState(mainPos, stateKey);
        var legacyState = scope.getState(mainPos, RITUAL_HARVEST_STATE);
        if ((encoded != null && !encoded.isBlank())
                || (legacyState != null && !legacyState.isBlank())
                || scope.hasFlag(mainPos, dispatchedFlagKey(level))
                || scope.hasFlag(mainPos, RITUAL_DISPATCHED_FLAG)) {
            var state = OccultismHarvestState.decode(
                    encoded, level.dimension().location());
            if (state == null) {
                LOG.warn("Retaining unreadable Occultism dispatch state instead of allowing overwrite: "
                                + "dimension={} pos={}",
                        level.dimension().location(), mainPos);
            } else {
                logStaleState(level, mainPos, state);
            }
            return false;
        }
        return canDispatch(level, mainPos, handle);
    }

    @Override
    public boolean supportsPatternIndependentHarvest() {
        return true;
    }

    @Override
    public void tickPending(ServerLevel level, BlockPos mainPos, AdapterPersistentScope scope) {
        var state = OccultismHarvestState.decode(
                scope.getState(mainPos, harvestStateKey(level)), level.dimension().location());
        if (state == null || (state.autoHarvest() && !state.complete())
                || scope.getState(mainPos, RITUAL_HARVEST_STATE) != null
                || scope.hasFlag(mainPos, RITUAL_DISPATCHED_FLAG)) {
            return;
        }
        var be = level.getBlockEntity(mainPos);
        if (be != null && recognizesMain(level, mainPos, be) && OccultismRitualAccess.isIdle(be)) {
            // OFF jobs own no products. AUTO jobs may retire only once their debt is paid.
            clearHarvestState(level, mainPos, scope);
        }
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        if (!(handle instanceof OccultismBindHandle bind)) {
            LOG.debug("canDispatch: handle wrong type at {}", mainPos);
            return false;
        }
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            LOG.debug("canDispatch: not recognized at {}", mainPos);
            return false;
        }
        if (!OccultismRitualAccess.isIdle(be)) {
            LOG.debug("canDispatch: golden bowl busy at {}", mainPos);
            return false;
        }
        var goldenHandler = OccultismRitualAccess.itemHandler(be);
        if (goldenHandler == null || !slotEmpty(goldenHandler)) {
            LOG.debug("canDispatch: golden bowl slot not empty at {}", mainPos);
            return false;
        }
        if (!OccultismRitualAccess.hasValidPentacle(bind.recipe(), level, mainPos)) {
            LOG.debug("canDispatch: pentacle invalid for {} at {}", bind.recipe().getClass().getSimpleName(), mainPos);
            return false;
        }
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
        if (!(handle instanceof OccultismBindHandle bind)
                || !canDispatch(level, mainPos, handle, scope)) {
            return null;
        }
        boolean autoHarvest = scope.getReturnMode() == ReturnMode.AUTO;

        var units = expandInputUnits(inputs);
        if (units == null || units.isEmpty()) {
            LOG.debug("planWithBinding: expandInputUnits null/empty at {}", mainPos);
            return null;
        }

        var match = matchInputsToRecipe(units, bind.recipe());
        if (match == null) {
            LOG.debug("planWithBinding: runtime inputs ({} units) didn't match bound recipe at {}",
                    units.size(), mainPos);
            return null;
        }

        var bowls = findEmptySacrificialBowls(level, mainPos, bind.recipe());
        if (bowls == null || bowls.size() < match.ingredients().size()) {
            LOG.debug("planWithBinding: not enough empty sacrificial bowls at {} (need {}, found {})",
                    mainPos, match.ingredients().size(), bowls == null ? -1 : bowls.size());
            return null;
        }

        var expectedOutputs = autoHarvest ? patternItemOutputs(pattern) : List.<ItemStack>of();
        if (expectedOutputs == null || !(bind.recipe() instanceof Recipe<?> recipe)) {
            LOG.debug("planWithBinding: unsupported output or recipe type at {}", mainPos);
            return null;
        }
        var preexistingEntities = autoHarvest ? captureEntityCounts(
                level, ritualOutputAabb(mainPos), expectedOutputs) : Map.<UUID, Integer>of();
        var bowlBaselines = autoHarvest ? captureOutputBowls(level, mainPos) : Map.<BlockPos, ItemStack>of();
        if (bowlBaselines == null) {
            return null;
        }
        var outputBowls = bowlBaselines.keySet().stream()
                .sorted(java.util.Comparator.comparingLong(BlockPos::asLong)).toList();
        var targets = new ArrayList<TargetSlot>(match.ingredients().size() + 1 + match.proxyCosts().size());
        for (int i = 0; i < match.ingredients().size(); i++) {
            var unit = match.ingredients().get(i);
            var bowl = bowls.get(i);
            targets.add(new TargetSlot(
                    level,
                    bowl.pos(),
                    null,
                    List.of(unit.toGenericStack()),
                    InsertionStrategy.CUSTOM,
                    sacrificialBowlInserter(level, bowl.pos(), unit)));
        }

        // The completesSacrifice / completesItemUse flags must be set on the
        // GOLDEN bowl (sacrificeProvided / itemUseProvided live there, not on
        // peripheral sacrificial bowls). They also must be applied *after*
        // GoldenSacrificialBowlBlockEntity#startRitual runs (it gets invoked
        // synchronously by insertItem on the activation slot, and it resets
        // both flags to false). So we plumb the requirement through to the
        // golden-bowl inserter and let it flip the flags immediately after
        // the activation item is committed.
        targets.add(new TargetSlot(
                level,
                mainPos,
                null,
                List.of(match.activation().toGenericStack()),
                InsertionStrategy.CUSTOM,
                goldenBowlInserter(level, mainPos, bind.recipe(), match.activation(),
                        match.completesSacrifice(), match.completesItemUse())));
        if (!match.proxyCosts().isEmpty()) {
            targets.add(new TargetSlot(
                    level,
                    mainPos,
                    null,
                    match.proxyCosts().stream().map(PlannedUnit::toGenericStack).toList(),
                    InsertionStrategy.CUSTOM,
                    proxyCostConsumer(level, mainPos, match.proxyCosts(),
                            match.completesSacrifice(), match.completesItemUse())));
        }

        return new DispatchPlan(
                List.copyOf(targets),
                () -> {
                    var state = new OccultismHarvestState(
                            UUID.randomUUID(), level.dimension().location(),
                            recipe.getId(), level.getGameTime(), expectedOutputs,
                            preexistingEntities, outputBowls, bowlBaselines, autoHarvest);
                    scope.setState(mainPos, harvestStateKey(level), state.encode());
                    scope.setFlag(mainPos, dispatchedFlagKey(level));
                    scope.clearState(mainPos, RITUAL_HARVEST_STATE);
                    scope.clearFlag(mainPos, RITUAL_DISPATCHED_FLAG);
                },
                (accepted, recovered) -> recoverPartialDispatch(
                        level, mainPos, scope, targets, match, accepted, recovered));
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
        if (scope.getReturnMode() != ReturnMode.AUTO) {
            return List.of();
        }
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be) || !OccultismRitualAccess.isIdle(be)) {
            return List.of();
        }

        var stateKey = harvestStateKey(level);
        var encoded = scope.getState(mainPos, stateKey);
        var legacyState = scope.getState(mainPos, RITUAL_HARVEST_STATE);
        if (encoded == null || encoded.isBlank()) {
            if ((legacyState != null && !legacyState.isBlank())
                    || scope.hasFlag(mainPos, dispatchedFlagKey(level))
                    || scope.hasFlag(mainPos, RITUAL_DISPATCHED_FLAG)) {
                LOG.warn("Retaining missing or legacy Occultism dispatch state instead of extracting "
                                + "unowned outputs: dimension={} pos={}",
                        level.dimension().location(), mainPos);
            }
            return List.of();
        }

        var state = OccultismHarvestState.decode(encoded, level.dimension().location());
        if (state == null) {
            LOG.warn("Retaining unreadable Occultism dispatch state instead of extracting "
                            + "unowned outputs: dimension={} pos={}",
                    level.dimension().location(), mainPos);
            return List.of();
        }
        if ((legacyState != null && !legacyState.isBlank())
                || scope.hasFlag(mainPos, RITUAL_DISPATCHED_FLAG)) {
            return List.of();
        }
        logStaleState(level, mainPos, state);
        if (!state.autoHarvest() || state.complete()) {
            clearHarvestState(level, mainPos, scope);
            return List.of();
        }

        var outputs = new ArrayList<GenericStack>();
        for (var bowlPos : state.outputBowls()) {
            if (!level.isLoaded(bowlPos)) {
                continue;
            }
            var bowlBe = level.getBlockEntity(bowlPos);
            if (bowlBe == null
                    || !OccultismRitualAccess.isSacrificialBowl(bowlBe)
                    || !isUpsideDownBowl(bowlBe.getBlockState())) {
                continue;
            }
            var handler = OccultismRitualAccess.itemHandler(bowlBe);
            if (handler == null || handler.getSlots() <= 0) {
                continue;
            }
            var current = handler.getStackInSlot(0).copy();
            int claimable = state.claimableFromBowl(bowlPos, current);
            if (claimable <= 0) {
                continue;
            }
            var simulated = handler.extractItem(0, claimable, true);
            if (simulated.isEmpty() || !ItemStack.isSameItemSameTags(current, simulated)) {
                continue;
            }
            int expectedCount = Math.min(simulated.getCount(), claimable);
            var extracted = handler.extractItem(0, expectedCount, false);
            if (extracted.isEmpty()
                    || extracted.getCount() != expectedCount
                    || !ItemStack.isSameItemSameTags(current, extracted)) {
                if (!extracted.isEmpty()) {
                    var remainder = handler.insertItem(0, extracted, false);
                    if (!remainder.isEmpty()) {
                        LOG.error("Could not restore unexpected item extracted from an Occultism output bowl: "
                                        + "dimension={} pos={} remainder={}",
                                level.dimension().location(), bowlPos, remainder);
                    }
                }
                continue;
            }
            outputs.add(new GenericStack(AEItemKey.of(extracted), extracted.getCount()));
            state = state.consume(extracted, extracted.getCount());
            scope.setState(mainPos, stateKey, state.encode());
        }

        for (var entity : level.getEntitiesOfClass(ItemEntity.class, ritualOutputAabb(mainPos))) {
            if (!entity.isAlive()) {
                continue;
            }
            var current = entity.getItem();
            int attributable = OccultismHarvestState.attributableCount(current.getCount(),
                    state.preexistingEntityCounts().getOrDefault(entity.getUUID(), 0));
            int claimable = state.claimable(current, attributable);
            if (claimable <= 0) {
                continue;
            }
            var extracted = current.copy();
            extracted.setCount(claimable);
            if (claimable == current.getCount()) {
                entity.discard();
            } else {
                var remainder = current.copy();
                remainder.shrink(claimable);
                entity.setItem(remainder);
            }
            outputs.add(new GenericStack(AEItemKey.of(extracted), claimable));
            state = state.consume(extracted, claimable);
            scope.setState(mainPos, stateKey, state.encode());
        }

        if (state.complete()) {
            clearHarvestState(level, mainPos, scope);
        }
        return List.copyOf(outputs);
    }

    @Nullable
    private static List<ItemStack> patternItemOutputs(IPatternDetails pattern) {
        var outputs = new ArrayList<ItemStack>();
        for (var output : pattern.getOutputs()) {
            if (!(output.what() instanceof AEItemKey key)) {
                continue;
            }
            if (output.amount() <= 0 || output.amount() > Integer.MAX_VALUE) {
                return null;
            }
            outputs.add(key.toStack((int) output.amount()));
        }
        return List.copyOf(outputs);
    }

    private static Map<UUID, Integer> captureEntityCounts(ServerLevel level, AABB bounds,
                                                           List<ItemStack> expectedOutputs) {
        var counts = new java.util.HashMap<UUID, Integer>();
        for (var entity : level.getEntitiesOfClass(ItemEntity.class, bounds)) {
            var current = entity.getItem();
            if (entity.isAlive() && containsSameStack(expectedOutputs, current)) {
                counts.put(entity.getUUID(), current.getCount());
            }
        }
        return Map.copyOf(counts);
    }

    @Nullable
    private static Map<BlockPos, ItemStack> captureOutputBowls(ServerLevel level, BlockPos mainPos) {
        var bowls = new java.util.HashMap<BlockPos, ItemStack>();
        for (int y = 1; y <= 3; y++) {
            var bowlPos = mainPos.above(y);
            if (!level.isLoaded(bowlPos)) {
                return null;
            }
            var bowlBe = level.getBlockEntity(bowlPos);
            if (bowlBe != null
                    && OccultismRitualAccess.isSacrificialBowl(bowlBe)
                    && isUpsideDownBowl(bowlBe.getBlockState())) {
                var handler = OccultismRitualAccess.itemHandler(bowlBe);
                if (handler == null || handler.getSlots() <= 0) {
                    return null;
                }
                bowls.put(bowlPos.immutable(), handler.getStackInSlot(0).copy());
            }
        }
        return Map.copyOf(bowls);
    }

    private static boolean containsSameStack(List<ItemStack> expected, ItemStack candidate) {
        for (var stack : expected) {
            if (ItemStack.isSameItemSameTags(stack, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String harvestStateKey(ServerLevel level) {
        return RITUAL_HARVEST_STATE + "@" + level.dimension().location();
    }

    private static String dispatchedFlagKey(ServerLevel level) {
        return RITUAL_DISPATCHED_FLAG + "@" + level.dimension().location();
    }

    private static void logStaleState(ServerLevel level, BlockPos mainPos,
                                      OccultismHarvestState state) {
        long age = level.getGameTime() - state.createdTick();
        if (age < 0 || age <= STALE_STATE_AGE) {
            return;
        }
        var key = level.dimension().location() + "@" + mainPos.asLong()
                + "@" + state.dispatchId();
        if (STALE_STATE_LOGGED.add(key)) {
            LOG.warn("Retaining stale Occultism dispatch state instead of allowing overwrite: "
                            + "dimension={} pos={} recipe={} createdTick={} age={}",
                    level.dimension().location(), mainPos, state.recipeId(),
                    state.createdTick(), age);
        }
    }

    private static void clearHarvestState(ServerLevel level, BlockPos mainPos,
                                          AdapterPersistentScope scope) {
        scope.clearState(mainPos, harvestStateKey(level));
        scope.clearState(mainPos, RITUAL_HARVEST_STATE);
        scope.clearFlag(mainPos, dispatchedFlagKey(level));
        scope.clearFlag(mainPos, RITUAL_DISPATCHED_FLAG);
        STALE_STATE_LOGGED.removeIf(key -> key.startsWith(
                level.dimension().location() + "@" + mainPos.asLong() + "@"));
    }

    private static void recoverPartialDispatch(ServerLevel level, BlockPos mainPos,
                                               AdapterPersistentScope scope,
                                               List<TargetSlot> targets, InputMatch match,
                                               List<AcceptedInsertion> acceptedInsertions,
                                               Consumer<GenericStack> recovered) {
        var goldenBowl = level.getBlockEntity(mainPos);
        if (goldenBowl == null || !OccultismRitualAccess.isGoldenBowl(goldenBowl)
                || !OccultismRitualAccess.isIdle(goldenBowl)) {
            return;
        }
        int recoveredCount = 0;
        for (var accepted : acceptedInsertions) {
            int targetIndex = targets.indexOf(accepted.target());
            if (targetIndex < 0) {
                continue;
            }
            GenericStack stack = null;
            if (targetIndex < match.ingredients().size()) {
                var bowlBe = level.getBlockEntity(targets.get(targetIndex).pos());
                IItemHandler handler = bowlBe == null ? null : OccultismRitualAccess.itemHandler(bowlBe);
                stack = recoverExactInsertion(handler, accepted.stack());
            } else if (targetIndex == match.ingredients().size()) {
                stack = recoverExactInsertion(OccultismRitualAccess.itemHandler(goldenBowl),
                        accepted.stack());
            } else {
                stack = accepted.stack();
            }
            if (stack != null) {
                recovered.accept(stack);
                if (stack.amount() == accepted.stack().amount()) {
                    recoveredCount++;
                }
            }
        }
        if (recoveredCount == acceptedInsertions.size()) {
            clearHarvestState(level, mainPos, scope);
        }
    }

    @Nullable
    static GenericStack recoverExactInsertion(@Nullable IItemHandler handler,
                                              GenericStack accepted) {
        if (handler == null || handler.getSlots() <= 0
                || !(accepted.what() instanceof AEItemKey key)
                || accepted.amount() <= 0 || accepted.amount() > Integer.MAX_VALUE) {
            return null;
        }
        var current = handler.getStackInSlot(0);
        var expected = key.toStack((int) accepted.amount());
        if (current.isEmpty()
                || current.getCount() != expected.getCount()
                || !ItemStack.isSameItemSameTags(current, expected)) {
            return null;
        }
        var simulated = handler.extractItem(0, current.getCount(), true);
        if (simulated.isEmpty()
                || simulated.getCount() != current.getCount()
                || !ItemStack.isSameItemSameTags(simulated, current)) {
            return null;
        }
        var extracted = handler.extractItem(0, current.getCount(), false);
        if (extracted.isEmpty() || !ItemStack.isSameItemSameTags(extracted, current)) {
            return null;
        }
        return new GenericStack(AEItemKey.of(extracted), extracted.getCount());
    }

    private static AABB ritualOutputAabb(BlockPos pos) {
        return new AABB(
                pos.getX() - 1.5,
                pos.getY() - 0.5,
                pos.getZ() - 1.5,
                pos.getX() + 2.5,
                pos.getY() + 3.5,
                pos.getZ() + 2.5);
    }

    /**
     * Recipe search executed during {@link #bind}. Ritual outputs cover too
     * many side-effect categories (summons, possessions, dummy placeholders,
     * commands), so we don't compare outputs at all here &mdash; the pattern's
     * input shape is the canonical disambiguator.
     */
    @Nullable
    private static OccultismBindHandle findCandidateRecipe(ServerLevel level, BlockPos mainPos,
                                                            IPatternDetails pattern) {
        var patternAlternatives = patternInputUnitAlternatives(pattern);
        if (patternAlternatives == null || patternAlternatives.isEmpty()) {
            LOG.debug("findCandidate: pattern input alternatives unavailable at {} (pattern={})",
                    mainPos, pattern.getDefinition());
            return null;
        }
        if (LOG.isDebugEnabled()) {
            var alternatives = new StringBuilder();
            for (var units : patternAlternatives) {
                if (alternatives.length() > 0) alternatives.append(" | ");
                for (int i = 0; i < units.size(); i++) {
                    if (i > 0) alternatives.append(", ");
                    alternatives.append(units.get(i).key().getId());
                }
            }
            LOG.debug("findCandidate: pattern input alternatives at {} expandedUnits=[{}]",
                    mainPos, alternatives);
        }
        int scanned = 0;
        int pentacleFail = 0;
        int missingApi = 0;
        int inputMismatch = 0;
        int mismatchLogged = 0;
        for (var recipe : recipes(level)) {
            scanned++;
            var activation = OccultismRitualAccess.getActivationItem(recipe);
            var ingredients = OccultismRitualAccess.getIngredients(recipe);
            if (activation == null || ingredients == null) {
                missingApi++;
                if (mismatchLogged < 5 && LOG.isDebugEnabled()) {
                    LOG.debug("findCandidate: recipe {} skipped (activationResolved={}, ingredientsResolved={})",
                            recipe.getId(), activation != null, ingredients != null);
                    mismatchLogged++;
                }
                continue;
            }
            boolean matchedAnyAlternative = false;
            for (var patternUnits : patternAlternatives) {
                if (matchInputsToRecipe(patternUnits, recipe, activation, ingredients) != null) {
                    matchedAnyAlternative = true;
                    break;
                }
            }
            if (!matchedAnyAlternative) {
                inputMismatch++;
                if (mismatchLogged < 5 && LOG.isDebugEnabled()) {
                    // Guard the debug branch: ingredientFirstItems / ingredientListItems
                    // call BuiltInRegistries.ITEM.getKey per ItemStack (allocates a
                    // ResourceLocation and validates the path), which is wasteful
                    // when debug logging is off.
                    LOG.debug("findCandidate: recipe {} mismatch (activation={}, ingredients={}, requiresSacrifice={}, requiresItemUse={}, itemToUse={})",
                            recipe.getId(),
                            ingredientFirstItems(activation),
                            ingredientListItems(ingredients),
                            OccultismRitualAccess.requiresSacrifice(recipe),
                            OccultismRitualAccess.requiresItemUse(recipe),
                            ingredientFirstItems(OccultismRitualAccess.getItemToUse(recipe)));
                    mismatchLogged++;
                }
                continue;
            }
            // Pentacle last: it is an in-world structure check, so only the
            // input-matched recipe needs it. (Checking it first rejected all 70
            // recipes whenever the player's built pentacle belonged to a
            // different ritual, and hid input mismatches behind pentacleFail.)
            if (!OccultismRitualAccess.hasValidPentacle(recipe, level, mainPos)) {
                pentacleFail++;
                if (mismatchLogged < 5 && LOG.isDebugEnabled()) {
                    LOG.debug("findCandidate: recipe {} input-matched but pentacle invalid at {}",
                            recipe.getId(), mainPos);
                    mismatchLogged++;
                }
                continue;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("findCandidate: matched recipe {} at {} ({} input alternatives)",
                        recipe.getId(), mainPos, patternAlternatives.size());
            }
            return new OccultismBindHandle(recipe);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("findCandidate: no match at {} (scanned={}, pentacleFail={}, missingApi={}, inputMismatch={}, inputAlternatives={})",
                    mainPos, scanned, pentacleFail, missingApi, inputMismatch, patternAlternatives.size());
        }
        return null;
    }

    private static String ingredientFirstItems(@Nullable Ingredient ingredient) {
        if (ingredient == null) {
            return "<null>";
        }
        try {
            var items = ingredient.getItems();
            if (items.length == 0) {
                return "<empty>";
            }
            var sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, items.length); i++) {
                if (sb.length() > 0) sb.append("|");
                sb.append(BuiltInRegistries.ITEM.getKey(items[i].getItem()));
            }
            if (items.length > 3) sb.append("...");
            return sb.toString();
        } catch (RuntimeException | LinkageError ignored) {
            return "<error>";
        }
    }

    private static String ingredientListItems(List<Ingredient> ingredients) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < ingredients.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(ingredientFirstItems(ingredients.get(i)));
        }
        return sb.append("]").toString();
    }

    /**
     * Per-push input matching: pairs runtime inputs with the bound recipe's
     * activation + ingredient ingredients. Cheap because the recipe is already
     * locked in.
     */
    @Nullable
    private static InputMatch matchInputsToRecipe(List<PlannedUnit> units, Object recipe) {
        var activation = OccultismRitualAccess.getActivationItem(recipe);
        var ingredients = OccultismRitualAccess.getIngredients(recipe);
        if (activation == null || ingredients == null) {
            return null;
        }
        return matchInputsToRecipe(units, recipe, activation, ingredients);
    }

    @Nullable
    private static InputMatch matchInputsToRecipe(List<PlannedUnit> units, Object recipe,
                                                  Ingredient activation,
                                                  List<Ingredient> ingredients) {
        for (int i = 0; i < units.size(); i++) {
            if (!activation.test(units.get(i).stack())) {
                continue;
            }
            var match = matchInputsWithActivation(units, i, ingredients, recipe);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    @Nullable
    private static InputMatch matchInputsWithActivation(List<PlannedUnit> units,
                                                        int activationIndex,
                                                        List<Ingredient> ingredients,
                                                        Object recipe) {
        var used = new boolean[units.size()];
        var matchedActivation = units.get(activationIndex);
        used[activationIndex] = true;

        var matchedIngredients = new ArrayList<PlannedUnit>(ingredients.size());
        // Bounded backtracking: a broad early ingredient must not consume the
        // only unit a later narrow ingredient needs. Budget exhaustion fails
        // closed instead of falling back to greedy first-fit.
        if (!assignIngredientsBounded(units, used, ingredients, 0,
                matchedIngredients, new SearchBudget())) {
            return null;
        }

        boolean completesSacrifice = false;
        boolean completesItemUse = false;
        var proxyCosts = new ArrayList<PlannedUnit>(2);
        var itemToUse = OccultismRitualAccess.getItemToUse(recipe);
        boolean requiresSacrifice = OccultismRitualAccess.requiresSacrifice(recipe);
        var sacrificeTag = requiresSacrifice
                ? OccultismRitualAccess.getEntityToSacrifice(recipe)
                : null;
        if (requiresSacrifice && sacrificeTag == null) {
            return null;
        }
        boolean requiresItemUse = itemToUse != null && OccultismRitualAccess.requiresItemUse(recipe);
        for (int i = 0; i < units.size(); i++) {
            if (used[i]) {
                continue;
            }
            var unit = units.get(i);
            if (!completesSacrifice && requiresSacrifice
                    && isMatchingSacrificeEgg(unit.stack(), sacrificeTag)) {
                completesSacrifice = true;
                used[i] = true;
                proxyCosts.add(unit);
                continue;
            }
            if (!completesItemUse && requiresItemUse && itemToUse.test(unit.stack())) {
                completesItemUse = true;
                used[i] = true;
                proxyCosts.add(unit);
                continue;
            }
            return null;
        }

        return new InputMatch(matchedActivation, List.copyOf(matchedIngredients),
                completesSacrifice, completesItemUse, List.copyOf(proxyCosts));
    }

    private static boolean assignIngredientsBounded(List<PlannedUnit> units,
                                                    boolean[] used,
                                                    List<Ingredient> ingredients,
                                                    int ingredientIndex,
                                                    List<PlannedUnit> matched,
                                                    SearchBudget budget) {
        if (ingredientIndex == ingredients.size()) {
            return true;
        }
        if (!budget.tryNode()) {
            return false;
        }
        var ingredient = ingredients.get(ingredientIndex);
        for (int i = 0; i < units.size(); i++) {
            if (used[i] || !ingredient.test(units.get(i).stack())) {
                continue;
            }
            used[i] = true;
            matched.add(units.get(i));
            if (assignIngredientsBounded(units, used, ingredients, ingredientIndex + 1,
                    matched, budget)) {
                return true;
            }
            matched.remove(matched.size() - 1);
            used[i] = false;
        }
        return false;
    }

    private static final class SearchBudget {
        private static final int MAX_SEARCH_NODES = 4096;

        private int remaining = MAX_SEARCH_NODES;

        boolean tryNode() {
            if (remaining == 0) {
                return false;
            }
            remaining--;
            return true;
        }
    }

    @Nullable
    private static List<BowlSlot> findEmptySacrificialBowls(ServerLevel level, BlockPos mainPos, Object recipe) {
        var bowls = OccultismRitualAccess.getSacrificialBowls(recipe, level, mainPos);
        if (bowls == null) {
            return null;
        }

        var result = new ArrayList<BowlSlot>();
        for (var bowl : bowls) {
            if (!(bowl instanceof BlockEntity bowlBe)) {
                return null;
            }
            if (bowlBe.getBlockPos().equals(mainPos) || isUpsideDownBowl(bowlBe.getBlockState())) {
                continue;
            }

            var handler = OccultismRitualAccess.itemHandler(bowlBe);
            if (handler == null || handler.getSlots() <= 0) {
                return null;
            }
            if (!handler.getStackInSlot(0).isEmpty()) {
                return null;
            }
            result.add(new BowlSlot(bowlBe.getBlockPos()));
        }
        return result;
    }

    private static BiFunction<GenericStack, Actionable, Long> sacrificialBowlInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (!matchesPlannedStack(stack, unit)) {
                return 0L;
            }

            var be = level.getBlockEntity(pos);
            if (be == null
                    || !OccultismRitualAccess.isSacrificialBowl(be)
                    || isUpsideDownBowl(be.getBlockState())) {
                return 0L;
            }

            var handler = OccultismRitualAccess.itemHandler(be);
            if (handler == null || handler.getSlots() <= 0 || !handler.getStackInSlot(0).isEmpty()) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                var remainder = handler.insertItem(0, unit.stack(), false);
                var accepted = 1L - remainder.getCount();
                if (accepted > 0) {
                    be.setChanged();
                    level.updateNeighborsAt(pos, be.getBlockState().getBlock());
                }
                return accepted;
            }
            return 1L;
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> goldenBowlInserter(
            ServerLevel level, BlockPos pos, Object recipe, PlannedUnit unit,
            boolean completesSacrifice, boolean completesItemUse) {
        return (stack, mode) -> {
            if (!matchesPlannedStack(stack, unit)) {
                return 0L;
            }

            var be = level.getBlockEntity(pos);
            if (be == null
                    || !OccultismRitualAccess.isGoldenBowl(be)
                    || !OccultismRitualAccess.isIdle(be)
                    || !OccultismRitualAccess.hasValidPentacle(recipe, level, pos)) {
                return 0L;
            }

            var handler = OccultismRitualAccess.itemHandler(be);
            if (handler == null || handler.getSlots() <= 0 || !handler.getStackInSlot(0).isEmpty()) {
                return 0L;
            }

            if (!OccultismRitualAccess.canCompleteDeferredRequirements(
                    be, completesSacrifice, completesItemUse)) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                var remainder = handler.insertItem(0, unit.stack(), false);
                var accepted = 1L - remainder.getCount();
                if (accepted > 0 && !OccultismRitualAccess.completeDeferredRequirements(
                        be, completesSacrifice, completesItemUse)) {
                    throw new IllegalStateException("Could not verify Occultism proxy requirement state");
                }
                if (accepted > 0) {
                    be.setChanged();
                }
                return accepted;
            }
            return 1L;
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> proxyCostConsumer(
            ServerLevel level, BlockPos mainPos, List<PlannedUnit> units,
            boolean completesSacrifice, boolean completesItemUse) {
        return (stack, mode) -> {
            for (var unit : units) {
                if (!matchesPlannedStack(stack, unit)) {
                    continue;
                }
                var bowl = level.getBlockEntity(mainPos);
                if (bowl == null || (mode == Actionable.MODULATE
                        && !OccultismRitualAccess.deferredRequirementsComplete(
                        bowl, completesSacrifice, completesItemUse))) {
                    return 0L;
                }
                return 1L;
            }
            return 0L;
        };
    }

    @Nullable
    private static List<PlannedUnit> expandInputUnits(KeyCounter[] inputs) {
        var units = new ArrayList<PlannedUnit>();
        long total = 0;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    return null;
                }
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                total += amount;
                if (total > MAX_INPUT_UNITS) {
                    return null;
                }
                for (long i = 0; i < amount; i++) {
                    units.add(new PlannedUnit(itemKey));
                }
            }
        }
        return units;
    }

    @Nullable
    private static List<List<PlannedUnit>> patternInputUnitAlternatives(IPatternDetails pattern) {
        var alternatives = PatternInputAlternatives.expandItemUnitAlternatives(pattern, MAX_INPUT_UNITS);
        if (alternatives == null) {
            return null;
        }
        var planned = new ArrayList<List<PlannedUnit>>(alternatives.size());
        for (var alternative : alternatives) {
            planned.add(alternative.stream().map(PlannedUnit::new).toList());
        }
        return List.copyOf(planned);
    }

    private static boolean matchesPlannedStack(GenericStack stack, PlannedUnit unit) {
        return stack.amount() == 1 && unit.key().equals(stack.what());
    }

    private static boolean slotEmpty(IItemHandler handler) {
        return handler.getSlots() > 0 && handler.getStackInSlot(0).isEmpty();
    }

    private static boolean isUpsideDownBowl(BlockState state) {
        return state.hasProperty(BlockStateProperties.FACING)
                && state.getValue(BlockStateProperties.FACING) == Direction.DOWN;
    }

    private static boolean isMatchingSacrificeEgg(
            ItemStack stack, @Nullable TagKey<EntityType<?>> sacrificeTag) {
        if (!(stack.getItem() instanceof SpawnEggItem egg) || sacrificeTag == null) {
            return false;
        }
        try {
            return egg.getType(stack.getTag()).is(sacrificeTag);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static List<Recipe<?>> recipes(ServerLevel level) {
        var type = AdapterRecipeTypes.find(RITUAL_RECIPE_TYPE);
        if (type == null) {
            return List.of();
        }
        return (List<Recipe<?>>) (List<?>) level.getRecipeManager()
                .getAllRecipesFor((RecipeType) type);
    }

    private static boolean isOccultismLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation occultismId(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

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

    /**
     * Opaque handle returned from {@link #bind}. Caches the matched recipe so
     * {@code canDispatch} / {@code planWithBinding} can skip the recipe-table
     * scan entirely.
     */
    private record OccultismBindHandle(Object recipe) {
    }

    /** Per-push input-to-ingredient match result. */
    private record InputMatch(PlannedUnit activation, List<PlannedUnit> ingredients,
                              boolean completesSacrifice, boolean completesItemUse,
                              List<PlannedUnit> proxyCosts) {
    }

    private record BowlSlot(BlockPos pos) {
    }

}
