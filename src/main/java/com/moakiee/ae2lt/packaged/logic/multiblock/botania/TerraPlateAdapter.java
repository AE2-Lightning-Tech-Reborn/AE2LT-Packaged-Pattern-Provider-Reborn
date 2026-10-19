package com.moakiee.ae2lt.packaged.logic.multiblock.botania;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.AcceptedInsertion;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.BoundedConstraintMatcher;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.DroppedItemHarvestOwnership;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

/**
 * Dispatches one real terra-plate cycle, letting Botania accumulate mana.
 * Readiness also checks pending item entities during the plate's startup latch tick.
 * Inputs spawn inside the plate's [Y, Y+1) scan volume and carry an ownership tag.
 * Harvest includes the plate's own Y level because outputs spawn at Y + 0.2.
 */
public final class TerraPlateAdapter implements MultiblockAdapter {

    private static final String INPUT_MARKER = "ae2ltpp.botania.terra_plate_input";
    private static final String HARVEST_STATE = "botania_terra_plate:harvest";
    private static final double EXTRACT_RADIUS = 2.5d;
    // Plate is 0.1875 blocks tall; Botania spawns its product at
    // pos.Y + 0.2 (just above the visible top). We mirror that so the
    // dropped inputs sit on the plate exactly where the player would
    // have tossed them, which is also inside the plate's 1x1x1 input
    // scan AABB.
    private static final double SPAWN_Y_OFFSET = 0.2d;

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
        return BotaniaReflection.isTerraPlate(be);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.BOTANIA_TERRA_PLATE;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (!recognizesMain(level, mainPos, be)) {
            return null;
        }
        var holder = BotaniaRecipeLookup.findTerraPlateByOutput(level, pattern);
        if (holder == null) {
            return null;
        }
        // Enforce K=1 at bind time: pattern.amount must equal the recipe's
        // own result count (one terra plate cycle = one craft).
        var result = holder.getResultItem();
        if (result == null || result.isEmpty()) {
            return null;
        }
        if (BotaniaRecipeLookup.patternPrimaryOutputAmount(pattern) != result.getCount()) {
            return null;
        }
        return new BindingResult(new TerraBindHandle(holder), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        return canDispatch(level, mainPos, handle, AdapterPersistentScope.NOOP);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle,
                               AdapterPersistentScope scope) {
        if (!(handle instanceof TerraBindHandle)) {
            return false;
        }
        var be = level.getBlockEntity(mainPos);
        if (!BotaniaReflection.isTerraPlate(be) || !BotaniaReflection.terraIsIdle(be)) {
            return false;
        }
        var stateKey = DroppedItemHarvestOwnership.qualifiedKey(level, HARVEST_STATE);
        var encoded = scope.getState(mainPos, stateKey);
        if (encoded == null && scope.getState(mainPos, HARVEST_STATE) != null) {
            // Legacy, dimension-unqualified ownership cannot prove attribution.
            return false;
        }
        if (encoded != null) {
            var ownership = DroppedItemHarvestOwnership.load(level, mainPos, scope, HARVEST_STATE);
            if (ownership == null) {
                return false;
            }
            if (!DroppedItemHarvestOwnership.expired(level, ownership)
                    || DroppedItemHarvestOwnership.hasMatchingExpectedOutput(
                    level, outputBounds(mainPos), ownership)
                    || plateHasItemEntities(level, mainPos)) {
                return false;
            }
            // A long-expired craft with an empty idle plate has no output
            // left to attribute. Clear only the provider record, never items.
            DroppedItemHarvestOwnership.clear(level, mainPos, scope, HARVEST_STATE);
        }
        return !plateHasItemEntities(level, mainPos);
    }

    private static boolean plateHasItemEntities(ServerLevel level, BlockPos mainPos) {
        var plateAabb = new AABB(mainPos);
        var items = level.getEntitiesOfClass(ItemEntity.class, plateAabb);
        for (var entity : items) {
            if (entity.isAlive() && !entity.getItem().isEmpty()) {
                return true;
            }
        }
        return false;
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
        if (!(handle instanceof TerraBindHandle bind) || !canDispatch(level, mainPos, handle, scope)) {
            return null;
        }
        var recipe = bind.holder();
        var result = recipe.getResultItem();
        if (result == null || result.isEmpty()) {
            return null;
        }
        var assignment = pickKeyCounterStacks(recipe.getIngredients(), inputs);
        if (assignment == null || assignment.isEmpty()) {
            return null;
        }

        var spawnPos = new Vec3(
                mainPos.getX() + 0.5,
                mainPos.getY() + SPAWN_Y_OFFSET,
                mainPos.getZ() + 0.5);
        var bounds = outputBounds(mainPos);
        var baseline = DroppedItemHarvestOwnership.capture(level, bounds, result);
        var spawned = new ArrayList<SpawnedInput>();
        var targets = new ArrayList<TargetSlot>(assignment.size());
        for (var stack : assignment) {
            targets.add(new TargetSlot(
                    level, mainPos, null,
                    List.of(new GenericStack(AEItemKey.of(stack), stack.getCount())),
                    InsertionStrategy.CUSTOM,
                    terraInserter(level, spawnPos, stack, spawned)));
        }
        return new DispatchPlan(
                List.copyOf(targets),
                () -> DroppedItemHarvestOwnership.store(
                        level, mainPos, scope, HARVEST_STATE, baseline, result, recipe.getId()),
                (accepted, recovered) -> recoverPartialDispatch(
                        level, mainPos, targets, spawned, accepted, recovered));
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
        if (!BotaniaReflection.isTerraPlate(be) || !BotaniaReflection.terraIsIdle(be)) {
            return List.of();
        }
        var ownership = DroppedItemHarvestOwnership.load(level, mainPos, scope, HARVEST_STATE);
        if (ownership == null
                || DroppedItemHarvestOwnership.expired(level, ownership)
                || !filter.matches(AEItemKey.of(ownership.expectedOutput()))) {
            return List.of();
        }
        var outputs = DroppedItemHarvestOwnership.collectExpectedOutput(
                level, outputBounds(mainPos), ownership);
        if (!outputs.isEmpty()) {
            DroppedItemHarvestOwnership.clear(level, mainPos, scope, HARVEST_STATE);
        }
        return outputs;
    }

