package com.moakiee.ae2lt.packaged.logic.multiblock.occultism;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.PackagedCoreBootstrap;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

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
        if (!(handle instanceof OccultismBindHandle bind)) {
            return null;
        }

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
                    proxyCostConsumer(match.proxyCosts())));
        }

        return new DispatchPlan(List.copyOf(targets), null);
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be) || !OccultismRitualAccess.isIdle(be)) {
            return List.of();
        }

        for (int y = 1; y <= 3; y++) {
            var bowlPos = mainPos.above(y);
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

            var stack = handler.getStackInSlot(0);
            if (stack.isEmpty()) {
                continue;
            }

            var key = AEItemKey.of(stack);
            if (!allowsAutoReturn(level, mainPos, filter, key)) {
                continue;
            }

            var extracted = handler.extractItem(0, stack.getCount(), false);
            if (extracted.isEmpty()) {
                continue;
            }
            return List.of(new GenericStack(AEItemKey.of(extracted), extracted.getCount()));
        }

        return List.of();
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
        var patternUnits = patternInputUnits(pattern);
        if (patternUnits == null || patternUnits.isEmpty()) {
            LOG.debug("findCandidate: patternInputUnits null/empty at {} (pattern={})",
                    mainPos, pattern.getDefinition());
            return null;
        }
        if (LOG.isDebugEnabled()) {
            var sb = new StringBuilder();
            for (var u : patternUnits) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(u.key().getId());
            }
            var raw = new StringBuilder();
            for (var input : pattern.getInputs()) {
                var possible = input.getPossibleInputs();
                if (raw.length() > 0) raw.append(", ");
                if (possible.length == 0) {
                    raw.append("<empty>");
                } else {
                    raw.append(possible[0].what()).append(" x").append(possible[0].amount())
                            .append(" mult=").append(input.getMultiplier());
                }
            }
            LOG.debug("findCandidate: pattern inputs at {} expandedUnits=[{}] rawInputs=[{}]",
                    mainPos, sb, raw);
        }
        int scanned = 0;
        int pentacleFail = 0;
        int missingApi = 0;
        int inputMismatch = 0;
        int mismatchLogged = 0;
        for (var holder : recipes(level)) {
            scanned++;
            var recipe = holder.value();
            // Ordinary rituals may produce world effects rather than items. A
            // core bootstrap must instead prove its real, exact item result;
            // an invented pattern output must never unlock arbitrary rituals.
            if (PackagedCoreBootstrap.isCorePattern(pattern)) {
                var result = recipe.getResultItem(level.registryAccess());
                var expected = pattern.getOutputs().getFirst();
                if (result.isEmpty() || !expected.what().equals(AEItemKey.of(result))
                        || expected.amount() != result.getCount()) continue;
            }
            if (!OccultismRitualAccess.hasValidPentacle(recipe, level, mainPos)) {
                pentacleFail++;
                continue;
            }
            var activation = OccultismRitualAccess.getActivationItem(recipe);
            var ingredients = OccultismRitualAccess.getIngredients(recipe);
            if (activation == null || ingredients == null) {
                missingApi++;
                continue;
            }
            if (matchInputsToRecipe(patternUnits, recipe) == null) {
                inputMismatch++;
                if (mismatchLogged < 5) {
                    LOG.debug("findCandidate: recipe {} mismatch (activation={}, ingredients={}, requiresSacrifice={}, requiresItemUse={}, itemToUse={})",
                            holder.id(),
                            ingredientFirstItems(activation),
                            ingredientListItems(ingredients),
                            OccultismRitualAccess.requiresSacrifice(recipe),
                            OccultismRitualAccess.requiresItemUse(recipe),
                            ingredientFirstItems(OccultismRitualAccess.getItemToUse(recipe)));
                    mismatchLogged++;
                }
                continue;
            }
            LOG.debug("findCandidate: matched recipe {} at {} ({} pattern units)",
                    holder.id(), mainPos, patternUnits.size());
            return new OccultismBindHandle(recipe);
        }
        LOG.debug("findCandidate: no match at {} (scanned={}, pentacleFail={}, missingApi={}, inputMismatch={}, patternUnits={})",
                mainPos, scanned, pentacleFail, missingApi, inputMismatch, patternUnits.size());
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
        for (var ingredient : ingredients) {
            PlannedUnit matched = null;
            for (int i = 0; i < units.size(); i++) {
                if (!used[i] && ingredient.test(units.get(i).stack())) {
                    matched = units.get(i);
                    used[i] = true;
                    break;
                }
            }
            if (matched == null) {
                return null;
            }
            matchedIngredients.add(matched);
        }

        boolean completesSacrifice = false;
        boolean completesItemUse = false;
        var proxyCosts = new ArrayList<PlannedUnit>(2);
        var itemToUse = OccultismRitualAccess.getItemToUse(recipe);
        boolean requiresSacrifice = OccultismRitualAccess.requiresSacrifice(recipe);
        boolean requiresItemUse = itemToUse != null && OccultismRitualAccess.requiresItemUse(recipe);
        for (int i = 0; i < units.size(); i++) {
            if (used[i]) {
                continue;
            }
            var unit = units.get(i);
            if (!completesSacrifice && requiresSacrifice && isSpawnEgg(unit.stack())) {
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

            if (mode == Actionable.MODULATE) {
                var remainder = handler.insertItem(0, unit.stack(), false);
                var accepted = 1L - remainder.getCount();
                if (accepted > 0 && (completesSacrifice || completesItemUse)) {
                    // insertItem above synchronously calls startRitual() on
                    // success, which resets sacrificeProvided and
                    // itemUseProvided. Flip them back here so the ritual tick
                    // doesn't stall waiting for an in-world sacrifice / item
                    // use that the proxy items already represent.
                    OccultismRitualAccess.completeDeferredRequirements(
                            be, completesSacrifice, completesItemUse);
                    be.setChanged();
                }
                return accepted;
            }
            return 1L;
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> proxyCostConsumer(List<PlannedUnit> units) {
        return (stack, mode) -> {
            for (var unit : units) {
                if (matchesPlannedStack(stack, unit)) {
                    return 1L;
                }
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
    private static List<PlannedUnit> patternInputUnits(IPatternDetails pattern) {
        var inputs = pattern.getInputs();
        var units = new ArrayList<PlannedUnit>();
        long total = 0;
        for (var input : inputs) {
            var possible = input.getPossibleInputs();
            if (possible.length == 0 || !(possible[0].what() instanceof AEItemKey itemKey)) {
                continue;
            }
            // AE2's IInput stores the per-craft quantity in two places: the
            // primary {@link GenericStack#amount} of {@code possibleInputs[0]}
            // (typically 1 for items, 1000 for fluids) and a separate
            // {@link IInput#getMultiplier()} that scales it. The true count of
            // items the pattern wants is multiplier * primary.amount(). Using
            // only primary.amount() collapses any stack > 1 into a single unit,
            // which was the bug that caused "iron_ingot x4" to look like one
            // pattern unit.
            long stackAmount = Math.max(1L, possible[0].amount());
            long multiplier = Math.max(1L, input.getMultiplier());
            long amount;
            try {
                amount = Math.multiplyExact(stackAmount, multiplier);
            } catch (ArithmeticException ignored) {
                return null;
            }
            total += amount;
            if (total > MAX_INPUT_UNITS) {
                return null;
            }
            for (long i = 0; i < amount; i++) {
                units.add(new PlannedUnit(itemKey));
            }
        }
        return units.isEmpty() ? null : List.copyOf(units);
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

    private static boolean isSpawnEgg(ItemStack stack) {
        return stack.getItem() instanceof SpawnEggItem;
    }

    private static List<RecipeHolder<?>> recipes(ServerLevel level) {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(RITUAL_RECIPE_TYPE)
                .map(type -> recipesForType(level, type))
                .orElse(List.of());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipesForType(ServerLevel level, RecipeType<?> type) {
        return (List<RecipeHolder<?>>) (List<?>) level.getRecipeManager()
                .getAllRecipesFor((RecipeType) type);
    }

    private static boolean isOccultismLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation occultismId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
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
