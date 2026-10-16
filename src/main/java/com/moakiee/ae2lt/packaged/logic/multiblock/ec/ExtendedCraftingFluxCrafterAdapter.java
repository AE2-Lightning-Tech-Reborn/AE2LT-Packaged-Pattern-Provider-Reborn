package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
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
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.SlotHarvestOwnership;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingTableGridPlanner.Assignment;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

/**
 * Real dispatch into the Flux Crafter's 3x3 grid; harvest from slot 9.
 * The machine draws FE from flux alternators while ticking, so dispatch does
 * not require them to be powered already. ExtendedCrafting uses reflection.
 */
public final class ExtendedCraftingFluxCrafterAdapter implements MultiblockAdapter {

    private static final String MOD_ID = "extendedcrafting";
    private static final ResourceLocation CRAFTER_BLOCK = ecId("flux_crafter");
    private static final ResourceLocation RECIPE_TYPE_ID = ecId("flux_crafter");
    private static final int GRID_SIZE = 9;
    private static final int OUTPUT_SLOT = 9;
    private static final String HARVEST_STATE = "ec_flux_crafter:harvest";

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
        return AdapterIds.EC_FLUX;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) return null;
        if (!hasSingleItemOutput(pattern)) return null;

        var recipe = findCandidateRecipe(level, pattern);
        if (recipe == null) return null;
        return new BindingResult(new FluxBindHandle(recipe), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        return canDispatch(level, mainPos, handle, AdapterPersistentScope.NOOP);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle,
                               AdapterPersistentScope scope) {
        if (!(handle instanceof FluxBindHandle)) return false;
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) return false;

        var inv = EcCrafterSupport.getInventory(be);
        if (inv == null || inv.getSlots() < GRID_SIZE + 1) return false;

        for (int i = 0; i < GRID_SIZE; i++) {
            if (!inv.getStackInSlot(i).isEmpty()) return false;
        }
        if (!inv.getStackInSlot(OUTPUT_SLOT).isEmpty()) return false;
        // Neither alternator presence nor FE balance is gated here: both can
        // be set up after dispatch and the BE re-checks them every tick.
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
        if (!(handle instanceof FluxBindHandle bind) || !canDispatch(level, mainPos, handle, scope)) return null;
        var be = level.getBlockEntity(mainPos);
        if (be == null) return null;
        var inv = EcCrafterSupport.getInventory(be);
        if (inv == null || inv.getSlots() < GRID_SIZE + 1) return null;
        var expectedOutput = EcCrafterSupport.getResultItem(bind.recipe(), level);
        if (expectedOutput == null || expectedOutput.isEmpty()) return null;
        var layout = EcCrafterSupport.assignGrid(bind.recipe(), inputs);
        if (layout == null) return null;

        var targets = new ArrayList<TargetSlot>(layout.size());
        for (var placement : layout) {
            targets.add(new TargetSlot(
                    level, mainPos, null,
                    List.of(new GenericStack(placement.value(), 1)),
                    InsertionStrategy.CUSTOM,
                    gridInserter(level, mainPos, placement.slot(), placement.value())));
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
        var inv = EcCrafterSupport.getInventory(be);
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
            var result = EcCrafterSupport.getResultItem(recipe, level);
            if (result == null || result.isEmpty()) continue;
            if (!outputMatches(pattern, result)) continue;
            return recipe;
        }
        return null;
    }

    private static BiFunction<GenericStack, Actionable, Long> gridInserter(
            ServerLevel level, BlockPos pos, int slot, AEItemKey key) {
        return (stack, mode) -> {
            if (stack.amount() != 1 || !key.equals(stack.what())) return 0L;
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(CRAFTER_BLOCK)) return 0L;
            var inv = EcCrafterSupport.getInventory(be);
            if (!(inv instanceof IItemHandlerModifiable modifiable)) return 0L;
            if (modifiable.getSlots() <= slot) return 0L;
            if (!modifiable.getStackInSlot(slot).isEmpty()) return 0L;
            if (mode == Actionable.MODULATE) {
                modifiable.setStackInSlot(slot, key.toStack(1));
                be.setChanged();
            }
            return 1L;
        };
    }

    private static void recoverPartialDispatch(ServerLevel level, BlockPos mainPos,
                                               List<TargetSlot> targets, List<Assignment<AEItemKey>> layout,
                                               List<AcceptedInsertion> accepted,
                                               Consumer<GenericStack> recovered) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !blockId(be.getBlockState()).equals(CRAFTER_BLOCK)) return;
        var inv = EcCrafterSupport.getInventory(be);
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

    private record FluxBindHandle(Object recipe) {}

}
