package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import com.moakiee.ae2lt.packaged.logic.multiblock.AcceptedInsertion;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.BlockCapabilities;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterBlocks;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.ExpectedStackHarvestOwnership;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekmmMachineSpec.PortDef;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekmmMachineSpec.PortGroup;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekmmMachineSpec.PortType;

/**
 * Single adapter that drives every Mekanism: More Machines multiblock
 * controller (large variants of chemical infuser, electrolytic separator,
 * antiprotonic nucleosynthesizer, rotary condensentrator, solar neutron
 * activator, pigment mixer). All per-machine differences live in
 * {@link MekmmMachines} as declarative {@link MekmmMachineSpec} entries.
 *
 * <p>All machines share the same packaged core ({@link AdapterIds#MEKMM}) so the
 * user only needs one item in the provider regardless of which large machine
 * sits in front of it.
 */
public final class MekanismMoreMachinesAdapter implements MultiblockAdapter {

    private static final String HARVEST_STATE = "mekmm:harvest";
    private static final long STATE_TTL_TICKS = 20L * 60L * 60L * 6L;

    @Override
    public int priority() {
        return 100;
    }

    @Override
    @Nullable
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.MEKMM;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        if (be == null || !MekReflection.isModLoaded()) return false;
        return findSpec(level, be) != null;
    }

    /**
     * Resolve the {@link MekmmMachineSpec} that owns the given block entity
     * (already known to live at {@code pos} in {@code level}). Walks bounding
     * blocks back to the controller and matches by both registry id and tile
     * class so two different mekmm machines that happen to share a class can
     * coexist.
     */
    @Nullable
    private static MekmmMachineSpec findSpec(ServerLevel level, BlockEntity be) {
        BlockEntity mainBe = be;
        if (MekReflection.isBoundingBlock(be)) {
            BlockPos mainPos = MekReflection.getMainPos(be);
            if (mainPos == null || !level.isLoaded(mainPos)) return null;
            mainBe = level.getBlockEntity(mainPos);
            if (mainBe == null) return null;
        }
        ResourceLocation blockId = AdapterBlocks.idOf(mainBe.getBlockState().getBlock());
        for (MekmmMachineSpec spec : MekmmMachines.ALL) {
            if (spec.block().equals(blockId)
                    && MekReflection.isTileEntityClass(mainBe, spec.tileClass())) {
                return spec;
            }
        }
        return null;
    }

    private static BlockPos resolveMainPos(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be != null && MekReflection.isBoundingBlock(be)) {
            BlockPos resolved = MekReflection.getMainPos(be);
            if (resolved != null) return resolved;
        }
        return pos;
    }

    @Nullable
    private static MekmmMachineSpec specAt(ServerLevel level, BlockPos mainPos) {
        var be = level.getBlockEntity(mainPos);
        if (be == null) return null;
        return findSpec(level, be);
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        mainPos = resolveMainPos(level, mainPos);
        MekmmMachineSpec spec = specAt(level, mainPos);
        if (spec == null) return null;
        var binding = MekRecipeBinding.find(level, level.getBlockEntity(mainPos), spec, pattern);
        return binding == null ? null : new BindingResult(binding, BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        return canDispatch(level, mainPos, handle, AdapterPersistentScope.NOOP);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle,
                               AdapterPersistentScope scope) {
        if (!(handle instanceof MekRecipeBinding binding)) return false;
        var spec = binding.spec();
        mainPos = resolveMainPos(level, mainPos);
        var be = level.getBlockEntity(mainPos);
        if (be == null || !MekReflection.isTileEntityClass(be, spec.tileClass())) return false;
        if (spec.idleGated() && MekReflection.isActive(be)) return false;
        boolean outputsEmpty = outputPortsEmpty(level, mainPos, MekReflection.getFacing(be),
                spec.activeMode(be));
        maybeClearExpiredHarvest(level, mainPos, scope, outputsEmpty);
        if (harvestPending(level, mainPos, scope)) return false;

        // Output attribution precondition: this craft may only be harvested
        // later if the output ports provably held nothing before it started.
        return outputsEmpty;
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
        if (!(handle instanceof MekRecipeBinding binding)
                || !canDispatch(level, mainPos, handle, scope)) return null;
        var spec = binding.spec();
        mainPos = resolveMainPos(level, mainPos);
        var be = level.getBlockEntity(mainPos);
        if (be == null || !MekReflection.isTileEntityClass(be, spec.tileClass())) return null;
        if (spec.idleGated() && MekReflection.isActive(be)) return null;

        PortGroup mode = spec.activeMode(be);
        Direction facing = MekReflection.getFacing(be);

        // Native recipe predicates decide the port order, including two different gases/pigments.
        // Keep the Forge ownership and compensation callbacks around the resulting exact plan.
        var arranged = binding.arrange(level, mode, pattern, inputs);
        if (arranged == null) return null;
        var expected = copyPatternOutputs(pattern);
        if (expected.isEmpty()) return null;
        var targets = new ArrayList<TargetSlot>();
        for (int i = 0; i < mode.inputs().size(); i++) {
            var port = mode.inputs().get(i);
            BlockPos portPos = portPos(mainPos, facing, port.spec());
            Direction portSide = portSide(facing, port.spec());
            if (!level.isLoaded(portPos)) return null;
            targets.add(new TargetSlot(level, portPos, portSide, List.of(arranged.get(i)),
                    InsertionStrategy.CUSTOM, inserterFor(port.type(), level, portPos, portSide)));
        }

        var resolvedMainPos = mainPos;
        return new DispatchPlan(List.copyOf(targets), () ->
                ExpectedStackHarvestOwnership.store(
                        level, resolvedMainPos, scope, HARVEST_STATE, expected),
                (accepted, recovered) -> recoverPartialDispatch(
                        level, resolvedMainPos, targets, accepted, recovered));
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
        mainPos = resolveMainPos(level, mainPos);
        var be = level.getBlockEntity(mainPos);
        if (be == null) return List.of();
        MekmmMachineSpec spec = findSpec(level, be);
        if (spec == null) return List.of();
        if (spec.idleGated() && MekReflection.isActive(be)) return List.of();
        var claim = ExpectedStackHarvestOwnership.load(level, mainPos, scope, HARVEST_STATE);
        if (claim == null || ExpectedStackHarvestOwnership.expired(level, claim) || claim.isEmpty()) {
            return List.of();
        }

        Direction facing = MekReflection.getFacing(be);
        PortGroup mode = spec.activeMode(be);
        var remaining = new ArrayList<>(claim.remaining());
        var results = new ArrayList<GenericStack>();
        for (PortDef port : mode.outputs()) {
            BlockPos portPos = portPos(mainPos, facing, port.spec());
            Direction portSide = portSide(facing, port.spec());
            if (!level.isLoaded(portPos)) continue;
            switch (port.type()) {
                case ITEM -> extractItems(level, portPos, portSide, filter, remaining, results);
                case FLUID -> extractFluids(level, portPos, portSide, filter, remaining, results);
                case CHEMICAL -> extractChemicals(level, portPos, portSide, filter, remaining, results);
            }
        }
        var leftover = ExpectedStackHarvestOwnership.compact(remaining);
        if (leftover.isEmpty()) {
            ExpectedStackHarvestOwnership.clear(level, mainPos, scope, HARVEST_STATE);
        } else {
            ExpectedStackHarvestOwnership.store(
                    level, mainPos, scope, HARVEST_STATE, claim.createdTick(), leftover);
        }
        return results;
    }

    // ---------- key / port helpers ----------

    @Nullable
    private static PortType classify(AEKey key) {
        if (key instanceof AEItemKey) return PortType.ITEM;
        if (key instanceof AEFluidKey) return PortType.FLUID;
        if (AppmekReflection.isMekanismKey(key)) return PortType.CHEMICAL;
        return null;
    }



    private static BlockPos portPos(BlockPos main, Direction facing, MekPortSpec spec) {
        Direction back = facing.getOpposite();
        Direction left = facing.getClockWise();
        return main.offset(
                back.getStepX() * spec.backSteps() + left.getStepX() * spec.leftSteps(),
                spec.y(),
                back.getStepZ() * spec.backSteps() + left.getStepZ() * spec.leftSteps());
    }

    private static Direction portSide(Direction facing, MekPortSpec spec) {
        return switch (spec.access()) {
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getClockWise();
            case RIGHT -> facing.getCounterClockWise();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }

    // ---------- insertion (per-type custom inserters) ----------

    private static BiFunction<GenericStack, Actionable, Long> inserterFor(
            PortType type, ServerLevel level, BlockPos pos, Direction side) {
        return switch (type) {
            case ITEM -> itemInserter(level, pos, side);
            case FLUID -> fluidInserter(level, pos, side);
            case CHEMICAL -> chemicalInserter(level, pos, side);
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> itemInserter(
            ServerLevel level, BlockPos pos, Direction side) {
        return (stack, mode) -> {
            if (!(stack.what() instanceof AEItemKey itemKey)
                    || stack.amount() <= 0 || stack.amount() > Integer.MAX_VALUE) return 0L;
            IItemHandler handler = BlockCapabilities.find(level, pos, ForgeCapabilities.ITEM_HANDLER, side);
            if (handler == null) return 0L;
            ItemStack toInsert = itemKey.toStack((int) stack.amount());
            return MekItemInsertion.insertIntoAnySlot(toInsert, mode == Actionable.SIMULATE,
                    new MekItemInsertion.SlotAccess<>() {
                        @Override public int slots() { return handler.getSlots(); }
                        @Override public ItemStack insert(int slot, ItemStack s, boolean sim) {
                            return handler.insertItem(slot, s, sim);
                        }
                        @Override public int amount(ItemStack s) { return s.getCount(); }
                    });
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> fluidInserter(
            ServerLevel level, BlockPos pos, Direction side) {
        return (stack, mode) -> {
            if (!(stack.what() instanceof AEFluidKey fluidKey)
                    || stack.amount() <= 0 || stack.amount() > Integer.MAX_VALUE) return 0L;
            IFluidHandler handler = BlockCapabilities.find(level, pos, ForgeCapabilities.FLUID_HANDLER, side);
            if (handler == null) return 0L;
            FluidStack toInsert = fluidKey.toStack((int) stack.amount());
            int filled = handler.fill(toInsert,
                    mode == Actionable.SIMULATE
                            ? IFluidHandler.FluidAction.SIMULATE
                            : IFluidHandler.FluidAction.EXECUTE);
            return (long) filled;
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> chemicalInserter(
            ServerLevel level, BlockPos pos, Direction side) {
        return (stack, mode) -> {
            if (!AppmekReflection.isMekanismKey(stack.what()) || stack.amount() <= 0) return 0L;
            var handlers = MekReflection.getChemicalHandlers(level, pos, side);
            if (handlers == null || handlers.isEmpty()) return 0L;
            // Identity-aware handler selection: only handlers that actually
            // accept this chemical receive any of it, in resolve order.
            long remaining = stack.amount();
            for (Object handler : handlers) {
                if (remaining <= 0) break;
                Object chemStack = AppmekReflection.toChemicalStackWithAmount(stack.what(), remaining);
                if (chemStack == null) return 0L;
                long inserted = MekReflection.insertChemical(handler, chemStack,
                        mode == Actionable.SIMULATE);
                if (inserted > 0) {
                    remaining -= Math.min(inserted, remaining);
                }
            }
            return stack.amount() - Math.max(0, remaining);
        };
    }

    // ---------- extraction (per-type) ----------

    private static void extractItems(ServerLevel level, BlockPos pos, Direction side,
                                     AllowedOutputFilter filter, List<GenericStack> remaining,
                                     List<GenericStack> out) {
        IItemHandler handler = BlockCapabilities.find(level, pos, ForgeCapabilities.ITEM_HANDLER, side);
        if (handler == null) return;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            AEItemKey key = AEItemKey.of(stack);
            if (!filter.matches(key)) continue;
            int want = ExpectedStackHarvestOwnership.claimableCount(remaining, key, stack.getCount());
            if (want <= 0) continue;
            var simulated = handler.extractItem(i, want, true);
            if (simulated.isEmpty() || simulated.getCount() <= 0
                    || !ItemStack.isSameItemSameTags(simulated, stack.copyWithCount(simulated.getCount()))) {
                continue;
            }
            var extracted = handler.extractItem(i, simulated.getCount(), false);
            if (extracted.isEmpty() || !ItemStack.isSameItemSameTags(extracted, simulated)) continue;
            var extractedKey = AEItemKey.of(extracted);
            ExpectedStackHarvestOwnership.debit(remaining, extractedKey, extracted.getCount());
            out.add(new GenericStack(extractedKey, extracted.getCount()));
        }
    }

    private static void extractFluids(ServerLevel level, BlockPos pos, Direction side,
                                      AllowedOutputFilter filter, List<GenericStack> remaining,
                                      List<GenericStack> out) {
        IFluidHandler handler = BlockCapabilities.find(level, pos, ForgeCapabilities.FLUID_HANDLER, side);
        if (handler == null) return;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack fluid = handler.getFluidInTank(i);
            if (fluid.isEmpty()) continue;
            AEFluidKey key = AEFluidKey.of(fluid);
            if (!filter.matches(key)) continue;
            int want = ExpectedStackHarvestOwnership.claimableCount(remaining, key, fluid.getAmount());
            if (want <= 0) continue;
            var request = key.toStack(want);
            var simulated = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
            if (simulated.isEmpty() || simulated.getAmount() <= 0
                    || !simulated.isFluidEqual(request)
                    || !FluidStack.areFluidStackTagsEqual(simulated, request)) {
                continue;
            }
            var drained = handler.drain(key.toStack(simulated.getAmount()), IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty() || !drained.isFluidEqual(request)
                    || !FluidStack.areFluidStackTagsEqual(drained, request)) continue;
            var drainedKey = AEFluidKey.of(drained);
            ExpectedStackHarvestOwnership.debit(remaining, drainedKey, drained.getAmount());
            out.add(new GenericStack(drainedKey, drained.getAmount()));
        }
    }

    private static void extractChemicals(ServerLevel level, BlockPos pos, Direction side,
                                         AllowedOutputFilter filter, List<GenericStack> remaining,
                                         List<GenericStack> out) {
        var handlers = MekReflection.getChemicalHandlers(level, pos, side);
        if (handlers == null) return;
        for (Object handler : handlers) {
            int tanks = MekReflection.getChemicalTanks(handler);
            for (int i = 0; i < tanks; i++) {
                Object stack = MekReflection.getChemicalInTank(handler, i);
                if (MekReflection.isChemicalEmpty(stack)) continue;
                AEKey key = AppmekReflection.fromChemicalStack(stack);
                if (key == null || !filter.matches(key)) continue;
                long stored = MekReflection.getChemicalAmount(stack);
                long want = ExpectedStackHarvestOwnership.claimableAmount(remaining, key, stored);
                if (want <= 0 || !MekReflection.supportsTankIndexedChemicalExtraction()) continue;
                Object simulated = MekReflection.extractChemicalFromTank(handler, i, want, true);
                if (simulated == null || MekReflection.isChemicalEmpty(simulated)) continue;
                AEKey simulatedKey = AppmekReflection.fromChemicalStack(simulated);
                long simulatedAmount = MekReflection.getChemicalAmount(simulated);
                if (simulatedKey == null || !simulatedKey.equals(key) || simulatedAmount <= 0) continue;
                Object extracted = MekReflection.extractChemicalFromTank(handler, i, simulatedAmount, false);
                if (extracted == null || MekReflection.isChemicalEmpty(extracted)) continue;
                AEKey extractedKey = AppmekReflection.fromChemicalStack(extracted);
                long extractedAmount = MekReflection.getChemicalAmount(extracted);
                if (extractedKey == null || !extractedKey.equals(key) || extractedAmount <= 0) {
                    continue;
                }
                ExpectedStackHarvestOwnership.debit(remaining, extractedKey, extractedAmount);
                out.add(new GenericStack(extractedKey, extractedAmount));
            }
        }
    }

    // ---------- provider-owned output attribution ----------

    private static List<GenericStack> copyPatternOutputs(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        if (outputs == null || outputs.length == 0) return List.of();
        var copied = new ArrayList<GenericStack>(outputs.length);
        for (var stack : outputs) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                return List.of();
            }
            copied.add(new GenericStack(stack.what(), stack.amount()));
        }
        return List.copyOf(copied);
    }

    /**
     * True while a committed dispatch still owns remaining expected outputs. A
     * malformed stored value blocks dispatch instead of being silently
     * overwritten; an expired value no longer blocks (the output-emptiness
     * gate still prevents misattribution of whatever residue remains).
     */
    private static boolean harvestPending(ServerLevel level, BlockPos mainPos,
                                          AdapterPersistentScope scope) {
        var claim = ExpectedStackHarvestOwnership.load(level, mainPos, scope, HARVEST_STATE);
        if (claim != null) {
            return !ExpectedStackHarvestOwnership.expired(level, claim) && !claim.isEmpty();
        }
        var encoded = scope.getState(mainPos, ExpectedStackHarvestOwnership.qualifiedKey(level, HARVEST_STATE));
        if (encoded == null || encoded.isBlank()) return false;
        Long createdTick = parseHarvestTick(encoded);
        if (createdTick == null) return true;
        long age = level.getGameTime() - createdTick;
        return age >= 0 && age <= STATE_TTL_TICKS;
    }

    private static void maybeClearExpiredHarvest(ServerLevel level, BlockPos mainPos,
                                                 AdapterPersistentScope scope, boolean outputsEmpty) {
        if (!outputsEmpty) return;
        var claim = ExpectedStackHarvestOwnership.load(level, mainPos, scope, HARVEST_STATE);
        if (claim != null) {
            if (ExpectedStackHarvestOwnership.expired(level, claim) || claim.isEmpty()) {
                ExpectedStackHarvestOwnership.clear(level, mainPos, scope, HARVEST_STATE);
            }
            return;
        }
        var encoded = scope.getState(mainPos, ExpectedStackHarvestOwnership.qualifiedKey(level, HARVEST_STATE));
        if (encoded == null || encoded.isBlank()) return;
        Long createdTick = parseHarvestTick(encoded);
        if (createdTick == null) return;
        long age = level.getGameTime() - createdTick;
        if (age < 0 || age > STATE_TTL_TICKS) {
            ExpectedStackHarvestOwnership.clear(level, mainPos, scope, HARVEST_STATE);
        }
    }

    @Nullable
    private static Long parseHarvestTick(String encoded) {
        try {
            long value = Long.parseLong(encoded.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    /**
     * Every output port must provably hold nothing before a dispatch: that is
     * what makes later output content attributable to this dispatch. A port
     * whose handler cannot even be resolved also fails the check.
     */
    private static boolean outputPortsEmpty(ServerLevel level, BlockPos mainPos,
                                            Direction facing, PortGroup mode) {
        for (PortDef port : mode.outputs()) {
            BlockPos portPos = portPos(mainPos, facing, port.spec());
            Direction portSide = portSide(facing, port.spec());
            if (!level.isLoaded(portPos)) return false;
            switch (port.type()) {
                case ITEM -> {
                    IItemHandler handler = BlockCapabilities.find(level, portPos,
                            ForgeCapabilities.ITEM_HANDLER, portSide);
                    if (handler == null) return false;
                    for (int i = 0; i < handler.getSlots(); i++) {
                        if (!handler.getStackInSlot(i).isEmpty()) return false;
                    }
                }
                case FLUID -> {
                    IFluidHandler handler = BlockCapabilities.find(level, portPos,
                            ForgeCapabilities.FLUID_HANDLER, portSide);
                    if (handler == null) return false;
                    for (int i = 0; i < handler.getTanks(); i++) {
                        if (!handler.getFluidInTank(i).isEmpty()) return false;
                    }
                }
                case CHEMICAL -> {
                    var handlers = MekReflection.getChemicalHandlers(level, portPos, portSide);
                    if (handlers == null || handlers.isEmpty()) return false;
                    for (Object handler : handlers) {
                        int tanks = MekReflection.getChemicalTanks(handler);
                        for (int i = 0; i < tanks; i++) {
                            if (!MekReflection.isChemicalEmpty(
                                    MekReflection.getChemicalInTank(handler, i))) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    // ---------- partial-dispatch compensation ----------

    private static void recoverPartialDispatch(ServerLevel level, BlockPos mainPos,
                                               List<TargetSlot> targets,
                                               List<AcceptedInsertion> accepted,
                                               Consumer<GenericStack> recovered) {
        var be = level.getBlockEntity(mainPos);
        MekmmMachineSpec spec = be == null ? null : findSpec(level, be);
        if (spec == null || be == null) return;
        // Only an idle-gated machine that is verifiably idle can prove it has
        // not started consuming the accepted inputs. Non-gated machines
        // (solar neutron activator) get no compensation.
        if (!spec.idleGated() || MekReflection.isActive(be)) return;
        for (var insertion : accepted) {
            long amount = insertion.stack().amount();
            if (amount <= 0) continue;
            PortType type = classify(insertion.stack().what());
            if (type == PortType.ITEM) {
                recoverItems(level, insertion.target(), insertion.stack(), recovered);
            } else if (type == PortType.FLUID) {
                recoverFluids(level, insertion.target(), insertion.stack(), recovered);
            } else if (type == PortType.CHEMICAL) {
                recoverChemicals(level, insertion.target(), insertion.stack(), recovered);
            }
        }
    }

    private static void recoverItems(ServerLevel level, TargetSlot target,
                                     GenericStack accepted, Consumer<GenericStack> recovered) {
        if (accepted.amount() > Integer.MAX_VALUE
                || !(accepted.what() instanceof AEItemKey itemKey)) return;
        IItemHandler handler = BlockCapabilities.find(level, target.pos(),
                ForgeCapabilities.ITEM_HANDLER, target.face());
        if (handler == null) return;
        var expected = itemKey.toStack((int) accepted.amount());
        for (int i = 0; i < handler.getSlots(); i++) {
            var current = handler.getStackInSlot(i);
            if (current.getCount() != expected.getCount()
                    || !ItemStack.isSameItemSameTags(current, expected)) continue;
            if (handler.extractItem(i, expected.getCount(), true).getCount()
                    != expected.getCount()) return;
            var extracted = handler.extractItem(i, expected.getCount(), false);
            if (extracted.getCount() == expected.getCount()
                    && ItemStack.isSameItemSameTags(extracted, expected)) {
                recovered.accept(accepted);
            }
            return;
        }
    }

    private static void recoverFluids(ServerLevel level, TargetSlot target,
                                      GenericStack accepted, Consumer<GenericStack> recovered) {
        if (accepted.amount() > Integer.MAX_VALUE
                || !(accepted.what() instanceof AEFluidKey fluidKey)) return;
        IFluidHandler handler = BlockCapabilities.find(level, target.pos(),
                ForgeCapabilities.FLUID_HANDLER, target.face());
        if (handler == null) return;
        var request = fluidKey.toStack((int) accepted.amount());
        for (int i = 0; i < handler.getTanks(); i++) {
            var tank = handler.getFluidInTank(i);
            if (tank.isEmpty() || !(tank.isFluidEqual(request) && FluidStack.areFluidStackTagsEqual(tank, request))) continue;
            var simulated = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
            if (simulated.getAmount() != request.getAmount()
                    || !(simulated.isFluidEqual(request) && FluidStack.areFluidStackTagsEqual(simulated, request))) continue;
            var drained = handler.drain(request, IFluidHandler.FluidAction.EXECUTE);
            if (drained.getAmount() == request.getAmount()
                    && drained.isFluidEqual(request) && FluidStack.areFluidStackTagsEqual(drained, request)) {
                recovered.accept(accepted);
            }
            return;
        }
    }

    private static void recoverChemicals(ServerLevel level, TargetSlot target,
                                         GenericStack accepted, Consumer<GenericStack> recovered) {
        if (!AppmekReflection.isMekanismKey(accepted.what())) return;
        var handlers = MekReflection.getChemicalHandlers(level, target.pos(), target.face());
        if (handlers == null) return;
        for (Object handler : handlers) {
            int tanks = MekReflection.getChemicalTanks(handler);
            for (int i = 0; i < tanks; i++) {
                var inTank = MekReflection.getChemicalInTank(handler, i);
                if (MekReflection.isChemicalEmpty(inTank)) continue;
                AEKey tankKey = AppmekReflection.fromChemicalStack(inTank);
                if (tankKey == null || !tankKey.equals(accepted.what())) continue;
                if (MekReflection.getChemicalAmount(inTank) < accepted.amount()) continue;
                if (!MekReflection.supportsTankIndexedChemicalExtraction()) return;
                if (!extractTankExact(handler, i, accepted)) return;
                recovered.accept(accepted);
                return;
            }
        }
    }

    private static boolean extractTankExact(Object handler, int tank, GenericStack accepted) {
        var simulated = MekReflection.extractChemicalFromTank(handler, tank,
                accepted.amount(), true);
        if (simulated == null || MekReflection.isChemicalEmpty(simulated)) return false;
        var simulatedKey = AppmekReflection.fromChemicalStack(simulated);
        if (simulatedKey == null || !simulatedKey.equals(accepted.what())
                || MekReflection.getChemicalAmount(simulated) != accepted.amount()) {
            return false;
        }
        var extracted = MekReflection.extractChemicalFromTank(handler, tank,
                accepted.amount(), false);
        if (extracted == null || MekReflection.isChemicalEmpty(extracted)) return false;
        var extractedKey = AppmekReflection.fromChemicalStack(extracted);
        return extractedKey != null && extractedKey.equals(accepted.what())
                && MekReflection.getChemicalAmount(extracted) == accepted.amount();
    }

}
