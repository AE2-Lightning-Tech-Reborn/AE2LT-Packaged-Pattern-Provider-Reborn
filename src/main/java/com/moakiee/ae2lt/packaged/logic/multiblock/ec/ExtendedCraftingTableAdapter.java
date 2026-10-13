package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.applyCraftRemainders;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.assemble;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.assignIngredientsStrict;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.canApplyRemaining;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.canAssignPatternIngredients;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.countNonEmpty;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.currentStacks;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.gridIsEmpty;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.hasSingleItemOutput;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.ingredients;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.outputMatchesBatch;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.recipeMatches;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.remainingItems;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.resultItem;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.virtualOutputs;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.UnboundedVirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.BoundRecipe;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime adapter for ExtendedCrafting's ordinary crafting tables.
 * Pure reflection: ExtendedCrafting is not a compile dependency.
 *
 * Supported blocks:
 *   basic_table    3x3, tier 1
 *   advanced_table 5x5, tier 2
 *   elite_table    7x7, tier 3
 *   ultimate_table 9x9, tier 4
 *
 * These tables act as virtual crafting lanes for packaged providers. Inputs are
 * consumed by AE, while this adapter validates the matching table recipe and
 * returns the assembled result plus remaining items without inserting into the
 * table grid.
 */
public final class ExtendedCraftingTableAdapter implements UnboundedVirtualCraftingAdapter {

    private static final String MOD_ID = "extendedcrafting";
    private static final Identifier BASIC_TABLE_BLOCK = ecId("basic_table");
    private static final Identifier ADVANCED_TABLE_BLOCK = ecId("advanced_table");
    private static final Identifier ELITE_TABLE_BLOCK = ecId("elite_table");
    private static final Identifier ULTIMATE_TABLE_BLOCK = ecId("ultimate_table");
    private static final Identifier RECIPE_TYPE_ID = ecId("table");

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null
                && isExtendedCraftingLoaded()
                && tableSpec(be.getBlockState()) != null
                && ExtendedCraftingTableAccess.isSupportedTable(be);
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public net.minecraft.resources.Identifier requiredAdapterId(ServerLevel level, BlockPos pos) {
        // Tier id matches the live block at the queried position. Higher-tier
        // packaged cores cover all lower tiers via MultiblockAdapterItem.covers,
        // so a single ec_ultimate core unlocks every EC table.
        var spec = tableSpec(level.getBlockState(pos));
        if (spec == null) {
            return AdapterIds.EC_BASIC;
        }
        return switch (spec.tier()) {
            case 1 -> AdapterIds.EC_BASIC;
            case 2 -> AdapterIds.EC_ADVANCED;
            case 3 -> AdapterIds.EC_ELITE;
            case 4 -> AdapterIds.EC_ULTIMATE;
            default -> AdapterIds.EC_BASIC;
        };
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
        var spec = tableSpec(be.getBlockState());
        if (spec == null) {
            return null;
        }
        var handle = searchHandle(level, pattern, spec);
        if (handle == null) {
            return null;
        }
        return new BindingResult(handle, BindingMode.VIRTUAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        // Virtual lanes have no machine state to check; the actual grid-empty
        // gate runs inside planVirtualWithBinding because EC tables share the
        // physical grid with player interaction.
        return true;
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        // Virtual adapters never produce a real DispatchPlan; route through
        // planVirtualWithBinding instead.
        return null;
    }

    @Override
    @Nullable
    public VirtualCraftingResult planVirtualWithBinding(ServerLevel level, BlockPos mainPos,
                                                        IPatternDetails pattern, KeyCounter[] inputs,
                                                        Object handle, IActionSource source) {
        if (!(handle instanceof EcBindHandle bind)) {
            return null;
        }
        var spec = bind.spec();

        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be) || !spec.matches(be.getBlockState())) {
            return null;
        }

        var handler = CucumberInventoryAccess.get(be);
        if (handler == null || handler.getSlots() < spec.slots() || !gridIsEmpty(handler, spec.slots())) {
            return null;
        }

