package com.moakiee.ae2lt.packaged.logic.multiblock.avaritia;

import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.currentStacks;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.gridIsEmpty;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.hasSingleItemOutput;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.outputMatchesBatch;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.assemble;
import static com.moakiee.ae2lt.packaged.logic.multiblock.shared.TableCraftingSupport.virtualOutputs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterRecipeTypes;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterBlocks;
import com.moakiee.ae2lt.packaged.logic.multiblock.UnboundedVirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.TablePatternMatcher;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.TablePatternMatcher.BoundRecipe;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

/**
 * Runtime adapter for Re-Avaritia's tiered tables and AvaritiaNeo's extreme table.
 * Pure reflection: neither Avaritia implementation is a compile dependency.
 */
public final class AvaritiaTableAdapter implements UnboundedVirtualCraftingAdapter {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("ae2ltpp/avaritia-table");
    private static final String MOD_ID = "avaritia";
    private static final ResourceLocation SCULK_TABLE_BLOCK = avaritiaId("sculk_crafting_table");
    private static final ResourceLocation NETHER_TABLE_BLOCK = avaritiaId("nether_crafting_table");
    private static final ResourceLocation END_TABLE_BLOCK = avaritiaId("end_crafting_table");
    private static final ResourceLocation EXTREME_TABLE_BLOCK = avaritiaId("extreme_crafting_table");
    private static final ResourceLocation RECIPE_TYPE_ID = avaritiaId("crafting_table_recipe");
    private static final Set<String> DIAGNOSED_TABLE_CLASSES = ConcurrentHashMap.newKeySet();

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, net.minecraft.core.BlockPos pos, @Nullable BlockEntity be) {
        if (be == null || !isAvaritiaLoaded() || !AvaritiaTableAccess.isSupportedTable(be)) {
            return false;
        }
        var spec = tableSpec(be.getBlockState());
        if (spec == null) {
            return false;
        }
        var handler = AvaritiaTableAccess.getHandler(be);
        diagnoseTableOnce(level, spec, be, handler);
        spec = runtimeTableSpec(spec, handler);
        return handler != null && spec != null && hasExactSlots(handler, spec);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, net.minecraft.core.BlockPos pos) {
        var spec = tableSpec(level.getBlockState(pos));
        if (spec == null) {
            return AdapterIds.AVARITIA_SCULK_TABLE;
        }
        // Mapped by block identity, not inferred from tier: Re-Avaritia 1.4.1
        // defines END as tier 3 and EXTREME as tier 4, and each unlock item is
        // tied to the concrete table block.
        var blockId = spec.blockId();
        if (blockId.equals(NETHER_TABLE_BLOCK)) {
            return AdapterIds.AVARITIA_NETHER_TABLE;
        }
        if (blockId.equals(END_TABLE_BLOCK)) {
            return AdapterIds.AVARITIA_END_TABLE;
        }
        if (blockId.equals(EXTREME_TABLE_BLOCK)) {
            return AdapterIds.AVARITIA_EXTREME_TABLE;
        }
        return AdapterIds.AVARITIA_SCULK_TABLE;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, net.minecraft.core.BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (be == null) {
            LOG.debug("bind: no block entity at {}", mainPos);
            return null;
        }
        var spec = tableSpec(be.getBlockState());
        if (spec == null) {
            LOG.debug("bind: unsupported block {} at {}", blockId(be.getBlockState()), mainPos);
            return null;
        }
        if (!AvaritiaTableAccess.isSupportedTable(be)) {
            LOG.debug("bind: block {} uses unsupported block entity {} at {}",
                    spec.blockId(), be.getClass().getName(), mainPos);
            return null;
        }
        var handler = AvaritiaTableAccess.getHandler(be);
        if (handler == null) {
            diagnoseTableOnce(level, spec, be, null);
            LOG.debug("bind: getInventory returned no IItemHandler for {} at {}",
                    be.getClass().getName(), mainPos);
            return null;
        }
        diagnoseTableOnce(level, spec, be, handler);
        spec = runtimeTableSpec(spec, handler);
        if (spec == null || !hasExactSlots(handler, spec)) {
            return null;
        }
        if (!hasSingleItemOutput(pattern)) {
            LOG.debug("bind: pattern at {} must have exactly one item output", mainPos);
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
        if (AvaritiaNeoSupport.isSupportedTable(be) && !AvaritiaNeoSupport.resultIsEmpty(be)) {
            return null;
        }

        var handler = AvaritiaTableAccess.getHandler(be);
        if (handler == null || !hasExactSlots(handler, spec) || !gridIsEmpty(handler, spec.slots())) {
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
                try { available.merge(itemKey, amount, Math::addExact); }
                catch (ArithmeticException overflow) { return null; }
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

        var input = AvaritiaTableAccess.createGridInput(spec.gridSize(), stacksForPlan(spec.slots(), planned));
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
        if (be != null && AvaritiaNeoSupport.isSupportedTable(be)) {
            return List.of();
        }
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return List.of();
        }

        var declaredSpec = tableSpec(be.getBlockState());
        var handler = AvaritiaTableAccess.getHandler(be);
        var spec = runtimeTableSpec(declaredSpec, handler);
        if (spec == null || !(handler instanceof IItemHandlerModifiable modifiable)
                || !hasExactSlots(handler, spec) || gridIsEmpty(handler, spec.slots())) {
            return List.of();
        }

        var input = AvaritiaTableAccess.createGridInput(spec.gridSize(), currentStacks(handler, spec.slots()));
        if (input == null) {
            return List.of();
        }

        var recipe = findMatchingRecipe(level, input, spec);
        if (recipe == null) {
            LOG.debug("extract: no recipe matched the occupied {}x{} grid at {}",
                    spec.gridSize(), spec.gridSize(), mainPos);
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
        if (remaining == null) {
            return List.of();
        }
        var offset = recipeOffset(recipe, input, spec.gridSize());
        if (offset == null || !canApplyRemaining(handler, spec.gridSize(), input, remaining, offset)) {
            return List.of();
        }

        if (!applyCraftRemainders(modifiable, spec.gridSize(), input, remaining, offset)) {
            return List.of();
        }
        return List.of(new GenericStack(AEItemKey.of(result), result.getCount()));
    }

    @Nullable
    private static TableBindHandle searchHandle(ServerLevel level, IPatternDetails pattern, TableSpec spec) {
        var output = pattern.getOutputs()[0];
        if (!(output.what() instanceof AEItemKey key) || output.amount() <= 0) return null;
        boolean idOnlyOutput = OverloadPatternSemantics.isIdOnlyOutput(pattern, 0);
        var candidates = new ArrayList<BoundRecipe>();
        int tierRejected = 0;
        for (var recipe : recipes(level)) {
            var recipeTier = AvaritiaTableAccess.recipeTier(recipe);
            if (!tableCanCraftRecipe(spec, recipeTier)) {
                if (tierRejected++ < 4) {
                    Integer recipeWidth = AvaritiaTableAccess.recipeWidth(recipe);
                    LOG.debug("search: rejected {} on {}x{} tier {} (recipe width={}, tier={}, requiredTier={})",
                            recipeName(recipe), spec.gridSize(), spec.gridSize(), spec.tier(),
                            recipeWidth, recipeTier.tier(), recipeTier.requiredTier());
                }
                continue;
            }
            var result = resultItem(recipe, level);
            if (result == null || result.isEmpty() || result.getItem() != key.getItem()
                    || (!idOnlyOutput && !key.equals(AEItemKey.of(result)))
                    || output.amount() % result.getCount() != 0) continue;
            long ratio = output.amount() / result.getCount();
            if (ratio <= 0 || ratio > Integer.MAX_VALUE) continue;
            var ingredients = ingredients(recipe);
            if (ingredients == null || countNonEmpty(ingredients) > spec.slots()
                    || !TablePatternMatcher.matches(ingredients, pattern, ratio)) continue;
            // Native matches/assemble use actual delivered NBT during planning. Declared
            // fuzzy inputs need not satisfy a particular component variant at bind time.
            candidates.add(new BoundRecipe(recipe, (int) ratio));
        }
        LOG.debug("search: retained {} candidates; tierRejected={}", candidates.size(), tierRejected);
        return candidates.isEmpty() ? null : new TableBindHandle(spec, List.copyOf(candidates));
    }

    private static boolean tableCanCraftRecipe(TableSpec spec, Object recipe) {
        return tableCanCraftRecipe(spec, AvaritiaTableAccess.recipeTier(recipe));
    }

    private static boolean tableCanCraftRecipe(TableSpec spec,
                                               AvaritiaTableSpecs.RecipeTier recipeTier) {
        return spec.pureSpec().canCraftRecipeTier(recipeTier);
    }

    @Nullable
    private static List<ItemStack> assignIngredientsStrict(NonNullList<Ingredient> ingredients,
                                                           LinkedHashMap<AEItemKey, Long> available,
                                                           long k) {
        return assignIngredientStacks(ingredients, available, k,
                (ingredient, key) -> ingredient.test(key.toStack(1)));
    }

    @Nullable
    private static List<ItemStack> assignIngredientStacks(NonNullList<Ingredient> ingredients,
                                                          LinkedHashMap<AEItemKey, Long> available,
                                                          long k,
                                                          java.util.function.BiPredicate<Ingredient, AEItemKey> matcher) {
        var assignedKeys = assignIngredientKeys(ingredients, available, k, matcher);
        if (assignedKeys == null) {
            return null;
        }

        var stacks = new ArrayList<ItemStack>(assignedKeys.size());
        for (var key : assignedKeys) {
            stacks.add(key.toStack(1));
        }
        return List.copyOf(stacks);
    }

    @Nullable
    private static List<AEItemKey> assignIngredientKeys(NonNullList<Ingredient> ingredients,
                                                        LinkedHashMap<AEItemKey, Long> available,
                                                        long k,
                                                        java.util.function.BiPredicate<Ingredient, AEItemKey> matcher) {
        var nonEmptyIngredients = new ArrayList<Ingredient>();
        for (var ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                nonEmptyIngredients.add(ingredient);
            }
        }
        if (nonEmptyIngredients.isEmpty()) {
            return null;
        }

        var inputs = new ArrayList<AvaritiaTableIngredientAssigner.Input<AEItemKey>>(available.size());
        for (var entry : available.entrySet()) {
            inputs.add(new AvaritiaTableIngredientAssigner.Input<>(entry.getKey(), entry.getValue()));
        }
        return AvaritiaTableIngredientAssigner.assign(nonEmptyIngredients, inputs, k, matcher);
    }

    private static int countNonEmpty(NonNullList<Ingredient> ingredients) {
        int count = 0;
        for (var ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    private static List<PlannedUnit> layoutGridFromIngredients(Object recipe,
                                                               List<ItemStack> perCraftStacks,
                                                               TableSpec spec) {
        var ingredients = ingredients(recipe);
        if (ingredients == null || ingredients.isEmpty()) {
            return null;
        }

        var width = AvaritiaTableAccess.recipeWidth(recipe);
        if (width == null) {
            var assignments = AvaritiaTableGridPlanner.placeSequential(
                    spec.gridSize(), perCraftStacks);
            if (assignments.isEmpty() && !perCraftStacks.isEmpty()) {
                return null;
            }
            var planned = new ArrayList<PlannedUnit>(assignments.size());
            for (var assignment : assignments) {
                planned.add(new PlannedUnit(assignment.slot(), assignment.value().copy()));
            }
            return List.copyOf(planned);
        }

        if (width <= 0 || width > spec.gridSize()) {
            return null;
        }

        var recipeCells = new ArrayList<ItemStack>(ingredients.size());
        int nonEmptyIndex = 0;
        for (var ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                recipeCells.add(null);
                continue;
            }
            if (nonEmptyIndex >= perCraftStacks.size()) {
                return null;
            }
            recipeCells.add(perCraftStacks.get(nonEmptyIndex++).copy());
        }
        if (nonEmptyIndex != perCraftStacks.size()) {
            return null;
        }

        var assignments = AvaritiaTableGridPlanner.place(width, spec.gridSize(), recipeCells);
        if (assignments.isEmpty() && !perCraftStacks.isEmpty()) {
            return null;
        }

        var planned = new ArrayList<PlannedUnit>(assignments.size());
        for (var assignment : assignments) {
            planned.add(new PlannedUnit(assignment.slot(), assignment.value().copy()));
        }
        return List.copyOf(planned);
    }

    @Nullable
    private static Object findMatchingRecipe(ServerLevel level, CraftingContainer input,
                                             TableSpec spec) {
        for (var recipe : recipes(level)) {
            if (tableCanCraftRecipe(spec, recipe) && recipeMatches(recipe, input, level)) {
                return recipe;
            }
        }
        return null;
    }

    private static boolean canApplyRemaining(IItemHandler handler, int gridSize, CraftingContainer input,
                                             NonNullList<ItemStack> remaining, GridOffset offset) {
        if (remaining.size() != input.getContainerSize()
                || input.getWidth() != gridSize || input.getHeight() != gridSize) {
            return false;
        }

        for (int slot = 0; slot < remaining.size(); slot++) {
            int x = slot % gridSize;
            int y = slot / gridSize;
            boolean inside = x >= offset.left() && x < offset.left() + offset.width()
                    && y >= offset.top() && y < offset.top() + offset.height();
            if (!inside && !remaining.get(slot).isEmpty()) {
                return false;
            }
        }

        var currentStacks = new ArrayList<AvaritiaTableRemainderPlanner.Stack<ItemStack>>();
        var remainderStacks = new ArrayList<AvaritiaTableRemainderPlanner.Stack<ItemStack>>();
        for (int y = 0; y < offset.height(); y++) {
            for (int x = 0; x < offset.width(); x++) {
                int slot = offset.slot(x, y, gridSize);
                var current = handler.getStackInSlot(slot);
                if (!current.isEmpty() && handler.extractItem(slot, 1, true).getCount() != 1) {
                    return false;
                }
                var remainder = remaining.get(slot);
                if (!remainder.isEmpty() && !handler.isItemValid(slot, remainder)) {
                    return false;
                }
                int slotLimit = handler.getSlotLimit(slot);
                currentStacks.add(remainderStack(current, slotLimit));
                remainderStacks.add(remainderStack(remainder, slotLimit));
            }
        }
        return AvaritiaTableRemainderPlanner.canApply(
                currentStacks, remainderStacks, ItemStack::isSameItemSameTags);
    }

    private static AvaritiaTableRemainderPlanner.Stack<ItemStack> remainderStack(
            ItemStack stack, int slotLimit) {
        if (stack.isEmpty()) {
            return AvaritiaTableRemainderPlanner.Stack.empty();
        }
        return new AvaritiaTableRemainderPlanner.Stack<>(
                stack, stack.getCount(), Math.min(slotLimit, stack.getMaxStackSize()));
    }

    private static boolean applyCraftRemainders(IItemHandlerModifiable handler, int gridSize,
                                                 CraftingContainer input,
                                                 NonNullList<ItemStack> remaining,
                                                 GridOffset offset) {
        var slots = new int[offset.width() * offset.height()];
        var before = new ItemStack[slots.length];
        int index = 0;
        for (int y = 0; y < offset.height(); y++) {
            for (int x = 0; x < offset.width(); x++) {
                int slot = offset.slot(x, y, gridSize);
                slots[index] = slot;
                before[index++] = handler.getStackInSlot(slot).copy();
            }
        }

        for (int i = 0; i < slots.length; i++) {
            int slot = slots[i];
            var current = handler.getStackInSlot(slot);
            if (!current.isEmpty() && handler.extractItem(slot, 1, false).getCount() != 1) {
                LOG.warn("extract: handler failed to consume one item from slot {}", slot);
                restoreStacks(handler, slots, before);
                return false;
            }

            var remainder = remaining.get(slot);
            if (remainder.isEmpty()) {
                continue;
            }

            var after = handler.getStackInSlot(slot);
            if (after.isEmpty()) {
                handler.setStackInSlot(slot, remainder.copy());
            } else if (ItemStack.isSameItemSameTags(after, remainder)) {
                var combined = after.copy();
                combined.grow(remainder.getCount());
                handler.setStackInSlot(slot, combined);
            } else {
                LOG.warn("extract: handler state changed before remainder insertion at slot {}", slot);
                restoreStacks(handler, slots, before);
                return false;
            }
        }
        return true;
    }

    private static void restoreStacks(IItemHandlerModifiable handler, int[] slots, ItemStack[] before) {
        for (int i = 0; i < slots.length; i++) {
            handler.setStackInSlot(slots[i], before[i]);
        }
    }

    @Nullable
    private static GridOffset recipeOffset(Object recipe, CraftingContainer input, int gridSize) {
        Integer width = AvaritiaTableAccess.recipeWidth(recipe);
        Integer height = AvaritiaTableAccess.recipeHeight(recipe);
        if (width == null || height == null) {
            return new GridOffset(0, 0, gridSize, gridSize);
        }
        if (width <= 0 || height <= 0 || width > gridSize || height > gridSize) {
            return null;
        }
        // Match Re-Avaritia's getRemainingItems search order exactly:
        // left outermost, top innermost, mirrored before non-mirrored.
        for (int left = 0; left <= gridSize - width; left++) {
            for (int top = 0; top <= gridSize - height; top++) {
                if (shapedRecipeMatches(recipe, input, gridSize, width, height, left, top, true)
                        || shapedRecipeMatches(recipe, input, gridSize, width, height, left, top, false)) {
                    return new GridOffset(left, top, width, height);
                }
            }
        }
        return null;
    }

    private static boolean shapedRecipeMatches(Object recipe, CraftingContainer input, int gridSize,
                                                int width, int height, int left, int top,
                                                boolean mirrored) {
        var ingredients = ingredients(recipe);
        if (ingredients == null || ingredients.size() != width * height) {
            return false;
        }
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                int rx = x - left;
                int ry = y - top;
                Ingredient ingredient = Ingredient.EMPTY;
                if (rx >= 0 && ry >= 0 && rx < width && ry < height) {
                    int recipeX = mirrored ? width - 1 - rx : rx;
                    ingredient = ingredients.get(recipeX + ry * width);
                }
                if (!ingredient.test(input.getItem(x + y * gridSize))) {
                    return false;
                }
            }
        }
        return true;
    }

    private record GridOffset(int left, int top, int width, int height) {
        int slot(int x, int y, int gridSize) {
            return left + x + (top + y) * gridSize;
        }
    }

    private static boolean hasExactSlots(IItemHandler handler, TableSpec spec) {
        return spec.acceptsSlotCount(handler.getSlots());
    }

    /**
     * Resolves the explicit runtime profile for this table instance. The
     * handler slot count is part of the block-specific contract and must not
     * be inferred from another table's dimensions.
     */
    @Nullable
    private static TableSpec runtimeTableSpec(@Nullable TableSpec declared,
                                              @Nullable IItemHandler handler) {
        if (declared == null || handler == null) {
            return null;
        }
        return declared.acceptsSlotCount(handler.getSlots()) ? declared : null;
    }

    private static void diagnoseTableOnce(ServerLevel level, TableSpec spec,
                                          BlockEntity be, @Nullable IItemHandler handler) {
        String diagnosticKey = spec.blockId() + "|" + be.getClass().getName() + "|"
                + (handler == null ? "unresolved" : handler.getClass().getName());
        if (!DIAGNOSED_TABLE_CLASSES.add(diagnosticKey)) {
            return;
        }

        var recipeType = recipeType();
        int recipeCount = recipeType == null ? -1 : recipes(level).size();
        var effectiveSpec = runtimeTableSpec(spec, handler);
        Object[] details = {
                avaritiaVersion(), spec.blockId(), be.getClass().getName(),
                AvaritiaTableAccess.matchedTableClassName(), AvaritiaTableAccess.inventoryMethodName(),
                handler == null ? "unresolved" : handler.getClass().getName(),
                handler == null ? -1 : handler.getSlots(), spec.slots(),
                effectiveSpec == null ? "rejected" : effectiveSpec.gridSize() + "x" + effectiveSpec.gridSize(),
                recipeTypeId(), recipeType == null ? "no" : recipeType.getClass().getName(), recipeCount,
                AvaritiaTableAccess.shapedWidthMethodName(), AvaritiaTableAccess.tierMethodNames()
        };
        String message = "[ae2ltpp] Avaritia table diagnostic: version={}, block={}, blockEntity={}, "
                + "matchedTableClass={}, inventoryMethod={}, handler={}, slots={}, declaredSlots={}, "
                + "layoutProfile={}, recipeType={} (resolved={}), recipes={}, shapedWidthMethod={}, tierMethods={}";
        if (handler == null || effectiveSpec == null || !hasExactSlots(handler, effectiveSpec)) {
            LOG.warn(message, details);
        } else {
            LOG.info(message, details);
        }
    }

    private static String avaritiaVersion() {
        try {
            return ModList.get().getModContainerById(MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (RuntimeException | LinkageError e) {
            return "unknown";
        }
    }

    private static String recipeName(Object recipe) {
        if (recipe instanceof Recipe<?> typedRecipe) {
            try {
                return typedRecipe.getId() + " [" + recipe.getClass().getName() + "]";
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        return recipe.getClass().getName();
    }

    private static List<ItemStack> stacksForPlan(int slots, List<PlannedUnit> planned) {
        var stacks = NonNullList.withSize(slots, ItemStack.EMPTY);
        for (var unit : planned) {
            stacks.set(unit.slot(), unit.stack().copy());
        }
        return stacks;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean recipeMatches(Object recipe, CraftingContainer input, ServerLevel level) {
        return recipeMatchesChecked(recipe, input, level).value();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CheckedValue<Boolean> recipeMatchesChecked(Object recipe, CraftingContainer input,
                                                               ServerLevel level) {
        try {
            return new CheckedValue<>(recipe instanceof Recipe<?> r
                    && ((Recipe) r).matches(input, level), false);
        } catch (RuntimeException | LinkageError e) {
            return new CheckedValue<>(false, true);
        }
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static NonNullList<ItemStack> remainingItems(Object recipe, CraftingContainer input) {
        // Re-Avaritia's shaped table recipe overrides the handler-based API and
        // returns a list indexed by the physical table slots. Calling the
        // vanilla Container overload would bypass that implementation.
        var handlerRemainders = AvaritiaTableAccess.remainingItems(recipe, input);
        if (handlerRemainders != null) {
            return handlerRemainders;
        }
        try {
            return recipe instanceof Recipe<?> r
                    ? ((Recipe) r).getRemainingItems(input)
                    : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static ItemStack resultItem(Object recipe, ServerLevel level) {
        return resultItemChecked(recipe, level).value();
    }

    private static CheckedValue<ItemStack> resultItemChecked(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> r)) {
            return new CheckedValue<>(null, false);
        }
        try {
            return new CheckedValue<>(r.getResultItem(level.registryAccess()), false);
        } catch (RuntimeException | LinkageError e) {
            return new CheckedValue<>(null, true);
        }
    }

    @Nullable
    private static NonNullList<Ingredient> ingredients(Object recipe) {
        return ingredientsChecked(recipe).value();
    }

    private static CheckedValue<NonNullList<Ingredient>> ingredientsChecked(Object recipe) {
        if (!(recipe instanceof Recipe<?> r)) {
            return new CheckedValue<>(null, false);
        }
        try {
            return new CheckedValue<>(r.getIngredients(), false);
        } catch (RuntimeException | LinkageError e) {
            return new CheckedValue<>(null, true);
        }
    }

    // A successful recipe-type lookup is stable for the JVM lifetime. A null
    // lookup is deliberately not cached because another mod may first call the
    // adapter before Avaritia has finished registering its recipe type.
    private static volatile RecipeType<?> cachedRecipeType;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Recipe<?>> recipes(ServerLevel level) {
        var type = recipeType();
        if (type == null) {
            LOG.debug("recipes: recipe type {} is not registered yet", recipeTypeId());
            return List.of();
        }
        return (List<Recipe<?>>) (List<?>) level.getRecipeManager().getAllRecipesFor((RecipeType) type);
    }

    @Nullable
    private static RecipeType<?> recipeType() {
        var type = cachedRecipeType;
        if (type == null) {
            synchronized (AvaritiaTableAdapter.class) {
                type = cachedRecipeType;
                if (type == null) {
                    type = AdapterRecipeTypes.find(recipeTypeId());
                    if (type != null) {
                        cachedRecipeType = type;
                    }
                }
            }
        }
        return type;
    }

    private static ResourceLocation recipeTypeId() {
        return AvaritiaNeoSupport.isAvailable() ? avaritiaId("extreme_crafting") : RECIPE_TYPE_ID;
    }

    @Nullable
    private static TableSpec tableSpec(BlockState state) {
        var id = blockId(state);
        // Re-Avaritia 1.4.1 runtime profiles: Sculk 3x3/tier 1,
        // Nether 5x5/tier 2, End 7x7/tier 3, Extreme 9x9/tier 4.
        var pureSpec = AvaritiaTableSpecs.find(id.getNamespace(), id.getPath());
        return pureSpec == null ? null : new TableSpec(id, pureSpec, pureSpec.gridSize());
    }

    private static boolean isAvaritiaLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation blockId(BlockState state) { return AdapterBlocks.idOf(state); }

    private static ResourceLocation avaritiaId(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private record TableSpec(ResourceLocation blockId,
                             AvaritiaTableSpecs.TableSpec pureSpec,
                             int runtimeGridSize) {
        int gridSize() {
            return runtimeGridSize;
        }

        int tier() {
            return pureSpec.tier();
        }

        int slots() {
            return Math.multiplyExact(runtimeGridSize, runtimeGridSize);
        }

        boolean acceptsSlotCount(int actualSlots) {
            return actualSlots == slots();
        }

        TableSpec withGridSize(int size) {
            return new TableSpec(blockId, pureSpec, size);
        }

        boolean matches(BlockState state) {
            return AvaritiaTableAdapter.blockId(state).equals(blockId);
        }
    }

    private record PlannedUnit(int slot, ItemStack stack) {
        PlannedUnit {
            Objects.requireNonNull(stack, "stack");
        }
    }

    private record TableBindHandle(TableSpec spec, List<BoundRecipe> recipes) {
    }

    private record CheckedValue<T>(@Nullable T value, boolean exception) {
    }

}
