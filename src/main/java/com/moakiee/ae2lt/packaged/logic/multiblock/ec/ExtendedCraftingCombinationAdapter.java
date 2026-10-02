package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.shared.CucumberInventoryAccess;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
 * Runtime adapter for ExtendedCrafting's Combination Crafter — a central
 * {@code crafting_core} surrounded by up to 48 {@code pedestal} blocks
 * within a 7×7 horizontal AOE (same Y level as the core). Pure reflection.
 *
 * <p>The Core stores a catalyst in slot 0 and a {@link
 * net.neoforged.neoforge.energy.EnergyStorage}-backed FE buffer. Pedestals
 * each hold one ingredient. Once enough power has accumulated, the BE
 * consumes the catalyst + pedestal stacks and writes the result back into
 * Core slot 0.
 *
 * <p>Binding mode is {@link BindingMode#REAL} — there's no equivalent
 * virtual evaluation since the recipe demands physical pedestals + tick
 * progress. We deliberately do not gate dispatch on FE balance; external
 * sources can keep refilling the Core while the BE waits.
 */
public final class ExtendedCraftingCombinationAdapter implements MultiblockAdapter {

    private static final String MOD_ID = "extendedcrafting";
    private static final ResourceLocation CORE_BLOCK = ecId("crafting_core");
    private static final ResourceLocation PEDESTAL_BLOCK = ecId("pedestal");
    private static final ResourceLocation RECIPE_TYPE_ID = ecId("combination");
    private static final int CORE_SLOT = 0;
    private static final int PEDESTAL_SLOT = 0;
    private static final int AOE_RADIUS = 3;
    private static final int MAX_INGREDIENTS = 48;

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null && isEcLoaded() && blockId(be.getBlockState()).equals(CORE_BLOCK);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.EC_COMBINATION;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) return null;
        if (!hasSingleItemOutput(pattern)) return null;

        var recipe = findCandidateRecipe(level, pattern);
        if (recipe == null) return null;
        return new BindingResult(new CombinationBindHandle(recipe), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        if (!(handle instanceof CombinationBindHandle bind)) return false;
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) return false;

        var inv = CucumberInventoryAccess.get(be);
        if (inv == null || inv.getSlots() <= CORE_SLOT) return false;
        if (!inv.getStackInSlot(CORE_SLOT).isEmpty()) return false;
        if (ExtendedCraftingCombinationAccess.getProgress(be) != 0) return false;

        var ingredients = ExtendedCraftingCombinationAccess.getRecipeIngredients(bind.recipe());
        if (ingredients == null) return false;
        int needed = ingredients.size();
        if (needed <= 0 || needed > MAX_INGREDIENTS) return false;

        var pedestals = findEmptyPedestals(level, mainPos);
        return pedestals != null && pedestals.size() >= needed;
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        if (!(handle instanceof CombinationBindHandle bind)
                || !canDispatch(level, mainPos, handle)) return null;
        var be = level.getBlockEntity(mainPos);
        if (be == null) return null;

        var catalyst = ExtendedCraftingCombinationAccess.getCatalyst(bind.recipe());
        var ingredients = ExtendedCraftingCombinationAccess.getRecipeIngredients(bind.recipe());
        if (catalyst == null || ingredients == null) return null;

        var match = matchInputs(catalyst, ingredients, inputs);
        if (match == null) return null;

        var emptyPedestals = findEmptyPedestals(level, mainPos);
        if (emptyPedestals == null || emptyPedestals.size() < ingredients.size()) return null;

        var targets = new ArrayList<TargetSlot>(ingredients.size() + 1);
        targets.add(new TargetSlot(
                level, mainPos, null,
                List.of(match.catalyst().toGenericStack()),
                InsertionStrategy.CUSTOM,
                coreInserter(level, mainPos, match.catalyst())));

        for (int i = 0; i < match.ingredients().size(); i++) {
            var unit = match.ingredients().get(i);
            var pedestalPos = emptyPedestals.get(i);
            targets.add(new TargetSlot(
                    level, pedestalPos, null,
                    List.of(unit.toGenericStack()),
                    InsertionStrategy.CUSTOM,
                    pedestalInserter(level, pedestalPos, unit)));
        }

        // Refresh recipe state after all inputs arrive. The machine's own ticker
        // still owns FE consumption and crafting progress.
        return new DispatchPlan(List.copyOf(targets),
                () -> ExtendedCraftingCombinationAccess.refreshRecipe(be));
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) return List.of();

        var inv = CucumberInventoryAccess.get(be);
        if (inv == null || inv.getSlots() <= CORE_SLOT) return List.of();

        var stack = inv.getStackInSlot(CORE_SLOT);
        if (stack.isEmpty()) return List.of();

        var key = AEItemKey.of(stack);
        // Slot zero holds both catalyst and product. Unlike a dedicated output
        // slot, an arbitrary idle stack here is not sufficient proof of output.
        if (!filter.matches(key)) return List.of();
        // Avoid an EC pedestal scan for empty slots or non-output inputs.
        if (!ExtendedCraftingCombinationAccess.isIdleWithoutRecipe(be)) return List.of();

        if (!(inv instanceof IItemHandlerModifiable modifiable)) return List.of();
        var copy = stack.copy();
        modifiable.setStackInSlot(CORE_SLOT, ItemStack.EMPTY);
        be.setChanged();
        return List.of(new GenericStack(AEItemKey.of(copy), copy.getCount()));
    }

    // ===== Recipe matching =====

    @Nullable
    private static Object findCandidateRecipe(ServerLevel level, IPatternDetails pattern) {
        for (var holder : recipes(level)) {
            var recipe = holder.value();
            var result = ExtendedCraftingCombinationAccess.getResultItem(recipe, level);
            if (result == null || result.isEmpty()) continue;
            if (!outputMatches(pattern, result)) continue;
            return recipe;
        }
        return null;
    }

    /**
     * Greedy shapeless match: catalyst gets a single unit from any input
     * that satisfies {@code recipe.getInput()}; ingredients each pick the
     * first unused unit that satisfies them. Match order on the ingredient
     * side is the recipe's declared order, which is also what the BE checks
     * via {@code RecipeMatcher.findMatches} at completion time.
     */
    @Nullable
    private static InputMatch matchInputs(Ingredient catalyst, List<Ingredient> ingredients,
                                          KeyCounter[] inputs) {
        var units = expandUnits(inputs, 1 + ingredients.size());
        if (units == null || units.size() != 1 + ingredients.size()) return null;

        boolean[] used = new boolean[units.size()];
        PlannedUnit catalystUnit = null;
        for (int i = 0; i < units.size(); i++) {
            if (catalyst.test(units.get(i).stack())) {
                catalystUnit = units.get(i);
                used[i] = true;
                break;
            }
        }
        if (catalystUnit == null) return null;

        var pedestalUnits = new ArrayList<PlannedUnit>(ingredients.size());
        for (var ing : ingredients) {
            PlannedUnit picked = null;
            for (int i = 0; i < units.size(); i++) {
                if (used[i]) continue;
                if (ing.test(units.get(i).stack())) {
                    used[i] = true;
                    picked = units.get(i);
                    break;
                }
            }
            if (picked == null) return null;
            pedestalUnits.add(picked);
        }
        return new InputMatch(catalystUnit, pedestalUnits);
    }

    // ===== Inserters =====

    private static BiFunction<GenericStack, Actionable, Long> coreInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (stack.amount() != 1 || !unit.key().equals(stack.what())) return 0L;
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(CORE_BLOCK)) return 0L;
            if (ExtendedCraftingCombinationAccess.getProgress(be) != 0) return 0L;
            var inv = CucumberInventoryAccess.get(be);
            if (!(inv instanceof IItemHandlerModifiable modifiable)) return 0L;
            if (modifiable.getSlots() <= CORE_SLOT) return 0L;
            if (!modifiable.getStackInSlot(CORE_SLOT).isEmpty()) return 0L;
            if (mode == Actionable.MODULATE) {
                modifiable.setStackInSlot(CORE_SLOT, unit.stack());
                be.setChanged();
            }
            return 1L;
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> pedestalInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (stack.amount() != 1 || !unit.key().equals(stack.what())) return 0L;
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(PEDESTAL_BLOCK)) return 0L;
            var inv = CucumberInventoryAccess.get(be);
            if (!(inv instanceof IItemHandlerModifiable modifiable)) return 0L;
            if (modifiable.getSlots() <= PEDESTAL_SLOT) return 0L;
            if (!modifiable.getStackInSlot(PEDESTAL_SLOT).isEmpty()) return 0L;
            if (mode == Actionable.MODULATE) {
                modifiable.setStackInSlot(PEDESTAL_SLOT, unit.stack());
                be.setChanged();
            }
            return 1L;
        };
    }

    // ===== World scanning =====

    /**
     * Walks the {@code [-3,0,-3]..[+3,0,+3]} AOE in X→Z order (same iteration
     * as {@code BlockPos.betweenClosedStream}) collecting empty pedestals.
     * The Core's own ingredient scan uses identical order, so the i-th
     * assigned ingredient maps cleanly to the i-th BE-side pedestal at
     * completion. An occupied or unreadable pedestal area is not ready for
     * another complete recipe and returns null.
     */
    @Nullable
    private static List<BlockPos> findEmptyPedestals(ServerLevel level, BlockPos mainPos) {
        var result = new ArrayList<BlockPos>();
        var min = mainPos.offset(-AOE_RADIUS, 0, -AOE_RADIUS);
        var max = mainPos.offset(AOE_RADIUS, 0, AOE_RADIUS);
        var cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                cursor.set(x, mainPos.getY(), z);
                if (cursor.equals(mainPos)) continue;
                if (!level.isLoaded(cursor)) return null;
                if (!blockId(level.getBlockState(cursor)).equals(PEDESTAL_BLOCK)) continue;
                var be = level.getBlockEntity(cursor);
                if (be == null) return null;
                var inv = CucumberInventoryAccess.get(be);
                if (inv == null || inv.getSlots() <= PEDESTAL_SLOT) return null;
                // EC includes every occupied pedestal when matching. Filling
                // other empty pedestals would accumulate incompatible inputs.
                if (!inv.getStackInSlot(PEDESTAL_SLOT).isEmpty()) return null;
                result.add(cursor.immutable());
            }
        }
        return result;
    }

    // ===== Helpers =====

    @Nullable
    private static List<PlannedUnit> expandUnits(KeyCounter[] inputs, int cap) {
        var units = new ArrayList<PlannedUnit>();
        long total = 0;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) return null;
                long amount = entry.getLongValue();
                if (amount <= 0) continue;
                total += amount;
                if (total > cap) return null;
                for (long i = 0; i < amount; i++) {
                    units.add(new PlannedUnit(itemKey));
                }
            }
        }
        return units;
    }

    private static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.size() == 1 && outputs.getFirst().what() instanceof AEItemKey;
    }

    private static boolean outputMatches(IPatternDetails pattern, ItemStack result) {
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1) return false;
        var expected = outputs.getFirst();
        if (!(expected.what() instanceof AEItemKey expectedKey) || expected.amount() != result.getCount()) {
            return false;
        }
        var actual = AEItemKey.of(result);
        return OverloadPatternSemantics.isIdOnlyOutput(pattern, 0)
                ? expectedKey.dropSecondary().equals(actual.dropSecondary())
                : expectedKey.equals(actual);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipes(ServerLevel level) {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(RECIPE_TYPE_ID)
                .map(type -> (List<RecipeHolder<?>>) (List<?>) level.getRecipeManager()
                        .getAllRecipesFor((RecipeType) type))
                .orElse(List.of());
    }

    private static boolean isEcLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static ResourceLocation ecId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
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

    private record InputMatch(PlannedUnit catalyst, List<PlannedUnit> ingredients) {}

    private record CombinationBindHandle(Object recipe) {}

    // ===== Reflection =====

}