        var available = new LinkedHashMap<AEItemKey, Long>();
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    return null;
                }
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                available.merge(itemKey, amount, Long::sum);
            }
        }
        if (available.isEmpty()) {
            return null;
        }

        for (var candidate : bind.recipes()) {
            var result = planRecipe(level, pattern, available, spec, candidate);
            if (result != null) return result;
        }
        return null;
    }

    @Nullable
    private static VirtualCraftingResult planRecipe(ServerLevel level, IPatternDetails pattern,
            LinkedHashMap<AEItemKey, Long> available, TableSpec spec, BoundRecipe candidate) {
        var ingredients = ingredients(candidate.recipe());
        if (ingredients == null) {
            return null;
        }
        int nonEmpty = countNonEmpty(ingredients);
        if (nonEmpty == 0 || nonEmpty > spec.slots()) {
            return null;
        }

        // Assign ingredients against exact AEItemKeys, not raw Item ids. This
        // preserves same-item variants with different DataComponents/NBT and
        // lets ambiguous ingredients backtrack instead of stealing the only
        // key a later ingredient can use.
        int k = candidate.craftRatio();
        var perCraftStacks = assignIngredientsStrict(ingredients, available, k);
        if (perCraftStacks == null) {
            return null;
        }

        var planned = layoutGridFromIngredients(candidate.recipe(), perCraftStacks, spec);
        if (planned == null) {
            return null;
        }

        var input = ExtendedCraftingTableAccess.createTableInput(spec.gridSize(), planned, spec.tier());
        if (input == null || !recipeMatches(candidate.recipe(), input, level)) {
            return null;
        }

        var result = assemble(candidate.recipe(), input, level);
        if (result == null || result.isEmpty()) {
            return null;
        }
        long totalCount = (long) k * result.getCount();
        if (totalCount <= 0 || totalCount > Integer.MAX_VALUE) {
            return null;
        }
        if (!outputMatchesBatch(pattern, result, totalCount)) {
            return null;
        }

        var remaining = remainingItems(candidate.recipe(), input);
        if (remaining == null) {
            return null;
        }

        var outputs = virtualOutputs(result, remaining, k);
        if (outputs.isEmpty()) {
            return null;
        }
        return new VirtualCraftingResult(outputs);
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return List.of();
        }

        var spec = tableSpec(be.getBlockState());
        var handler = CucumberInventoryAccess.get(be);
        if (spec == null || !(handler instanceof IItemHandlerModifiable modifiable)
                || handler.getSlots() < spec.slots() || gridIsEmpty(handler, spec.slots())) {
            return List.of();
        }

        var input = ExtendedCraftingTableAccess.createTableInput(spec.gridSize(), currentStacks(handler, spec.slots()), spec.tier());
        if (input == null) {
            return List.of();
        }

        var recipe = findMatchingRecipe(level, input);
        if (recipe == null) {
            return List.of();
        }

        var result = assemble(recipe, input, level);
        if (result == null || result.isEmpty()) {
            return List.of();
        }

        var key = AEItemKey.of(result);
        if (!allowsAutoReturn(level, mainPos, filter, key)) {
            return List.of();
        }

        var remaining = remainingItems(recipe, input);
        if (remaining == null || !canApplyRemaining(handler, spec.gridSize(), input, remaining, ExtendedCraftingTableAccess.top(input), ExtendedCraftingTableAccess.left(input))) {
            return List.of();
        }

        applyCraftRemainders(modifiable, spec.gridSize(), input, remaining, ExtendedCraftingTableAccess.top(input), ExtendedCraftingTableAccess.left(input));
        return List.of(new GenericStack(AEItemKey.of(result), result.getCount()));
    }

    /**
     * One-shot recipe + K search executed at bind time. Walks every EC
     * table recipe whose result item id matches the pattern's primary
     * output, derives the batch multiplier {@code K = patternOutAmount /
     * resultCount}, and verifies each non-empty ingredient can be satisfied
     * by the pattern's declared inputs. Matching (recipe, K) candidates
     * are cached on the binding so plan-time
     * dispatch can skip the full recipe-table scan.
     *
     * <p>Inputs bind according to each slot's strict or ID-only mode; the output has its own
     * independent mode. Keep every matching recipe so a later concrete input variant can select
     * its recipe, even after an earlier push used different components.
     *
     * <p>Anti-duplication invariant: neither path ever falls back to
     * {@code item.getDefaultInstance()} or any other representative stack
     * for the grid &mdash; the per-craft grid stack is always the exact
     * {@link KeyCounter} key (with its DataComponents/NBT) at plan time.
     */
    @Nullable
    private static EcBindHandle searchHandle(ServerLevel level, IPatternDetails pattern, TableSpec spec) {
        var patternOut = pattern.getOutputs().getFirst();
        if (!(patternOut.what() instanceof AEItemKey patternOutKey) || patternOut.amount() <= 0) {
            return null;
        }
        long patternOutAmount = patternOut.amount();

        boolean idOnlyOutput = OverloadPatternSemantics.isIdOnlyOutput(pattern, 0);
        var candidates = new ArrayList<BoundRecipe>();

        for (var holder : recipes(level)) {
            var recipe = holder.value();
            var resultPreview = resultItem(recipe, level);
            if (resultPreview == null || resultPreview.isEmpty()) {
                continue;
            }
            if (resultPreview.getItem() != patternOutKey.getItem()) {
                continue;
            }
            if (!idOnlyOutput && !patternOutKey.equals(AEItemKey.of(resultPreview))) {
                continue;
            }

            int resultCount = resultPreview.getCount();
            if (resultCount <= 0 || patternOutAmount % resultCount != 0) {
                continue;
            }
            long k = patternOutAmount / resultCount;
            if (k <= 0 || k > Integer.MAX_VALUE) {
                continue;
            }

            var ingredients = ingredients(recipe);
            if (ingredients == null) {
                continue;
            }
            int nonEmpty = countNonEmpty(ingredients);
            if (nonEmpty == 0 || nonEmpty > spec.slots()) {
                continue;
            }

            if (!canAssignPatternIngredients(ingredients, pattern, k)) {
                continue;
            }

            candidates.add(new BoundRecipe(recipe, (int) k));
        }
        return candidates.isEmpty() ? null : new EcBindHandle(spec, List.copyOf(candidates));
    }

    /**
     * Places one ingredient's representative stack into a grid layout the
     * EC {@code TableCraftingInput.of} factory can trim and feed to
     * {@link Recipe#matches}.
     *
     * <p>For shaped recipes we walk the ingredient list in its native
     * {@code width × height} order and copy non-empty cells to the top-left
     * of the table grid. EC's input wrapper trims the empty borders, so the
     * absolute placement doesn't matter as long as the relative shape is
     * preserved.
     *
     * <p>For shapeless recipes we drop the stacks row-major into the top of
     * the grid; shapeless matching ignores positions.
     */
    @Nullable
    private static List<ItemStack> layoutGridFromIngredients(Object recipe,
                                                             List<ItemStack> perCraftStacks,
                                                             TableSpec spec) {
        var ingredients = ingredients(recipe);
        if (ingredients == null || ingredients.isEmpty()) return null;
        var width = ExtendedCraftingTableAccess.recipeWidth(recipe);
        if (width != null && width > 0 && (ingredients.size() + width - 1) / width > spec.gridSize()) {
            return null;
        }
        return TableCraftingSupport.layoutGrid(ingredients, perCraftStacks, spec.gridSize(), width);
    }

    @Nullable
    private static Object findMatchingRecipe(ServerLevel level, CraftingInput input) {
        for (var holder : recipes(level)) {
            var recipe = holder.value();
            if (recipeMatches(recipe, input, level)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Batch match: pattern output amount must equal the derived total
     * (K × per-craft result count). Used in the virtual-craft hot path
     * so the user can write any uniform N:N pattern (e.g.
     * {@code 8 stone → 8 brick}) and have it consumed in one push.
     */

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipes(ServerLevel level) {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(RECIPE_TYPE_ID)
                .map(type -> (List<RecipeHolder<?>>) (List<?>) com.moakiee.ae2lt.packaged.logic.multiblock.shared.RecipeCompat26.byType(level, type))
                .orElse(List.of());
    }

    @Nullable
    private static TableSpec tableSpec(BlockState state) {
        var id = blockId(state);
        if (id.equals(BASIC_TABLE_BLOCK)) {
            return new TableSpec(BASIC_TABLE_BLOCK, 3, 1);
        }
        if (id.equals(ADVANCED_TABLE_BLOCK)) {
            return new TableSpec(ADVANCED_TABLE_BLOCK, 5, 2);
        }
        if (id.equals(ELITE_TABLE_BLOCK)) {
            return new TableSpec(ELITE_TABLE_BLOCK, 7, 3);
        }
        if (id.equals(ULTIMATE_TABLE_BLOCK)) {
            return new TableSpec(ULTIMATE_TABLE_BLOCK, 9, 4);
        }
        return null;
    }

    private static boolean isExtendedCraftingLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static Identifier blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static Identifier ecId(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private record TableSpec(Identifier blockId, int gridSize, int tier) {
        int slots() {
            return gridSize * gridSize;
        }

        boolean matches(BlockState state) {
            return ExtendedCraftingTableAdapter.blockId(state).equals(blockId);
        }
    }

    /**
     * Bind-time cache populated by {@link #searchHandle}. Captures the
     * matching recipes and their batch multipliers so plan-time dispatch
     * can select using actual components without scanning unrelated recipes.
     */
    private record EcBindHandle(TableSpec spec, List<BoundRecipe> recipes) {
    }

}
