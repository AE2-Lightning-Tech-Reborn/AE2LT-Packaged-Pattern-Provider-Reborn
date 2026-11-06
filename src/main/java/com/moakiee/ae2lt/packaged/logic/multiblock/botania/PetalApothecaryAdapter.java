package com.moakiee.ae2lt.packaged.logic.multiblock.botania;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

/**
 * Virtual flower crafting; each batch consumes its petals and seed reagent.
 * Water source priority is buckets, AE fluid, then the filled basin. Buckets and
 * fluid cost one bucket per craft; basin water costs one charge for the batch.
 * Bucket inputs return empty buckets. All supplied inputs must be accounted for.
 * Planning is pure; basin changes run on commit and feedback runs at batch flush.
 */
public final class PetalApothecaryAdapter implements VirtualCraftingAdapter {

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
        return BotaniaReflection.isPetalApothecary(be);
    }

    // Note: flushSoundId() intentionally not overridden. onVirtualBatchFlush
    // already triggers the apothecary's own altarCraft sound via the
    // blockEvent path; adding a second "ding" at the provider would
    // overlap the same tick and produce audible dissonance.

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.BOTANIA_PETAL_APOTHECARY;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (!recognizesMain(level, mainPos, be)) {
            return null;
        }
        var holder = BotaniaRecipeLookup.findPetalApothecaryByOutput(level, pattern);
        if (holder == null) {
            return null;
        }
        return new BindingResult(new PetalBindHandle(holder), BindingMode.VIRTUAL);
    }

    /**
     * Cover petals and reagent for the declared batch without reusing input
     * quantities. Alternatives of one pattern input are choices, not additional
     * inventory. Water and leftover validation remain plan-time checks.
     */
    static boolean patternInputsCoverRecipe(IPatternDetails pattern, Recipe<?> recipe, long craftRatio,
                                            SearchBudget budget) {
        var patternInputs = pattern.getInputs();
        if (patternInputs == null || patternInputs.length == 0 || craftRatio <= 0 || budget.exhausted()) {
            return false;
        }
        if (patternInputs.length > 64) {
            budget.exhausted = true;
            return false;
        }
        var allIngredients = new ArrayList<Ingredient>(recipe.getIngredients());
        var reagent = BotaniaReflection.recipeReagent(recipe);
        if (reagent != null && !reagent.isEmpty()) {
            allIngredients.add(reagent);
        }
        return coverWithAlternatives(patternInputs, 0, allIngredients, craftRatio,
                new LinkedHashMap<>(), budget);
    }

    private static boolean coverWithAlternatives(IPatternDetails.IInput[] inputs, int index,
                                                 List<Ingredient> ingredients, long craftRatio,
                                                 LinkedHashMap<AEItemKey, Long> available, SearchBudget budget) {
        if (budget.exhausted()) {
            return false;
        }
        if (index == inputs.length) {
            return consumeIngredientsStrict(ingredients, craftRatio, available);
        }
        var input = inputs[index];
        var possible = input == null ? null : input.getPossibleInputs();
        if (possible == null || possible.length == 0) {
            return coverWithAlternatives(inputs, index + 1, ingredients, craftRatio, available, budget);
        }
        for (var stack : possible) {
            // Charge every attempted alternative before allocating, including invalid ones.
            if (!budget.tryBranch()) {
                return false;
            }
            var nextAvailable = new LinkedHashMap<>(available);
            if (stack != null && stack.what() instanceof AEItemKey key) {
                long multiplier = input.getMultiplier();
                if (stack.amount() <= 0 || multiplier <= 0) {
                    continue;
                }
                try {
                    long amount = Math.multiplyExact(stack.amount(), multiplier);
                    nextAvailable.put(key, Math.addExact(nextAvailable.getOrDefault(key, 0L), amount));
                } catch (ArithmeticException overflow) {
                    continue;
                }
            }
            if (coverWithAlternatives(inputs, index + 1, ingredients, craftRatio, nextAvailable, budget)) {
                return true;
            }
        }
        return false;
    }

    /** One budget per lookup, shared by all output-compatible candidates. */
    static final class SearchBudget {
        private int remaining = 4096;
        private boolean exhausted;

        boolean exhausted() {
            return exhausted;
        }

        private boolean tryBranch() {
            if (exhausted || remaining == 0) {
                exhausted = true;
                return false;
            }
            remaining--;
            return true;
        }
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        // Water-source readiness is checked during virtual planning.
        var be = level.getBlockEntity(mainPos);
        return BotaniaReflection.isPetalApothecary(be);
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        return null;
    }

    @Override
    @Nullable
    public VirtualCraftingResult planVirtualWithBinding(ServerLevel level, BlockPos mainPos,
                                                        IPatternDetails pattern, KeyCounter[] inputs,
                                                        Object handle, IActionSource source) {
        if (!(handle instanceof PetalBindHandle bind)) {
            return null;
        }
        var be = level.getBlockEntity(mainPos);
        if (!recognizesMain(level, mainPos, be)) {
            return null;
        }
        var recipe = bind.holder();
        var result = recipe.getResultItem(level.registryAccess());
        if (result == null || result.isEmpty()) {
            return null;
        }
        long patternAmount = BotaniaRecipeLookup.patternPrimaryOutputAmount(pattern);
        int resultCount = result.getCount();
        if (resultCount <= 0 || patternAmount <= 0 || patternAmount % resultCount != 0) {
            return null;
        }
        long craftRatio = patternAmount / resultCount;
        if (craftRatio <= 0 || craftRatio > Integer.MAX_VALUE) {
            return null;
        }

        // Materialize the KeyCounter into two mutable maps (items +
        // fluids). Every consumed key is debited from these maps; at
        // the end both maps must be exactly zero (no swallowing).
        var itemAvailable = new LinkedHashMap<AEItemKey, Long>();
        var fluidAvailable = new LinkedHashMap<Fluid, Long>();
        var fluidKeys = new HashMap<Fluid, AEFluidKey>();
        for (var counter : inputs) {
            for (var entry : counter) {
                long amt = entry.getLongValue();
                if (amt <= 0) {
                    continue;
                }
                if (entry.getKey() instanceof AEItemKey itemKey) {
                    long previousItemAmount = itemAvailable.getOrDefault(itemKey, 0L);
                    try {
                        itemAvailable.put(itemKey, Math.addExact(previousItemAmount, amt));
                    } catch (ArithmeticException overflow) {
                        return null;
                    }
                } else if (entry.getKey() instanceof AEFluidKey fluidKey) {
                    var fluid = fluidKey.getFluid();
                    long previousFluidAmount = fluidAvailable.getOrDefault(fluid, 0L);
                    try {
                        fluidAvailable.put(fluid, Math.addExact(previousFluidAmount, amt));
                    } catch (ArithmeticException overflow) {
                        return null;
                    }
                    fluidKeys.putIfAbsent(fluid, fluidKey);
                } else {
                    // Unknown key kind: refuse rather than mistakenly
                    // committing a craft that AE wouldn't be able to
                    // recover from.
                    return null;
                }
            }
        }
        if (itemAvailable.isEmpty() && fluidAvailable.isEmpty()) {
            return null;
        }

        // Choose water source (priority: bucket > fluid > basin).
        var water = chooseWaterSource(itemAvailable, fluidAvailable, craftRatio, be);
        if (water == null) {
            return null;
        }

        // Consume recipe ingredients + reagent strictly from itemAvailable.
        var ingredients = new ArrayList<Ingredient>(recipe.getIngredients());
        var reagent = BotaniaReflection.recipeReagent(recipe);
        if (reagent != null && !reagent.isEmpty()) {
            ingredients.add(reagent);
        }
        if (!consumeIngredientsStrict(ingredients, craftRatio, itemAvailable)) {
            return null;
        }

        // No-swallow check across both maps.
        for (var v : itemAvailable.values()) {
            if (v > 0) {
                return null;
            }
        }
        for (var v : fluidAvailable.values()) {
            if (v > 0) {
                return null;
            }
        }

        long totalCount;
        try {
            totalCount = Math.multiplyExact(craftRatio, (long) resultCount);
        } catch (ArithmeticException ignored) {
            return null;
        }
        if (!BotaniaRecipeLookup.validatePatternBatchOutput(pattern, result, totalCount)) {
            return null;
        }

        // Build the output list: always the primary recipe result; with
        // bucket-mode we additionally return K empty buckets to mirror
        // vanilla's water_bucket->bucket conversion.
        var outputs = new ArrayList<GenericStack>(2);
        outputs.add(new GenericStack(AEItemKey.of(result), totalCount));
        if (water == WaterSource.BUCKET) {
            outputs.add(new GenericStack(AEItemKey.of(Items.BUCKET), craftRatio));
        }
        // Drain the basin only after the provider acquires the virtual
        // lane. Bucket / fluid modes leave the basin untouched so a
        // bucket-fed pattern can run on an empty basin.
        if (water != WaterSource.BASIN) {
            return new VirtualCraftingResult(outputs);
        }
        var committedPos = mainPos.immutable();
        return new VirtualCraftingResult(outputs, () -> {
            var current = level.getBlockEntity(committedPos);
            if (!recognizesMain(level, committedPos, current)
                    || !BotaniaReflection.apothecaryHasWater(current)) {
                return false;
            }
            BotaniaReflection.apothecarySetEmpty(current);
            return true;
        });
    }

    /** Selects and debits water from the availability maps, or returns {@code null}. */
    @Nullable
    private static WaterSource chooseWaterSource(LinkedHashMap<AEItemKey, Long> itemAvailable,
                                                 LinkedHashMap<Fluid, Long> fluidAvailable,
                                                 long craftRatio,
                                                 BlockEntity be) {
        long bucketsNeeded = craftRatio;
        for (var entry : itemAvailable.entrySet()) {
            if (entry.getKey().getItem() == Items.WATER_BUCKET) {
                bucketsNeeded -= Math.min(bucketsNeeded, entry.getValue());
            }
        }
        if (bucketsNeeded == 0) {
            long remaining = craftRatio;
            var entries = itemAvailable.entrySet().iterator();
            while (entries.hasNext() && remaining > 0) {
                var entry = entries.next();
                if (entry.getKey().getItem() != Items.WATER_BUCKET) continue;
                long used = Math.min(remaining, entry.getValue());
                remaining -= used;
                if (used == entry.getValue()) entries.remove();
                else entry.setValue(entry.getValue() - used);
            }
            return WaterSource.BUCKET;
        }
        long fluidAvail = fluidAvailable.getOrDefault(Fluids.WATER, 0L);
        long fluidNeed;
        try {
            fluidNeed = Math.multiplyExact(craftRatio, (long) AEFluidKey.AMOUNT_BUCKET);
        } catch (ArithmeticException ignored) {
            return null;
        }
        if (fluidAvail >= fluidNeed) {
            long remaining = fluidAvail - fluidNeed;
            if (remaining == 0) {
                fluidAvailable.remove(Fluids.WATER);
            } else {
                fluidAvailable.put(Fluids.WATER, remaining);
            }
            return WaterSource.FLUID;
        }
        // No bucket / fluid available: fall back to the basin's own
        // water. If even that is empty we can't craft.
        if (!BotaniaReflection.apothecaryHasWater(be)) {
            return null;
        }
        return WaterSource.BASIN;
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter, IActionSource source) {
        return List.of();
    }

    /**
     * Uses Botania's block event for craft particles and sound, and emits
     * BLOCK_ACTIVATE so Allay and sculk listeners receive the craft event.
     */
    @Override
    public void onVirtualBatchFlush(ServerLevel level, BlockPos mainPos, Object handle, IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (!BotaniaReflection.isPetalApothecary(be)) {
            return;
        }
        var state = be.getBlockState();
        level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, mainPos);
        level.blockEvent(mainPos, state.getBlock(), 1, 0);
    }

    /**
     * Consumes K NBT-matching units for each petal and reagent.
     * The caller must reject any leftover inputs.
     */
    private static boolean consumeIngredientsStrict(List<Ingredient> ingredients, long k,
                                                    LinkedHashMap<AEItemKey, Long> available) {
        if (k <= 0) {
            return false;
        }

        var required = ingredients.stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        if (required.isEmpty()) return true;
        // Capacities represent item counts, so a batch never allocates one node per item.
        // Residual paths can move a broad match aside for a later NBT-specific ingredient.
        if (required.size() > 64 || available.size() > 64) return false;
        var keys = new ArrayList<>(available.keySet());
        int firstIngredient = 1 + keys.size();
        int sink = firstIngredient + required.size();
        long[][] capacity = new long[sink + 1][sink + 1];
        for (int i = 0; i < keys.size(); i++) {
            long amount = available.get(keys.get(i));
            if (amount < 0) return false;
            capacity[0][i + 1] = amount;
            for (int j = 0; j < required.size(); j++) {
                if (required.get(j).test(keys.get(i).toStack(1))) {
                    capacity[i + 1][firstIngredient + j] = Math.min(amount, k);
                }
            }
        }
        for (int j = 0; j < required.size(); j++) capacity[firstIngredient + j][sink] = k;
        int[] parent = new int[sink + 1];
        int[] queue = new int[sink + 1];
        while (true) {
            java.util.Arrays.fill(parent, -1);
            parent[0] = 0;
            queue[0] = 0;
            int head = 0, tail = 1;
            while (head < tail && parent[sink] < 0) {
                int from = queue[head++];
                for (int to = 1; to <= sink; to++) {
                    if (parent[to] < 0 && capacity[from][to] > 0) {
                        parent[to] = from;
                        queue[tail++] = to;
                    }
                }
            }
            if (parent[sink] < 0) break;
            long moved = Long.MAX_VALUE;
            for (int to = sink; to != 0; to = parent[to]) {
                moved = Math.min(moved, capacity[parent[to]][to]);
            }
            for (int to = sink; to != 0; to = parent[to]) {
                capacity[parent[to]][to] -= moved;
                capacity[to][parent[to]] += moved;
            }
        }
        for (int j = 0; j < required.size(); j++) {
            if (capacity[firstIngredient + j][sink] != 0) return false;
        }
        // Publish deductions only after every ingredient has a complete assignment.
        for (int i = 0; i < keys.size(); i++) {
            long remaining = capacity[0][i + 1];
            if (remaining == 0) available.remove(keys.get(i));
            else available.put(keys.get(i), remaining);
        }
        return true;
    }

    /** Caches the recipe; water source selection remains a per-push decision. */
    private record PetalBindHandle(Recipe<?> holder) {
    }

    private enum WaterSource {
        BUCKET,
        FLUID,
        BASIN
    }
}
