package com.moakiee.ae2lt.packaged.logic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem;
import com.moakiee.ae2lt.packaged.logic.multiblock.AdapterPersistentScope;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;
import com.moakiee.ae2lt.packaged.logic.multiblock.NeighborMainBlockIndex;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.UnboundedVirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ProviderInternalCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BatchCopies;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.AdaptiveReturnSchedule;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.RequestCooldownTable;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekanismMoreMachinesAdapter;
import com.moakiee.ae2lt.packaged.mixin.PatternProviderLogicAccessor;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneCandidate;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneCooldownTable;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneKey;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.LaneRateLimiter;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.PatternBinding;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.PatternBindingTable;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.VirtualBatchAccumulator;
import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.patternprovider.PatternProviderPowerCost;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.packaged.patternprovider.StablePatternProviderLogic;

/** Schedules cached bindings, real-machine dispatch, virtual batches and output return. */
public class PackagedPatternProviderLogic extends StablePatternProviderLogic implements IBatchCraftingProvider {

    private static final Logger LOG = LoggerFactory.getLogger(PackagedPatternProviderLogic.class);

    /** Maximum virtual-craft acquisitions per lane per game tick. */
    private static final int VIRTUAL_PUSH_CAP_PER_LANE_PER_TICK = 64;

    /** Auto-return polling interval for real-dispatch lanes. */
    private static final int AUTO_RETURN_INTERVAL_TICKS = 20;

    /** Independent cadence for adapter-owned activation retries. */
    private static final int PENDING_RETRY_INTERVAL_TICKS = 20;

    private final NeighborMainBlockIndex neighborIndex;
    private final PatternBindingTable bindingTable = new PatternBindingTable();
    private final LaneCooldownTable cooldownTable = new LaneCooldownTable();
    private final LaneRateLimiter virtualPushLimiter =
            new LaneRateLimiter(VIRTUAL_PUSH_CAP_PER_LANE_PER_TICK);
    private final VirtualBatchAccumulator virtualBatch = new VirtualBatchAccumulator();

    /** Round-robin cursor across real candidates within a single binding. */
    private int realPushRoundRobin;
    private record MekTarget(ResourceLocation dimension, net.minecraft.core.BlockPos pos) {}
    private record MekRequest(MekTarget target, IPatternDetails pattern, Object mode) {}
    private final RequestCooldownTable<MekRequest> mekFailures = new RequestCooldownTable<>();
    private final AdaptiveReturnSchedule<MekTarget> mekReturns = new AdaptiveReturnSchedule<>();
    private final Map<MekTarget, MultiblockAdapter> mekReturnTargets = new LinkedHashMap<>();

    /** Long.MIN_VALUE forces the first auto-return poll. */
    private long lastAutoReturnTick = Long.MIN_VALUE;

    /** Last server tick at which adapter pending work was polled. */
    private long lastPendingRetryTick = Long.MIN_VALUE;

    /** Last virtual-push sound, consumed and cleared when the batch is flushed. */
    @Nullable
    private ResourceLocation pendingFlushSoundId;

    @Nullable
    private PendingVirtualFlushEffect pendingFlushEffect;

    public PackagedPatternProviderLogic(IManagedGridNode mainNode,
                                        PackagedPatternProviderBlockEntity host) {
        super(mainNode, host);
        this.neighborIndex = new NeighborMainBlockIndex(host.getBlockPos());
    }

    // Pattern lifecycle

    @Override
    public void updatePatterns() {
        super.updatePatterns();
        bindingTable.invalidateAll();
        resetMekScheduling();
    }

    @Override
    public void writeToNBT(net.minecraft.nbt.CompoundTag tag) {
        super.writeToNBT(tag);
        virtualBatch.writeToNBT(tag);
    }

    @Override
    public void readFromNBT(net.minecraft.nbt.CompoundTag tag) {
        super.readFromNBT(tag);
        resetMekScheduling();
        virtualBatch.readFromNBT(tag);
        pendingFlushSoundId = null;
        pendingFlushEffect = null;
        lastAutoReturnTick = Long.MIN_VALUE;
        lastPendingRetryTick = Long.MIN_VALUE;
    }

    @Override
    public void clearContent() {
        super.clearContent();
        resetMekScheduling();
        // Pending virtual outputs belong to the provider and must survive an
        // inventory/content reset until they can be delivered or dropped.
        pendingFlushSoundId = null;
        pendingFlushEffect = null;
        if (virtualBatch.hasPending()) {
            alertGridTick();
        }
    }

    public List<GenericStack> drainVirtualBatch() {
        var drained = virtualBatch.drainAll();
        if (!drained.isEmpty()) {
            getProviderHost().saveChanges();
        }
        return drained;
    }