    private java.util.function.BiFunction<GenericStack, Actionable, Long> terraInserter(
            ServerLevel level, Vec3 spawnPos, ItemStack stack, List<SpawnedInput> spawned) {
        return terraInserter(() -> {
            var entity = new ItemEntity(level, spawnPos.x, spawnPos.y, spawnPos.z, stack.copy());
            entity.setDeltaMovement(0, 0, 0);
            entity.setPickUpDelay(20);
            entity.getPersistentData().putBoolean(INPUT_MARKER, true);
            if (!level.addFreshEntity(entity)) {
                return false;
            }
            spawned.add(new SpawnedInput(entity.getUUID(), stack.copy()));
            return true;
        });
    }

    static java.util.function.BiFunction<GenericStack, Actionable, Long> terraInserter(
            java.util.function.BooleanSupplier spawnInput) {
        return (target, actionable) -> {
            if (actionable.isSimulate()) {
                return target.amount();
            }
            return spawnInput.getAsBoolean() ? target.amount() : 0L;
        };
    }

    private static AABB outputBounds(BlockPos mainPos) {
        return new AABB(
                mainPos.getX() + 0.5 - EXTRACT_RADIUS, mainPos.getY(),
                mainPos.getZ() + 0.5 - EXTRACT_RADIUS,
                mainPos.getX() + 0.5 + EXTRACT_RADIUS, mainPos.getY() + 3.0,
                mainPos.getZ() + 0.5 + EXTRACT_RADIUS);
    }

    private static void recoverPartialDispatch(ServerLevel level, BlockPos mainPos,
                                               List<TargetSlot> targets,
                                               List<SpawnedInput> spawned,
                                               List<AcceptedInsertion> accepted,
                                               Consumer<GenericStack> recovered) {
        var be = level.getBlockEntity(mainPos);
        if (!BotaniaReflection.isTerraPlate(be) || !BotaniaReflection.terraIsIdle(be)) {
            return;
        }
        for (var insertion : accepted) {
            int targetIndex = targets.indexOf(insertion.target());
            if (targetIndex < 0 || targetIndex >= spawned.size()) {
                continue;
            }
            var created = spawned.get(targetIndex);
            var entity = level.getEntity(created.id());
            if (!(entity instanceof ItemEntity item) || !item.isAlive()
                    || !ItemStack.isSameItemSameTags(item.getItem(), created.stack())
                    || item.getItem().getCount() != created.stack().getCount()
                    || insertion.stack().amount() != created.stack().getCount()) {
                continue;
            }
            item.discard();
            recovered.accept(insertion.stack());
        }
    }

    /**
     * Assigns every runtime unit to one non-empty recipe ingredient. The
     * matching is constraint-first and bounded, so overlapping ingredients do
     * not fail merely because a broad ingredient was considered first.
     */
    @Nullable
    private static List<ItemStack> pickKeyCounterStacks(List<Ingredient> ingredients, KeyCounter[] inputs) {
        var constraints = new ArrayList<Predicate<AEItemKey>>();
        for (var ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                constraints.add(key -> ingredient.test(key.toStack(1)));
            }
        }
        if (constraints.isEmpty()) {
            return null;
        }
        var units = new ArrayList<AEItemKey>(constraints.size());
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey key)) {
                    return null;
                }
                long amount = entry.getLongValue();
                if (amount < 0 || amount > constraints.size() - units.size()) {
                    return null;
                }
                for (long i = 0; i < amount; i++) {
                    units.add(key);
                }
            }
        }
        var assigned = BoundedConstraintMatcher.matchExactly(units, constraints);
        if (assigned == null) {
            return null;
        }
        var stacks = new ArrayList<ItemStack>(assigned.size());
        for (var key : assigned) {
            stacks.add(key.toStack(1));
        }
        return List.copyOf(stacks);
    }

    private record SpawnedInput(UUID id, ItemStack stack) {
        private SpawnedInput {
            stack = stack.copy();
        }
    }

    private record TerraBindHandle(Recipe<?> holder) {
    }
}
