package com.moakiee.ae2lt.packaged.logic.multiblock.aa;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.OverloadPatternSemantics;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterBlocks;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

/**
 * Runtime adapter for Actually Additions' Empowerer setup.
 * Pure reflection: Actually Additions is not a compile dependency.
 *
 * Layout: 1 Empowerer + 4 Display Stands exactly 3 blocks away on the
 * horizontal cardinal axes. AA itself uses the same offsets when ticking.
 */
public final class ActuallyAdditionsEmpowererAdapter implements MultiblockAdapter {

    private static final ResourceLocation EMPOWERER_BLOCK = AaSupport.aaId("empowerer");
    private static final ResourceLocation DISPLAY_STAND_BLOCK = AaSupport.aaId("display_stand");
    private static final ResourceLocation RECIPE_TYPE_ID = AaSupport.aaId("empower");
    private static final int MAX_INPUT_UNITS = 64;

    private static final BlockPos[] DISPLAY_STAND_OFFSETS = {
            new BlockPos(0, 0, 3),
            new BlockPos(-3, 0, 0),
            new BlockPos(0, 0, -3),
            new BlockPos(3, 0, 0),
    };

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null
                && AaSupport.isActuallyAdditionsLoaded()
                && AdapterBlocks.idOf(be.getBlockState()).equals(EMPOWERER_BLOCK)
                && ActuallyAdditionsEmpowererAccess.isEmpowerer(be);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return com.moakiee.ae2lt.packaged.item.AdapterIds.AA_EMPOWERER;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return null;
        }
        if (!AaSupport.hasSingleItemOutput(pattern)) {
            return null;
        }
        var recipe = findCandidateRecipe(level, pattern);
        if (recipe == null) {
            return null;
        }
        return new BindingResult(new EmpowererBindHandle(recipe), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        if (!(handle instanceof EmpowererBindHandle bind)) return false;
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) return false;
        if (ActuallyAdditionsEmpowererAccess.getProcessTime(be) > 0) return false;

        var inv = ActuallyAdditionsEmpowererAccess.getHandler(be);
        if (inv == null || inv.getSlots() < 1 || !inv.getStackInSlot(0).isEmpty()) return false;

        var stands = findEmptyDisplayStands(level, mainPos);
        if (stands == null) return false;
        return standsHaveTickEnergy(level, stands, bind.recipe());
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        if (!(handle instanceof EmpowererBindHandle bind)) return null;
        var standPositions = findEmptyDisplayStands(level, mainPos);
        if (standPositions == null) {
            return null;
        }

        var units = expandInputUnits(inputs);
        if (units == null || units.size() != 5) {
            return null;
        }

        var match = assignInputsToRecipe(bind.recipe(), units);
        if (match == null) {
            return null;
        }

        var targets = new ArrayList<TargetSlot>(5);
        targets.add(new TargetSlot(
                level, mainPos, null,
                List.of(match.base().toGenericStack()),
                InsertionStrategy.CUSTOM,
                empowererInserter(level, mainPos, match.base())));

        for (int i = 0; i < match.modifiers().size(); i++) {
            var unit = match.modifiers().get(i);
            var standPos = standPositions.get(i);
            targets.add(new TargetSlot(
                    level, standPos, null,
                    List.of(unit.toGenericStack()),
                    InsertionStrategy.CUSTOM,
                    displayStandInserter(level, standPos, unit)));
        }

        return new DispatchPlan(List.copyOf(targets), null);
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)
                || ActuallyAdditionsEmpowererAccess.getProcessTime(be) > 0) {
            return List.of();
        }

        if (!displayStandsAreEmpty(level, mainPos)) {
            return List.of();
        }

        var handler = ActuallyAdditionsEmpowererAccess.getHandler(be);
        if (handler == null || handler.getSlots() < 1) {
            return List.of();
        }

        var stack = handler.getStackInSlot(0);
        if (stack.isEmpty() || ActuallyAdditionsEmpowererAccess.isPossibleInput(stack)) {
            return List.of();
        }

        var key = AEItemKey.of(stack);
        if (!allowsAutoReturn(level, mainPos, filter, key)) {
            return List.of();
        }

        var extracted = handler.extractItem(0, stack.getCount(), false);
        if (extracted.isEmpty()) {
            return List.of();
        }
        return List.of(new GenericStack(AEItemKey.of(extracted), extracted.getCount()));
    }

    /**
     * Bind-time recipe search: locks the recipe by pattern.outputs and the
     * structural shape (input ingredient + 4 modifier ingredients).
     */
    @Nullable
    private static Object findCandidateRecipe(ServerLevel level, IPatternDetails pattern) {
        for (var recipe : AaSupport.recipes(level, RECIPE_TYPE_ID)) {
            var result = resultItem(recipe, level);
            if (result == null || result.isEmpty() || !outputMatches(pattern, result)) {
                continue;
            }
            if (ActuallyAdditionsEmpowererAccess.getInput(recipe) == null) {
                continue;
            }
            var modifiers = ActuallyAdditionsEmpowererAccess.getModifiers(recipe);
            if (modifiers == null || modifiers.size() != 4) {
                continue;
            }
            return recipe;
        }
        return null;
    }

    /**
     * Per-push input assignment against the recipe locked in by {@link #bind}.
     */
    @Nullable
    private static RecipeMatch assignInputsToRecipe(Object recipe, List<PlannedUnit> units) {
        var input = ActuallyAdditionsEmpowererAccess.getInput(recipe);
        var modifiers = ActuallyAdditionsEmpowererAccess.getModifiers(recipe);
        if (input == null || modifiers == null || modifiers.size() != 4) {
            return null;
        }

        Predicate<PlannedUnit> basePredicate = unit -> input.test(unit.stack());
        var modifierPredicates = new ArrayList<Predicate<PlannedUnit>>(4);
        for (var modifier : modifiers) {
            modifierPredicates.add(unit -> modifier.test(unit.stack()));
        }

        var match = EmpowererInputMatcher.match(units, basePredicate, modifierPredicates);
        if (match == null) {
            return null;
        }
        if (!ActuallyAdditionsEmpowererAccess.recipeMatches(recipe, match.base(), match.modifiers())) {
            return null;
        }
        return new RecipeMatch(recipe, match.base(), match.modifiers());
    }

    @Nullable
    private static List<BlockPos> findEmptyDisplayStands(ServerLevel level, BlockPos mainPos) {
        var positions = new ArrayList<BlockPos>(DISPLAY_STAND_OFFSETS.length);
        for (var offset : DISPLAY_STAND_OFFSETS) {
            var pos = mainPos.offset(offset.getX(), offset.getY(), offset.getZ());
            if (!level.isLoaded(pos)) {
                return null;
            }
            var be = level.getBlockEntity(pos);
            if (be == null || !AdapterBlocks.idOf(be.getBlockState()).equals(DISPLAY_STAND_BLOCK)
                    || !ActuallyAdditionsEmpowererAccess.isDisplayStand(be)) {
                return null;
            }
            var handler = ActuallyAdditionsEmpowererAccess.getHandler(be);
            if (handler == null || handler.getSlots() < 1 || !handler.getStackInSlot(0).isEmpty()) {
                return null;
            }
            positions.add(pos);
        }
        return List.copyOf(positions);
    }

    private static boolean displayStandsAreEmpty(ServerLevel level, BlockPos mainPos) {
        for (var offset : DISPLAY_STAND_OFFSETS) {
            var pos = mainPos.offset(offset.getX(), offset.getY(), offset.getZ());
            if (!level.isLoaded(pos)) {
                return false;
            }
            var be = level.getBlockEntity(pos);
            if (be == null || !AdapterBlocks.idOf(be.getBlockState()).equals(DISPLAY_STAND_BLOCK)
                    || !ActuallyAdditionsEmpowererAccess.isDisplayStand(be)) {
                return false;
            }
            var handler = ActuallyAdditionsEmpowererAccess.getHandler(be);
            if (handler == null || handler.getSlots() < 1 || !handler.getStackInSlot(0).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean standsHaveTickEnergy(ServerLevel level, List<BlockPos> standPositions,
                                                Object recipe) {
        int time = ActuallyAdditionsEmpowererAccess.getTime(recipe);
        if (time <= 0) {
            return false;
        }
        int energyPerTick = ActuallyAdditionsEmpowererAccess.getEnergyPerStand(recipe) / time;
        if (energyPerTick <= 0) {
            return true;
        }

        for (var pos : standPositions) {
            var be = level.getBlockEntity(pos);
            if (be == null || ActuallyAdditionsEmpowererAccess.getEnergyStored(be) < energyPerTick) {
                return false;
            }
        }
        return true;
    }

    private static BiFunction<GenericStack, Actionable, Long> empowererInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (!matchesPlannedStack(stack, unit)) {
                return 0L;
            }
            var be = level.getBlockEntity(pos);
            if (be == null || !AdapterBlocks.idOf(be.getBlockState()).equals(EMPOWERER_BLOCK)
                    || !ActuallyAdditionsEmpowererAccess.isEmpowerer(be)
                    || ActuallyAdditionsEmpowererAccess.getProcessTime(be) > 0) {
                return 0L;
            }
            var handler = ActuallyAdditionsEmpowererAccess.getHandler(be);
            if (!(handler instanceof IItemHandlerModifiable modifiable)
                    || modifiable.getSlots() < 1
                    || !modifiable.getStackInSlot(0).isEmpty()) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                modifiable.setStackInSlot(0, unit.stack());
            }
            return 1L;
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> displayStandInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (!matchesPlannedStack(stack, unit)) {
                return 0L;
            }
            var be = level.getBlockEntity(pos);
            if (be == null || !AdapterBlocks.idOf(be.getBlockState()).equals(DISPLAY_STAND_BLOCK)
                    || !ActuallyAdditionsEmpowererAccess.isDisplayStand(be)) {
                return 0L;
            }
            var handler = ActuallyAdditionsEmpowererAccess.getHandler(be);
            if (!(handler instanceof IItemHandlerModifiable modifiable)
                    || modifiable.getSlots() < 1
                    || !modifiable.getStackInSlot(0).isEmpty()) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                modifiable.setStackInSlot(0, unit.stack());
            }
            return 1L;
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

    private static boolean outputMatches(IPatternDetails pattern, ItemStack result) {
        var outputs = pattern.getOutputs();
        if (outputs.length != 1) {
            return false;
        }
        var expected = outputs[0];
        if (!(expected.what() instanceof AEItemKey expectedKey) || expected.amount() != result.getCount()) {
            return false;
        }
        var actual = AEItemKey.of(result);
        return OverloadPatternSemantics.isIdOnlyOutput(pattern, 0)
                ? expectedKey.dropSecondary().equals(actual.dropSecondary())
                : expectedKey.equals(actual);
    }

    @Nullable
    private static ItemStack resultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> r)) {
            return null;
        }
        try {
            return r.getResultItem(level.registryAccess());
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean matchesPlannedStack(GenericStack stack, PlannedUnit unit) {
        return stack.amount() == 1 && unit.key().equals(stack.what());
    }

    record PlannedUnit(AEItemKey key) {
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

    private record RecipeMatch(Object recipe, PlannedUnit base, List<PlannedUnit> modifiers) {
    }

    /** Opaque binding handle returned from {@link #bind}. */
    private record EmpowererBindHandle(Object recipe) {
    }

}
