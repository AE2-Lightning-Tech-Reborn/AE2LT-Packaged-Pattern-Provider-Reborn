package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import java.lang.reflect.Method;
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
import net.minecraftforge.items.IItemHandler;
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
import com.moakiee.ae2lt.packaged.logic.multiblock.SlotHarvestOwnership;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionBootstrap;

/**
 * Runtime adapter for ExtendedCrafting's Ender Crafter — a 3x3 tick-driven
 * table that requires at least one neighbouring {@code ender_alternator}
 * within a 7x7x7 box. Pure reflection: ExtendedCrafting is not a compile
 * dependency.
 *
 * <p>Binding mode is {@link BindingMode#REAL}: the recipe always has a fixed
 * crafting time (default 60s, min 1s with many alternators) so we cannot
 * pretend it's instantaneous. Items are written into slots 0-8 of the
 * crafter's internal {@code BaseItemStackHandler}; the BE's own tick then
 * advances {@code progress} until it commits the result into slot 9, which
 * {@link #extractOutputs} drains back to AE.
 */
public final class ExtendedCraftingEnderCrafterAdapter implements MultiblockAdapter {

    private static final Logger LOG = LoggerFactory.getLogger("ae2ltpp/ec-ender-crafter");

    private static final String MOD_ID = "extendedcrafting";
    private static final ResourceLocation CRAFTER_BLOCK = ecId("ender_crafter");
    private static final ResourceLocation RECIPE_TYPE_ID = ecId("ender_crafter");
    private static final int GRID_SIZE = 9;
    private static final int OUTPUT_SLOT = 9;
    private static final String HARVEST_STATE = "ec_ender_crafter:harvest";

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null && isEcLoaded() && blockId(be.getBlockState()).equals(CRAFTER_BLOCK);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.EC_ENDER;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) return null;
        if (!hasSingleItemOutput(pattern)) return null;

        var recipe = findCandidateRecipe(level, pattern);
        if (recipe == null) return null;
        return new BindingResult(new EnderBindHandle(recipe), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        return canDispatch(level, mainPos, handle, AdapterPersistentScope.NOOP);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle,
                               AdapterPersistentScope scope) {
        if (!(handle instanceof EnderBindHandle)) {
            return false;
        }
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) return false;

        var inv = EcReflection.getInventory(be);
        if (inv == null || inv.getSlots() < GRID_SIZE + 1) return false;

        for (int i = 0; i < GRID_SIZE; i++) {
            if (!inv.getStackInSlot(i).isEmpty()) return false;
        }
        if (!inv.getStackInSlot(OUTPUT_SLOT).isEmpty()) return false;
        // Alternator presence isn't checked: it's only a speed-up dependency
        // that the BE re-scans every tick, so the player can plug one in
        // after dispatch and the craft will still advance.
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
        if (!(handle instanceof EnderBindHandle bind)
                || !canDispatch(level, mainPos, handle, scope)) return null;
        var be = level.getBlockEntity(mainPos);
        if (be == null) return null;
        var inv = EcReflection.getInventory(be);
        if (inv == null || inv.getSlots() < GRID_SIZE + 1) return null;
        var expectedOutput = EcReflection.getResultItem(bind.recipe(), level);
        if (expectedOutput == null || expectedOutput.isEmpty()) return null;
        var layout = assignGrid(bind.recipe(), inputs);
        if (layout == null) return null;

        var targets = new ArrayList<TargetSlot>(layout.size());
        for (var placement : layout) {
            targets.add(new TargetSlot(
                    level, mainPos, null,
                    List.of(placement.unit().toGenericStack()),
                    InsertionStrategy.CUSTOM,
                    gridInserter(level, mainPos, placement.slot(), placement.unit())));
        }
        return new DispatchPlan(
                List.copyOf(targets),
                () -> SlotHarvestOwnership.store(level, mainPos, scope, HARVEST_STATE,
                        expectedOutput, recipeId(bind.recipe())),
                (accepted, recovered) -> recoverPartialDispatch(
                        level, mainPos, targets, layout, accepted, recovered));
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
        if (be == null || !recognizesMain(level, mainPos, be)) return List.of();
        var inv = EcReflection.getInventory(be);
        if (inv == null || inv.getSlots() <= OUTPUT_SLOT) return List.of();
        var ownership = SlotHarvestOwnership.load(level, mainPos, scope, HARVEST_STATE);
        if (ownership == null || SlotHarvestOwnership.expired(level, ownership)) return List.of();
        var stack = inv.getStackInSlot(OUTPUT_SLOT);
        if (stack.isEmpty()) return List.of();
        var key = AEItemKey.of(stack);
        if (!filter.matches(key)) return List.of();
        int expectedCount = ownership.expectedOutput().getCount();
        int attributable = ownership.attributableCount(stack);
        if (attributable != expectedCount || !(inv instanceof IItemHandlerModifiable modifiable)) {
            return List.of();
        }
        var extracted = stack.copy();
        extracted.setCount(expectedCount);
        if (stack.getCount() == expectedCount) {
            modifiable.setStackInSlot(OUTPUT_SLOT, ItemStack.EMPTY);
        } else {
            var remainder = stack.copy();
            remainder.shrink(expectedCount);
            modifiable.setStackInSlot(OUTPUT_SLOT, remainder);
        }
        be.setChanged();
        SlotHarvestOwnership.clear(level, mainPos, scope, HARVEST_STATE);
        return List.of(new GenericStack(AEItemKey.of(extracted), extracted.getCount()));
    }

    // ===== Recipe matching =====

    @Nullable
    private static Object findCandidateRecipe(ServerLevel level, IPatternDetails pattern) {
        for (var recipe : recipes(level)) {
            var result = EcReflection.getResultItem(recipe, level);
            if (result == null || result.isEmpty()) continue;
            if (!outputMatches(pattern, result)) continue;
            return recipe;
        }
        return null;
    }

    /**
     * Maps the runtime {@link KeyCounter} inputs onto the recipe's grid.
     *
     * <p>Shaped recipes are detected by reading the inner
     * {@link net.minecraft.world.item.crafting.ShapedRecipePattern} via
     * reflection (the field is private, so {@code getDeclaredField} is
     * needed). When that succeeds the ingredient list is treated as a
     * row-major width*height grid and items go into the matching 3x3 slot,
     * preserving the recipe's shape so the BE's pattern.matches() check
     * passes.
     *
     * <p>If reflection can't find the pattern but {@code getIngredients()}
     * returns exactly 9 entries, we still treat it as a padded 3x3 layout
     * (Ender / Flux Crafter recipes are always 3x3 internally) — covers the
     * common case where ingredient EMPTY padding already encodes the shape.
     *
     * <p>Otherwise we fall back to shapeless packing into the first N slots
     * — only safe for true shapeless recipes whose matches() walks the input
     * via {@code RecipeMatcher.findMatches}.
     */
    @Nullable
    private static List<GridPlacement> assignGrid(Object recipe, KeyCounter[] inputs) {
        var ingredients = recipeIngredients(recipe);
        if (ingredients == null || ingredients.isEmpty() || ingredients.size() > GRID_SIZE) return null;

        var units = expandUnits(inputs);
        if (units == null || units.size() != countNonEmpty(ingredients)) return null;

        var orderedIngredients = new ArrayList<Ingredient>();
        var gridSlots = new ArrayList<Integer>();

        int width = EcReflection.getShapedWidth(recipe);
        int height = EcReflection.getShapedHeight(recipe);
        boolean isShaped = width > 0 && height > 0 && width <= 3 && height <= 3;

        // Fallback: a 9-element ingredient list is always a 3x3 padded shape.
        if (!isShaped && ingredients.size() == GRID_SIZE) {
            width = 3;
            height = 3;
            isShaped = true;
        }

        if (isShaped) {
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int recipeIndex = row * width + col;
                    if (recipeIndex >= ingredients.size()) continue;
                    var ingredient = ingredients.get(recipeIndex);
                    if (!ingredient.isEmpty()) {
                        orderedIngredients.add(ingredient);
                        gridSlots.add(row * 3 + col);
                    }
                }
            }
        } else {
            int gridSlot = 0;
            for (var ingredient : ingredients) {
                if (!ingredient.isEmpty()) {
                    orderedIngredients.add(ingredient);
                    gridSlots.add(gridSlot++);
                }
            }
        }
        var assigned = BoundedConstraintMatcher.matchExactly(units,
                orderedIngredients.stream().<java.util.function.Predicate<PlannedUnit>>map(
                        ingredient -> unit -> ingredient.test(unit.stack())).toList());
        if (assigned == null || assigned.size() != gridSlots.size()) {
            return null;
        }
        var placements = new ArrayList<GridPlacement>(assigned.size());
        for (int i = 0; i < assigned.size(); i++) {
            placements.add(new GridPlacement(gridSlots.get(i), assigned.get(i)));
        }
        return List.copyOf(placements);
    }

    private static int countNonEmpty(List<Ingredient> ingredients) {
        int n = 0;
        for (var ing : ingredients) {
            if (!ing.isEmpty()) n++;
        }
        return n;
    }

    @Nullable
    private static List<Ingredient> recipeIngredients(Object recipe) {
        if (!(recipe instanceof Recipe<?> r)) return null;
        try {
            return new ArrayList<>(r.getIngredients());
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    // ===== Inserter =====

    private static BiFunction<GenericStack, Actionable, Long> gridInserter(
            ServerLevel level, BlockPos pos, int slot, PlannedUnit unit) {
        return (stack, mode) -> {
            if (stack.amount() != 1 || !unit.key().equals(stack.what())) return 0L;
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(CRAFTER_BLOCK)) return 0L;
            var inv = EcReflection.getInventory(be);
            if (!(inv instanceof IItemHandlerModifiable modifiable)) return 0L;
            if (modifiable.getSlots() <= slot) return 0L;
            if (!modifiable.getStackInSlot(slot).isEmpty()) return 0L;
            if (mode == Actionable.MODULATE) {
                modifiable.setStackInSlot(slot, unit.stack());
                be.setChanged();
            }
            return 1L;
        };
    }

    private static void recoverPartialDispatch(ServerLevel level, BlockPos mainPos,
                                               List<TargetSlot> targets, List<GridPlacement> layout,
                                               List<AcceptedInsertion> accepted,
                                               Consumer<GenericStack> recovered) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !blockId(be.getBlockState()).equals(CRAFTER_BLOCK)) return;
        var inv = EcReflection.getInventory(be);
        if (!(inv instanceof IItemHandlerModifiable modifiable)
                || inv.getSlots() <= OUTPUT_SLOT
                || !inv.getStackInSlot(OUTPUT_SLOT).isEmpty()) {
            return;
        }
        // Dispatch is gated on a fully empty grid, and this recovery runs in
        // the same tick as the failed modulation, so a slot still holding
        // exactly the accepted stack proves the crafter has not consumed it.
        boolean changed = false;
        for (var insertion : accepted) {
            int index = targets.indexOf(insertion.target());
            if (index < 0 || index >= layout.size()
                    || !(insertion.stack().what() instanceof AEItemKey key)
                    || insertion.stack().amount() != 1) {
                continue;
            }
            int slot = layout.get(index).slot();
            if (slot < 0 || slot >= inv.getSlots()) continue;
            var expected = key.toStack(1);
            var current = inv.getStackInSlot(slot);
            if (current.getCount() != 1 || !ItemStack.isSameItemSameTags(current, expected)) {
                continue;
            }
            if (modifiable.extractItem(slot, 1, true).getCount() != 1) continue;
            var extracted = modifiable.extractItem(slot, 1, false);
            if (extracted.getCount() != 1 || !ItemStack.isSameItemSameTags(extracted, expected)) {
                continue;
            }
            changed = true;
            recovered.accept(insertion.stack());
        }
        if (changed) {
            be.setChanged();
        }
    }

    // ===== Helpers =====

    @Nullable
    private static List<PlannedUnit> expandUnits(KeyCounter[] inputs) {
        var units = new ArrayList<PlannedUnit>();
        long total = 0;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) return null;
                long amount = entry.getLongValue();
                if (amount <= 0) continue;
                total += amount;
                if (total > GRID_SIZE) return null;
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

    private record GridPlacement(int slot, PlannedUnit unit) {}

    private record EnderBindHandle(Object recipe) {}

    // ===== Reflection =====

    private static final class EcReflection {
        private static final String BASE_INVENTORY_CLASS =
                "com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity";
        private static final String SHAPED_RECIPE_PATTERN =
                "net.minecraft.world.item.crafting.ShapedRecipePattern";

        private static final ReflectionBootstrap LOOKUP = new ReflectionBootstrap("ec-ender-crafter");
        private static volatile @Nullable Method getInventoryMethod;
        private static volatile @Nullable Method shapedWidthMethod;
        private static volatile @Nullable Method shapedHeightMethod;
        private static volatile @Nullable Class<?> shapedRecipePatternClass;

        @Nullable
        static IItemHandler getInventory(BlockEntity be) {
            ensureLookup();
            if (getInventoryMethod == null) return null;
            try {
                var value = getInventoryMethod.invoke(be);
                return value instanceof IItemHandler h ? h : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return null;
            }
        }

        @Nullable
        static ItemStack getResultItem(Object recipe, ServerLevel level) {
            if (!(recipe instanceof Recipe<?> r)) return null;
            try {
                return r.getResultItem(level.registryAccess());
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        static int getShapedWidth(Object recipe) {
            return getShapedDimension(recipe, shapedWidthMethod, "getWidth");
        }

        static int getShapedHeight(Object recipe) {
            return getShapedDimension(recipe, shapedHeightMethod, "getHeight");
        }

        /**
         * 1.20.5+ keeps shaped dimensions in {@code ShapedRecipePattern}; on
         * 1.20.x EC's shaped recipes declare {@code getWidth()}/{@code
         * getHeight()} directly (shapeless variants have neither, which
         * classifies them as shapeless).
         */
        private static int getShapedDimension(Object recipe, @Nullable Method patternMethod,
                                              String directName) {
            if (patternMethod != null && shapedRecipePatternClass != null) {
                var pattern = findPattern(recipe);
                if (pattern != null) {
                    try {
                        var value = patternMethod.invoke(pattern);
                        if (value instanceof Integer i) return i;
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    }
                }
            }
            var direct = ReflectionSupport.findMethodCached(recipe.getClass(), directName).orElse(null);
            if (direct == null) return -1;
            try {
                var value = direct.invoke(recipe);
                return value instanceof Integer i ? i : -1;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return -1;
            }
        }

        /**
         * Walks the recipe class hierarchy looking for any declared field
         * whose type is {@code ShapedRecipePattern}. Avoids hard-coding the
         * field name ({@code pattern}) and copes with private visibility.
         */
        @Nullable
        private static Object findPattern(Object recipe) {
            var patternClass = shapedRecipePatternClass;
            if (patternClass == null) return null;
            for (Class<?> c = recipe.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (var f : c.getDeclaredFields()) {
                    if (patternClass.isAssignableFrom(f.getType())) {
                        try {
                            f.setAccessible(true);
                            return f.get(recipe);
                        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                            return null;
                        }
                    }
                }
            }
            return null;
        }

        private static void ensureLookup() {
            LOOKUP.run(() -> {
                try {
                    var baseInventoryClass = Class.forName(BASE_INVENTORY_CLASS);
                    getInventoryMethod = baseInventoryClass.getMethod("getInventory");
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    LOG.warn("EnderCrafter reflection: BaseInventoryTileEntity#getInventory lookup failed: {}",
                            e.toString());
                }
                try {
                    var patternClass = Class.forName(SHAPED_RECIPE_PATTERN);
                    shapedRecipePatternClass = patternClass;
                    shapedWidthMethod = patternClass.getMethod("width");
                    shapedHeightMethod = patternClass.getMethod("height");
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    // 1.20.x: no ShapedRecipePattern; shaped EC recipes carry
                    // their own getWidth()/getHeight() — resolved per call.
                    shapedRecipePatternClass = null;
                    shapedWidthMethod = null;
                    shapedHeightMethod = null;
                }
            });
        }
    }
}
