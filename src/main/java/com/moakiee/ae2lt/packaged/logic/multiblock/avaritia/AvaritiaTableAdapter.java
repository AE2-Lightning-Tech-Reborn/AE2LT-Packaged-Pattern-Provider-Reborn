package com.moakiee.ae2lt.packaged.logic.multiblock.avaritia;

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
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.BoundRecipe;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime adapter for Re-Avaritia's tiered crafting tables.
 * Pure reflection: Re-Avaritia is not a compile dependency.
 */
public final class AvaritiaTableAdapter implements UnboundedVirtualCraftingAdapter {

    private static final String MOD_ID = "avaritia";
    private static final Identifier SCULK_TABLE_BLOCK = avaritiaId("sculk_crafting_table");
    private static final Identifier NETHER_TABLE_BLOCK = avaritiaId("nether_crafting_table");
    private static final Identifier END_TABLE_BLOCK = avaritiaId("end_crafting_table");
    private static final Identifier EXTREME_TABLE_BLOCK = avaritiaId("extreme_crafting_table");
    private static final Identifier RECIPE_TYPE_ID = avaritiaId("crafting_table_recipe");

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, net.minecraft.core.BlockPos pos, @Nullable BlockEntity be) {
        return be != null
                && isAvaritiaLoaded()
                && tableSpec(be.getBlockState()) != null
                && AvaritiaTableAccess.isSupportedTable(be);
    }

    @Override
    public Identifier requiredAdapterId(ServerLevel level, net.minecraft.core.BlockPos pos) {
        var spec = tableSpec(level.getBlockState(pos));
        if (spec == null) {
            return AdapterIds.AVARITIA_SCULK_TABLE;
        }
        return switch (spec.tier()) {
            case 1 -> AdapterIds.AVARITIA_SCULK_TABLE;
            case 2 -> AdapterIds.AVARITIA_NETHER_TABLE;
            case 3 -> AdapterIds.AVARITIA_END_TABLE;
            case 4 -> AdapterIds.AVARITIA_EXTREME_TABLE;
            default -> AdapterIds.AVARITIA_SCULK_TABLE;
        };
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, net.minecraft.core.BlockPos mainPos, IPatternDetails pattern) {
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
    public boolean canDispatch(ServerLevel level, net.minecraft.core.BlockPos mainPos, Object handle) {
        return true;
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, net.minecraft.core.BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        return null;
    }

    @Override
    @Nullable
    public VirtualCraftingResult planVirtualWithBinding(ServerLevel level, net.minecraft.core.BlockPos mainPos,
                                                        IPatternDetails pattern, KeyCounter[] inputs,
                                                        Object handle, IActionSource source) {
        if (!(handle instanceof TableBindHandle bind)) {
            return null;
        }
        var spec = bind.spec();

        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be) || !spec.matches(be.getBlockState())) {
            return null;
        }

        var handler = AvaritiaTableAccess.getHandler(be);
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

        int k = candidate.craftRatio();
        var perCraftStacks = assignIngredientsStrict(ingredients, available, k);
        if (perCraftStacks == null) {
            return null;
        }

        var planned = layoutGridFromIngredients(candidate.recipe(), perCraftStacks, spec);
        if (planned == null) {
            return null;
        }

        var input = AvaritiaTableAccess.createTierInput(
                spec.gridSize(), planned, spec.tier());
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
    public List<GenericStack> extractOutputs(ServerLevel level, net.minecraft.core.BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return List.of();
        }

        var spec = tableSpec(be.getBlockState());
        var handler = AvaritiaTableAccess.getHandler(be);
        if (spec == null || !(handler instanceof IItemHandlerModifiable modifiable)
                || handler.getSlots() < spec.slots() || gridIsEmpty(handler, spec.slots())) {
            return List.of();
        }

        var input = AvaritiaTableAccess.createTierInput(
                spec.gridSize(), currentStacks(handler, spec.slots()), spec.tier());
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
        if (remaining == null || !canApplyRemaining(handler, spec.gridSize(), input, remaining, AvaritiaTableAccess.top(input), AvaritiaTableAccess.left(input))) {
            return List.of();
        }

        applyCraftRemainders(modifiable, spec.gridSize(), input, remaining, AvaritiaTableAccess.top(input), AvaritiaTableAccess.left(input));
        return List.of(new GenericStack(AEItemKey.of(result), result.getCount()));
    }

    @Nullable
    private static TableBindHandle searchHandle(ServerLevel level, IPatternDetails pattern, TableSpec spec) {
        var patternOut = pattern.getOutputs().getFirst();
        if (!(patternOut.what() instanceof AEItemKey patternOutKey) || patternOut.amount() <= 0) {
            return null;
        }
        long patternOutAmount = patternOut.amount();

        boolean idOnlyOutput = OverloadPatternSemantics.isIdOnlyOutput(pattern, 0);
        var candidates = new ArrayList<BoundRecipe>();

        for (var holder : recipes(level)) {
            var recipe = holder.value();
            if (!tableCanCraftRecipe(spec, recipe)) {
                continue;
            }
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
        return candidates.isEmpty() ? null : new TableBindHandle(spec, List.copyOf(candidates));
    }

    private static boolean tableCanCraftRecipe(TableSpec spec, Object recipe) {
        int recipeTier = AvaritiaTableAccess.recipeTier(recipe);
        if (recipeTier <= 0) {
            return true;
        }
        return AvaritiaTableAccess.recipeHasRequiredTier(recipe)
                ? spec.tier() == recipeTier
                : spec.tier() >= recipeTier;
    }

    @Nullable
    private static List<ItemStack> layoutGridFromIngredients(Object recipe,
                                                             List<ItemStack> perCraftStacks,
                                                             TableSpec spec) {
        var ingredients = ingredients(recipe);
        if (ingredients == null || ingredients.isEmpty()) return null;
        var width = AvaritiaTableAccess.recipeWidth(recipe);
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipes(ServerLevel level) {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(RECIPE_TYPE_ID)
                .map(type -> (List<RecipeHolder<?>>) (List<?>) com.moakiee.ae2lt.packaged.logic.multiblock.shared.RecipeCompat26.byType(level, type))
                .orElse(List.of());
    }

    @Nullable
    private static TableSpec tableSpec(BlockState state) {
        var id = blockId(state);
        if (id.equals(SCULK_TABLE_BLOCK)) {
            return new TableSpec(SCULK_TABLE_BLOCK, 3, 1);
        }
        if (id.equals(NETHER_TABLE_BLOCK)) {
            return new TableSpec(NETHER_TABLE_BLOCK, 5, 2);
        }
        if (id.equals(END_TABLE_BLOCK)) {
            return new TableSpec(END_TABLE_BLOCK, 7, 3);
        }
        if (id.equals(EXTREME_TABLE_BLOCK)) {
            return new TableSpec(EXTREME_TABLE_BLOCK, 9, 4);
        }
        return null;
    }

    private static boolean isAvaritiaLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static Identifier blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static Identifier avaritiaId(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private record TableSpec(Identifier blockId, int gridSize, int tier) {
        int slots() {
            return gridSize * gridSize;
        }

        boolean matches(BlockState state) {
            return AvaritiaTableAdapter.blockId(state).equals(blockId);
        }
    }

    private record TableBindHandle(TableSpec spec, List<BoundRecipe> recipes) {
    }

}