    private void enqueueVirtualOutputs(List<GenericStack> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return;
        }
        virtualBatch.enqueue(outputs);
        getProviderHost().saveChanges();
    }

    @Override
    public void onNeighborChanged() {
        clearRemovedAdjacentTargetState();
        neighborIndex.invalidate();
        bindingTable.invalidateAll();
        resetMekScheduling();
        cooldownTable.clear();
        super.onNeighborChanged();
    }

    private void clearRemovedAdjacentTargetState() {
        if (getProviderHost().getProviderMode() != ProviderMode.NORMAL
                || !(getProviderHost().getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (var face : Direction.values()) {
            var pos = getProviderHost().getBlockPos().relative(face);
            if (!level.isLoaded(pos)) {
                continue;
            }
            var be = level.getBlockEntity(pos);
            // Read the old observation without rebuilding the index. A binding
            // (or even an installed pattern) is not required to retire ownership.
            var previous = neighborIndex.getAdapter(face);
            boolean remains;
            if (previous != null) {
                remains = previous.recognizesMain(level, pos, be);
            } else {
                // A cold index has no previous adapter identity. Check all
                // registrations: disabling an adapter does not remove its block.
                remains = MultiblockAdapterRegistry.registrations().stream()
                        .anyMatch(entry -> entry.adapter().recognizesMain(level, pos, be));
            }
            if (!remains) {
                adapterScope().clearFlagsForTarget(pos);
            }
        }
    }

    @Override
    public void onHostStateChanged() {
        bindingTable.invalidateAll();
        resetMekScheduling();
        pruneStaleLaneState();
        super.onHostStateChanged();
    }

    /** Rebuilds core eligibility after the adapter slot changes. */
    public void onAdapterSlotChanged() {
        bindingTable.invalidateAll();
        resetMekScheduling();
        cooldownTable.clear();
    }

    /** Provider-owned adapter state, or NOOP for hosts without persistence. */
    private AdapterPersistentScope adapterScope() {
        if (getProviderHost() instanceof AdapterPersistentScope scope) {
            return scope;
        }
        return AdapterPersistentScope.NOOP;
    }

    // Push path

    private boolean batchControlsAllowDispatch() {
        return !isBlocking() && getConfigManager().getSetting(appeng.api.config.Settings.LOCK_CRAFTING_MODE)
                == LockCraftingMode.NONE;
    }

    @Override
    public long getBatchCapacity(IPatternDetails pattern) {
        if (!getGridNode().isActive() || isBusy() || getCraftingLockedReason() != LockCraftingMode.NONE
                || !getAvailablePatterns().contains(pattern)) return 0;
        if (!BatchCopies.eligible(pattern) || !batchControlsAllowDispatch()) return 1;
        if (!(getProviderHost().getLevel() instanceof ServerLevel level)) return 0;
        var binding = getOrComputeBinding(level, pattern, level.getGameTime());
        return binding.candidates().stream().anyMatch(candidate -> candidate.mode() == BindingMode.VIRTUAL
                && candidate.adapter() instanceof UnboundedVirtualCraftingAdapter) ? Long.MAX_VALUE : 1;
    }

    @Override
    public long pushBatch(IPatternDetails pattern, KeyCounter[] template, long maxCraft) {
        if (maxCraft <= 0) return maxCraft;
        if (!BatchCopies.eligible(pattern) || !batchControlsAllowDispatch()) {
            return pushPattern(pattern, BatchCopies.copy(template)) ? maxCraft - 1 : maxCraft;
        }
        if (!getGridNode().isActive() || isBusy() || getCraftingLockedReason() != LockCraftingMode.NONE
                || !getAvailablePatterns().contains(pattern)
                || !(getProviderHost().getLevel() instanceof ServerLevel level)) return maxCraft;
        var grid = getGridNode().getGrid();
        double cost = PatternProviderPowerCost.totalCost(template);
        long tick = level.getGameTime();
        var binding = getOrComputeBinding(level, pattern, tick);
        for (var candidate : binding.candidates()) {
            if (candidate.mode() != BindingMode.VIRTUAL
                    || !(candidate.adapter() instanceof UnboundedVirtualCraftingAdapter adapter)) continue;
            var env = resolveLaneEnv(level, candidate);
            if (env == null) continue;
            var result = adapter.planVirtualWithBinding(env.level(), env.pos(), pattern,
                    BatchCopies.copy(template), candidate.handle(), getActionSource());
            if (result == null || result.outputs().isEmpty()) continue;
            long maximum = Math.min(maxCraft, virtualBatch.maxCopies(result.outputs()));
            long copies = BatchCopies.fittingLong(maximum,
                    n -> Double.isFinite(cost * n) && PatternProviderPowerCost.canAfford(grid, cost * n));
            if (copies <= 0) continue;
            // Only pure workbench adapters opt in; every product/remainder scales linearly.
            var outputs = BatchCopies.scale(result.outputs(), copies);
            if (!result.commit()) continue;
            enqueueVirtualOutputs(outputs);
            PatternProviderPowerCost.consumeRaw(grid, cost * copies);
            recordSuccessfulPush(pattern);
            alertGridTick();
            return maxCraft - copies;
        }
        return pushPattern(pattern, BatchCopies.copy(template)) ? maxCraft - 1 : maxCraft;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!getGridNode().isActive()) {
            return false;
        }
        if (!getAvailablePatterns().contains(patternDetails)) {
            return false;
        }
        if (getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }

        var level = getProviderHost().getLevel();
        if (!(level instanceof ServerLevel sl)) {
            return false;
        }

        double cost = PatternProviderPowerCost.totalCost(inputHolder);
        var grid = getGridNode().getGrid();
        if (!PatternProviderPowerCost.canAfford(grid, cost)) {
            return false;
        }

        long gameTick = sl.getGameTime();
        var binding = getOrComputeBinding(sl, patternDetails, gameTick);
        if (!binding.isMatched()) {
            return false;
        }

        if (tryVirtualPush(sl, patternDetails, inputHolder, binding, gameTick)) {
            PatternProviderPowerCost.consumeRaw(grid, cost);
            recordSuccessfulPush(patternDetails);
            return true;
        }

        if (tryRealDispatch(sl, patternDetails, inputHolder, binding, grid, cost, gameTick)) {
            PatternProviderPowerCost.consumeRaw(grid, cost);
            recordSuccessfulPush(patternDetails);
            return true;
        }

        return false;
    }

    /**
     * Admits at most one virtual lane per craft. Planning is pure;
     * machine mutations run on commit after the lane is acquired.
     */
    private boolean tryVirtualPush(ServerLevel level, IPatternDetails pattern, KeyCounter[] inputs,
                                   PatternBinding binding, long gameTick) {
        for (var candidate : binding.candidates()) {
            if (candidate.mode() != BindingMode.VIRTUAL) {
                continue;
            }
            boolean unbounded = candidate.adapter() instanceof UnboundedVirtualCraftingAdapter;
            if (!unbounded && !virtualPushLimiter.hasCapacity(candidate.lane(), gameTick)) {
                continue;
            }

            var env = resolveLaneEnv(level, candidate);
            if (env == null) {
                continue;
            }

            if (!(candidate.adapter() instanceof VirtualCraftingAdapter virtualAdapter)) {
                continue;
            }

            var result = virtualAdapter.planVirtualWithBinding(
                    env.level(), env.pos(), pattern, inputs, candidate.handle(), getActionSource());
            if (result == null || result.outputs().isEmpty() || virtualBatch.maxCopies(result.outputs()) < 1) {
                continue;
            }

            if (!unbounded && !virtualPushLimiter.tryAcquire(candidate.lane(), gameTick)) {
                continue;
            }
            if (!result.commit()) {
                continue;
            }
            enqueueVirtualOutputs(result.outputs());
            // The last contributing adapter supplies the batch's feedback.
            var sid = virtualAdapter.flushSoundId();
            if (sid != null) {
                pendingFlushSoundId = sid;
            }
            pendingFlushEffect = new PendingVirtualFlushEffect(
                    virtualAdapter, env.level(), env.pos(), candidate.handle());
            return true;
        }
        return false;
    }

    /**
     * Tries to dispatch one real candidate using the three-step flow:
     * extract first, gate on {@code canDispatch}, then plan + execute.
     */
    private boolean tryRealDispatch(ServerLevel level, IPatternDetails pattern, KeyCounter[] inputs,
                                    PatternBinding binding, IGrid grid,
                                    double inputCost, long gameTick) {
        var realCandidates = new ArrayList<LaneCandidate>();
        for (var c : binding.candidates()) {
            if (c.mode() == BindingMode.REAL) {
                realCandidates.add(c);
            }
        }
        if (realCandidates.isEmpty()) {
            return false;
        }

        int total = realCandidates.size();
        var filter = getOrBuildOutputFilter();
        for (int i = 0; i < total; i++) {
            int idx = Math.floorMod(realPushRoundRobin + i, total);
            var candidate = realCandidates.get(idx);

            var env = resolveLaneEnv(level, candidate);
            if (env == null) {
                dispatchFailed(candidate, pattern, env, gameTick);
                continue;
            }

            if (!dispatchReady(candidate, pattern, env, gameTick)) continue;

            // Pre-dispatch harvest must respect the output-return policy.
            if (getProviderHost().getReturnMode() == ReturnMode.AUTO
                    && (!(candidate.adapter() instanceof MekanismMoreMachinesAdapter)
                        || mekReturns.isDue(mekTarget(env), gameTick))) {
                var extracted = candidate.adapter().extractOutputs(
                        env.level(), env.pos(), filter, getActionSource(), adapterScope());
                if (candidate.adapter() instanceof MekanismMoreMachinesAdapter) {
                    var target = mekTarget(env);
                    mekReturnTargets.put(target, candidate.adapter());
                    mekReturns.record(target, gameTick, !extracted.isEmpty());
                }
                if (!extracted.isEmpty()) {
                    enqueueVirtualOutputs(insertOutputsToReturnInv(extracted));
                }
            }

            if (candidate.adapter() instanceof MekanismMoreMachinesAdapter mek && isBlocking()
                    && mek.containsPatternInput(env.level(), env.pos(),
                            ((PatternProviderLogicAccessor) this).ae2ltpp$getPatternInputs())) {
                dispatchFailed(candidate, pattern, env, gameTick);
                continue;
            }

            if (!candidate.adapter().canDispatch(
                    env.level(), env.pos(), candidate.handle(), adapterScope())) {
                dispatchFailed(candidate, pattern, env, gameTick);
                continue;
            }

            var plan = candidate.adapter().planWithBinding(
                    env.level(), env.pos(), pattern, inputs, candidate.handle(),
                    getActionSource(), adapterScope());
            // AUTO extraction above may have charged output power. Recheck before
            // DispatchExecutor can commit inputs so consumeRaw never undercharges.
            if (plan != null && !PatternProviderPowerCost.canAfford(grid, inputCost)) {
                return false;
            }
            if (plan == null
                    || !DispatchExecutor.execute(
                                    plan,
                                    getActionSource(),
                                    getInternalReturnInv(),
                                    residual -> {
                                        enqueueVirtualOutputs(List.of(residual));
                                        alertGridTick();
                                        return true;
                                    })
                            .success()) {
                dispatchFailed(candidate, pattern, env, gameTick);
                continue;
            }

            dispatchSucceeded(candidate, pattern, env, gameTick);
            realPushRoundRobin = (idx + 1) % total;
            return true;
        }
        return false;
    }

    // Tick

    @Override
    protected boolean hasAutoReturnWork() {
        return getProviderHost().getReturnMode() == ReturnMode.AUTO;
    }

    /** Keeps the ticker awake until all queued virtual products are delivered. */
    @Override
    public boolean hasAnyTickWork() {
        return super.hasAnyTickWork() || virtualBatch.hasPending();
    }

    public void tickPendingAdapters(ServerLevel providerLevel) {
        long gameTick = providerLevel.getGameTime();
        if (lastPendingRetryTick != Long.MIN_VALUE
                && elapsedTicks(gameTick, lastPendingRetryTick) < PENDING_RETRY_INTERVAL_TICKS) {
            return;
        }
        lastPendingRetryTick = gameTick;

        if (getProviderHost().getProviderMode() == ProviderMode.NORMAL) {
            for (var face : neighborIndex.adapterFaces(providerLevel)) {
                var adapter = neighborIndex.getAdapter(face);
                if (adapter == null || adapter instanceof VirtualCraftingAdapter) {
                    continue;
                }
                tickPendingAdapter(
                        adapter, providerLevel, getProviderHost().getBlockPos().relative(face));
            }
            return;
        }

        var server = providerLevel.getServer();
        for (var connection : getValidConnections(providerLevel, gameTick)) {
            var targetLevel = server.getLevel(connection.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(connection.pos())) {
                continue;
            }
            var be = targetLevel.getBlockEntity(connection.pos());
            var adapter = MultiblockAdapterRegistry.find(
                    targetLevel, connection.pos(), be);
            if (adapter == null || adapter instanceof VirtualCraftingAdapter) {
                continue;
            }
            tickPendingAdapter(adapter, targetLevel, connection.pos());
        }
    }

    private void tickPendingAdapter(MultiblockAdapter adapter,
                                    ServerLevel level,
                                    BlockPos pos) {
        try {
            adapter.tickPending(level, pos, adapterScope());
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Adapter pending tick failed at {} in {}",
                    pos, level.dimension().location(), e);
        }
    }

    @Override
    public void tickAutoReturn() {
        if (!hasAnyTickWork()) {
            return;
        }

        var level = getProviderHost().getLevel();
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        long gameTick = sl.getGameTime();

        // Virtual batch always ticks; outputs surface every 10 ticks.
        // Persist the transition after a real flush so clearing a delivered
        // batch or retaining a residual cannot be lost across a crash.
        boolean hadPendingVirtualOutputs = virtualBatch.hasPending();
        long previousFlushTick = virtualBatch.getLastFlushTick();
        boolean flushed = virtualBatch.tickFlush(gameTick, stacks -> {
            var residual = insertOutputsToReturnInv(stacks);
            if (residual.isEmpty()) {
                runPendingFlushEffectSafely();
                playFlushSoundSafely(sl);
            }
            return residual;
        });
        if (hadPendingVirtualOutputs && (flushed || previousFlushTick != virtualBatch.getLastFlushTick())) {
            getProviderHost().saveChanges();
        }

        if (getProviderHost().getReturnMode() != ReturnMode.AUTO || !getGridNode().isActive()) {
            return;
        }

        runMekReturnTick(sl, gameTick);

        if (lastAutoReturnTick == Long.MIN_VALUE
                || elapsedTicks(gameTick, lastAutoReturnTick) >= AUTO_RETURN_INTERVAL_TICKS) {
            runAutoReturnTick(sl, gameTick);
            lastAutoReturnTick = gameTick;
        }
    }

    private static long elapsedTicks(long current, long previous) {
        try {
            long elapsed = Math.subtractExact(current, previous);
            return elapsed < 0 ? Long.MAX_VALUE : elapsed;
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private void runAutoReturnTick(ServerLevel level, long gameTick) {
        var filter = getOrBuildOutputFilter();
        if (getProviderHost().getProviderMode() == ProviderMode.NORMAL) {
            autoReturnNormal(level, filter, gameTick);
        } else {
            autoReturnWireless(level, filter, gameTick);
        }
    }

    private void autoReturnNormal(ServerLevel level, AllowedOutputFilter filter, long gameTick) {
        for (var face : neighborIndex.adapterFaces(level)) {
            var adapter = neighborIndex.getAdapter(face);
            if (adapter == null || adapter instanceof VirtualCraftingAdapter) {
                continue;
            }
            if (filter.isEmpty() && !adapter.supportsPatternIndependentHarvest()) {
                continue;
            }
            var pos = getProviderHost().getBlockPos().relative(face);
            pollOutputs(adapter, level, pos, filter, gameTick);
        }
    }

    private void autoReturnWireless(ServerLevel providerLevel,
                                     AllowedOutputFilter filter,
                                     long gameTick) {
        var valid = getValidConnections(providerLevel, gameTick);
        if (valid.isEmpty()) {
            return;
        }
        var server = providerLevel.getServer();
        for (var conn : valid) {
            var targetLevel = server.getLevel(conn.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) {
                continue;
            }
            // BE may be null for block-only targets; pass through to find
            // regardless so each adapter can decide whether it applies.
            var be = targetLevel.getBlockEntity(conn.pos());
            var adapter = MultiblockAdapterRegistry.find(targetLevel, conn.pos(), be);
            if (adapter == null || adapter instanceof VirtualCraftingAdapter) {
                continue;
            }
            if (filter.isEmpty() && !adapter.supportsPatternIndependentHarvest()) {
                continue;
            }
            pollOutputs(adapter, targetLevel, conn.pos(), filter, gameTick);
        }
    }

    private void resetMekScheduling() {
        mekFailures.clear();
        mekReturns.clear();
        mekReturnTargets.clear();
        lastAutoReturnTick = Long.MIN_VALUE;
    }

    private MekTarget mekTarget(LaneEnv env) {
        return new MekTarget(env.level().dimension().location(),
                MekanismMoreMachinesAdapter.resolveMainPos(env.level(), env.pos()).immutable());
    }

    private MekRequest mekRequest(LaneCandidate candidate, IPatternDetails pattern, LaneEnv env) {
        return new MekRequest(mekTarget(env), pattern,
                ((MekanismMoreMachinesAdapter) candidate.adapter()).dispatchMode(env.level(), env.pos()));
    }

    private boolean dispatchReady(LaneCandidate candidate, IPatternDetails pattern, LaneEnv env, long tick) {
        return candidate.adapter() instanceof MekanismMoreMachinesAdapter
                ? mekFailures.isReady(mekRequest(candidate, pattern, env), tick)
                : cooldownTable.isReady(candidate.lane(), tick);
    }

    private void dispatchFailed(LaneCandidate candidate, IPatternDetails pattern, LaneEnv env, long tick) {
        if (env != null && candidate.adapter() instanceof MekanismMoreMachinesAdapter) {
            mekFailures.recordFailure(mekRequest(candidate, pattern, env), tick);
        } else cooldownTable.recordFailure(candidate.lane(), tick);
    }

    private void dispatchSucceeded(LaneCandidate candidate, IPatternDetails pattern, LaneEnv env, long tick) {
        cooldownTable.recordSuccess(candidate.lane());
        if (candidate.adapter() instanceof MekanismMoreMachinesAdapter) {
            mekFailures.recordSuccess(mekRequest(candidate, pattern, env));
            if (getProviderHost().getReturnMode() == ReturnMode.AUTO) {
                var target = mekTarget(env);
                mekReturnTargets.put(target, candidate.adapter());
                mekReturns.wake(target, tick);
                alertGridTick();
            }
        }
    }

    private void pollOutputs(MultiblockAdapter adapter, ServerLevel level, net.minecraft.core.BlockPos pos,
                             AllowedOutputFilter filter, long tick) {
        if (adapter instanceof MekanismMoreMachinesAdapter) {
            var target = mekTarget(new LaneEnv(level, pos));
            mekReturnTargets.put(target, adapter);
            if (!mekReturns.isDue(target, tick)) return;
            var outputs = adapter.extractOutputs(level, target.pos(), filter, getActionSource(), adapterScope());
            mekReturns.record(target, tick, !outputs.isEmpty());
            if (!outputs.isEmpty()) enqueueVirtualOutputs(insertOutputsToReturnInv(outputs));
        } else {
            var outputs = adapter.extractOutputs(level, pos, filter, getActionSource(), adapterScope());
            if (!outputs.isEmpty()) enqueueVirtualOutputs(insertOutputsToReturnInv(outputs));
        }
    }

    private void runMekReturnTick(ServerLevel providerLevel, long tick) {
        if (!mekReturns.hasDue(tick)) return;
        var filter = getOrBuildOutputFilter();
        for (var entry : mekReturnTargets.entrySet()) {
            if (!mekReturns.isDue(entry.getKey(), tick)) continue;
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    entry.getKey().dimension());
            var level = providerLevel.getServer().getLevel(key);
            if (level == null || !level.isLoaded(entry.getKey().pos())) continue;
            pollOutputs(entry.getValue(), level, entry.getKey().pos(), filter, tick);
        }
    }

    // Binding

    private PatternBinding getOrComputeBinding(ServerLevel level, IPatternDetails pattern, long gameTick) {
        var existing = bindingTable.getFresh(pattern, gameTick);
        if (existing != null) {
            return existing;
        }
        var fresh = computeBinding(level, pattern, gameTick);
        bindingTable.put(pattern, fresh);
        return fresh;
    }

    private PatternBinding computeBinding(ServerLevel level, IPatternDetails pattern, long gameTick) {
        var candidates = new ArrayList<LaneCandidate>();
        var mode = getProviderHost().getProviderMode();
        var installedCard = installedAdapterStack();

        if (mode == ProviderMode.NORMAL) {
            for (var face : neighborIndex.adapterFaces(level)) {
                var adapter = neighborIndex.getAdapter(face);
                if (adapter == null) {
                    continue;
                }
                var pos = getProviderHost().getBlockPos().relative(face);
                if (!isAdapterUnlocked(adapter, level, pos, installedCard)) {
                    continue;
                }
                var result = adapter.bind(level, pos, pattern);
                if (result != null) {
                    candidates.add(new LaneCandidate(
                            new LaneKey.FaceLane(face), adapter, result.handle(), result.mode()));
                }
            }
        } else {
            var server = level.getServer();
            for (var conn : getValidConnections(level, gameTick)) {
                var targetLevel = server.getLevel(conn.dimension());
                if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) {
                    continue;
                }
                // BE may be null for non-tile blocks; each adapter's
                // recognizesMain must filter for itself.
                var be = targetLevel.getBlockEntity(conn.pos());
                var adapter = MultiblockAdapterRegistry.find(targetLevel, conn.pos(), be);
                if (adapter == null) {
                    continue;
                }
                if (!isAdapterUnlocked(adapter, targetLevel, conn.pos(), installedCard)) {
                    continue;
                }
                var result = adapter.bind(targetLevel, conn.pos(), pattern);
                if (result != null) {
                    candidates.add(new LaneCandidate(
                            new LaneKey.ConnLane(conn), adapter, result.handle(), result.mode()));
                }
            }
        }

        for (var adapter : MultiblockAdapterRegistry.activeAdapters()) {
            if (!(adapter instanceof ProviderInternalCraftingAdapter internal)
                    || !isAdapterUnlocked(adapter, level, getProviderHost().getBlockPos(), installedCard)) continue;
            var result = adapter.bind(level, getProviderHost().getBlockPos(), pattern);
            if (result != null) {
                candidates.add(new LaneCandidate(new LaneKey.InternalLane(internal.internalLaneId()),
                        adapter, result.handle(), result.mode()));
            }
        }
        return candidates.isEmpty()
                ? PatternBinding.unmatched(gameTick)
                : new PatternBinding(candidates, gameTick);
    }

    /**
     * Resolves a {@link LaneCandidate} to its live world position and verifies the
     * adapter still recognizes the block there. Returns {@code null} when the
     * binding is stale (block changed / chunk unloaded / wireless target gone).
     */
    @Nullable
    private LaneEnv resolveLaneEnv(ServerLevel providerLevel, LaneCandidate candidate) {
        var lane = candidate.lane();
        if (lane instanceof LaneKey.InternalLane && candidate.adapter() instanceof ProviderInternalCraftingAdapter) {
            return isAdapterUnlocked(candidate.adapter(), providerLevel, getProviderHost().getBlockPos(),
                    ((PackagedPatternProviderBlockEntity) getProviderHost()).getInstalledAdapterStack())
                    ? new LaneEnv(providerLevel, getProviderHost().getBlockPos()) : null;
        }
        if (lane instanceof LaneKey.FaceLane faceLane) {
            var pos = getProviderHost().getBlockPos().relative(faceLane.face());
            if (!providerLevel.isLoaded(pos)) {
                // The adjacent chunk may be temporarily unloaded. Preserve any
                // pending adapter ownership until the target is confirmed gone.
                return null;
            }
            // BE may be null for plain blocks. Defer the "is this still my
            // multiblock?" check to recognizesMain, which each adapter
            // implements with the right BE / BlockState mix.
            var be = providerLevel.getBlockEntity(pos);
            if (!candidate.adapter().recognizesMain(providerLevel, pos, be)) {
                adapterScope().clearFlagsForTarget(pos);
                return null;
            }
            return new LaneEnv(providerLevel, pos);
        }
        if (lane instanceof LaneKey.ConnLane connLane) {
            var conn = connLane.connection();
            var targetLevel = providerLevel.getServer().getLevel(conn.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) {
                // An unloaded target may only be temporarily absent; preserve
                // pending ownership until the connection validator removes it.
                return null;
            }
            var be = targetLevel.getBlockEntity(conn.pos());
            if (!candidate.adapter().recognizesMain(targetLevel, conn.pos(), be)) {
                // The chunk is loaded, so this is a confirmed replacement/removal
                // rather than a transient unload. Drop all provider-owned state.
                adapterScope().clearFlagsForTarget(conn.pos());
                return null;
            }
            return new LaneEnv(targetLevel, conn.pos());
        }
        return null;
    }

    private void pruneStaleLaneState() {
        Set<LaneKey> active = new HashSet<>();
        for (var adapter : MultiblockAdapterRegistry.activeAdapters()) {
            if (adapter instanceof ProviderInternalCraftingAdapter internal) {
                active.add(new LaneKey.InternalLane(internal.internalLaneId()));
            }
        }
        for (var dir : EnumSet.allOf(Direction.class)) {
            active.add(new LaneKey.FaceLane(dir));
        }
        for (var conn : getProviderHost().getConnections()) {
            active.add(new LaneKey.ConnLane(conn));
        }
        cooldownTable.retainAll(active);
        virtualPushLimiter.retainAll(active);
    }

    // Output delivery

    /**
     * Delivers virtual outputs up to the grid's current capacity and power budget.
     * Any residual stays in the batch because there is no remote machine to re-poll.
     */
    private List<GenericStack> insertOutputsToReturnInv(List<GenericStack> outputs) {
        if (outputs.isEmpty()) {
            return List.of();
        }
        var grid = getGridNode().getGrid();
        var returnInv = getInternalReturnInv();
        var retained = new LinkedHashMap<AEKey, Long>();

        for (var stack : outputs) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                continue;
            }
            long total = stack.amount();
            long actuallyInserted = 0;
            long affordable;
            try {
                affordable = PatternProviderPowerCost.maxAffordable(grid, stack.what(), total);
            } catch (RuntimeException | LinkageError e) {
                LOG.warn("Virtual output affordability check failed for {} x{}", stack.what(), total, e);
                affordable = 0;
            }
            if (affordable > 0) {
                long inserted;
                try {
                    inserted = Math.max(0L, Math.min(affordable,
                            returnInv.insert(0, stack.what(), affordable, Actionable.MODULATE)));
                } catch (RuntimeException | LinkageError e) {
                    LOG.warn("Virtual output insertion failed for {} x{}", stack.what(), affordable, e);
                    inserted = 0;
                }
                if (inserted > 0) {
                    // The item is already in AE2's return inventory. If charging
                    // unexpectedly fails, retain the delivered amount rather than
                    // requeueing it and duplicating the product.
                    try {
                        PatternProviderPowerCost.consume(grid, stack.what(), inserted);
                    } catch (RuntimeException | LinkageError e) {
                        LOG.warn("Virtual output energy charge failed after delivery for {} x{}",
                                stack.what(), inserted, e);
                    }
                    actuallyInserted = inserted;
                }
            }
            long undelivered = total - actuallyInserted;
            if (undelivered > 0) {
                addMergedResidual(retained, stack.what(), undelivered);
            }
        }

        if (!retained.isEmpty()) {
            try {
                alertGridTick();
            } catch (RuntimeException | LinkageError e) {
                // Delivery accounting is already complete. Never let a ticker
                // notification failure restore products that reached returnInv.
                LOG.warn("Could not wake provider for retained virtual outputs", e);
            }
        }
        var residuals = new ArrayList<GenericStack>(retained.size());
        for (var entry : retained.entrySet()) {
            residuals.add(new GenericStack(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(residuals);
    }

    private static void addMergedResidual(Map<AEKey, Long> residuals,
                                          AEKey key,
                                          long amount) {
        if (key == null || amount <= 0) {
            return;
        }
        var previous = residuals.get(key);
        if (previous == null) {
            residuals.put(key, amount);
            return;
        }
        long merged;
        try {
            merged = Math.addExact(previous, amount);
        } catch (ArithmeticException overflow) {
            merged = Long.MAX_VALUE;
        }
        residuals.put(key, merged);
    }

    /** Plays the contributing adapter's sound after delivery; batches without a sound stay silent. */
    private void playFlushSoundSafely(ServerLevel level) {
        try {
            var sound = resolveFlushSoundEvent();
            // Consume the cached id either way so a stale id from one batch can't
            // bleed into the next; the next push will set a fresh one.
            pendingFlushSoundId = null;
            if (sound == null) {
                return;
            }
            var pos = getProviderHost().getBlockPos();
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.5f, 1.0f);
        } catch (RuntimeException | LinkageError e) {
            pendingFlushSoundId = null;
            LOG.warn("Virtual batch flush sound failed", e);
        }
    }

    /** Returns the cached sound event, or {@code null} if it is absent from the registry. */
    @Nullable
    private SoundEvent resolveFlushSoundEvent() {
        var id = pendingFlushSoundId;
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.SOUND_EVENT.get(id);
    }

    private void runPendingFlushEffectSafely() {
        var effect = pendingFlushEffect;
        pendingFlushEffect = null;
        if (effect == null) {
            return;
        }
        try {
            effect.adapter().onVirtualBatchFlush(
                    effect.level(), effect.pos(), effect.handle(), getActionSource());
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Virtual batch adapter flush effect failed at {} in {}",
                    effect.pos(), effect.level().dimension().location(), e);
        }
    }

    // Packaged core gating

    /**
     * @return the packaged-core ItemStack currently installed in the provider's
     * adapter slot, or {@link ItemStack#EMPTY} when the slot is empty.
     */
    private ItemStack installedAdapterStack() {
        if (getProviderHost() instanceof PackagedPatternProviderBlockEntity packaged) {
            return packaged.getInstalledAdapterStack();
        }
        return ItemStack.EMPTY;
    }

    /**
     * Decides whether the adapter at {@code pos} is allowed to participate in
     * binding given the currently-installed packaged core. Adapters whose
     * {@code requiredAdapterId} returns null are always allowed; otherwise the
     * installed core must {@link MultiblockAdapterItem#covers} the required id
     * (this handles EC's tier-cover-down chain transparently).
     */
    private static boolean isAdapterUnlocked(MultiblockAdapter adapter,
                                              ServerLevel level,
                                              BlockPos pos,
                                              ItemStack installedCard) {
        var required = adapter.requiredAdapterId(level, pos);
        if (required == null) {
            return true;
        }
        return MultiblockAdapterItem.stackCovers(installedCard, required);
    }

    // Helpers

    private record LaneEnv(ServerLevel level, BlockPos pos) {}

    private record PendingVirtualFlushEffect(VirtualCraftingAdapter adapter,
                                             ServerLevel level,
                                             BlockPos pos,
                                             Object handle) {
    }

}
