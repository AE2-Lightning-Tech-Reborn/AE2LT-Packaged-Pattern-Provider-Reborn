package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.packaged.patternprovider.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

public interface MultiblockAdapter {

    /** Smaller value matches first. */
    int priority();

    /** Cheap main-block check; targets without a block entity pass {@code null}. */
    boolean recognizesMain(ServerLevel level, BlockPos pos, @Nullable BlockEntity be);

    /**
     * Required core for the queried block, or {@code null} if none is required.
     * Tiered adapters return the block's tier; core coverage is checked separately.
     */
    @Nullable
    default ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return null;
    }

    /**
     * Resolves the recipe and structure at bind time, or returns {@code null}.
     * Planning uses the cached handle instead of repeating the full recipe search.
     */
    @Nullable
    BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern);

    /** Checks readiness before planning; failure puts the lane on cooldown. */
    boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle);

    /** Allows adapters with provider-owned state to clear stale dispatch markers. */
    default boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle,
                                AdapterPersistentScope scope) {
        return canDispatch(level, mainPos, handle);
    }

    /**
     * Retries pending work without a fresh pattern push or output extraction.
     * Must fail closed and must not dispatch new inputs.
     */
    default void tickPending(ServerLevel level, BlockPos mainPos,
                             AdapterPersistentScope scope) {
    }

    /** Plans from the cached handle; live-state validation must not repeat a full recipe search. */
    @Nullable
    DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                  IPatternDetails pattern, KeyCounter[] inputs,
                                  Object handle, IActionSource source);

    /**
     * Plans with provider-owned persistent state.
     * {@code onCommit} runs after payment and may record harvest ownership.
     */
    @Nullable
    default DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                          IPatternDetails pattern, KeyCounter[] inputs,
                                          Object handle, IActionSource source,
                                          AdapterPersistentScope scope) {
        return planWithBinding(level, mainPos, pattern, inputs, handle, source);
    }

    /** Returns allowed outputs and removes each returned stack from the machine. */
    List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                      AllowedOutputFilter filter,
                                      IActionSource source);

    /**
     * Harvests using provider-owned state; clear ownership after a successful pull
     * so a later manual craft cannot be harvested under a stale dispatch marker.
     */
    default List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                               AllowedOutputFilter filter,
                                               IActionSource source,
                                               AdapterPersistentScope scope) {
        return extractOutputs(level, mainPos, filter, source);
    }

    /** Whether persisted dispatch ownership permits harvesting without installed patterns. */
    default boolean supportsPatternIndependentHarvest() {
        return false;
    }

    /**
     * Accepts results from adapter-validated output slots by default.
     * Shared inventories or broad dropped-item scans may require the pattern allow-list.
     */
    default boolean allowsAutoReturn(ServerLevel level, BlockPos mainPos,
                                     AllowedOutputFilter filter, AEKey key) {
        return true;
    }
}
