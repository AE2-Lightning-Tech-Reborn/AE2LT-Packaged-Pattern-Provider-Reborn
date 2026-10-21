package com.moakiee.ae2lt.packaged.logic.multiblock;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Adapter contract for independent physical input ports that can accept a scaled batch. */
public interface InputBatchCraftingAdapter extends MultiblockAdapter {
    ResourceLocation batchAdapterId();

    long insertBatchInput(ServerLevel level, BlockPos pos, Direction side,
                           GenericStack stack, Actionable mode, IActionSource source);

    /** Builds a one-copy plan without running single-dispatch commit callbacks. */
    default DispatchPlan planBatchWithBinding(ServerLevel level, BlockPos mainPos,
                                               IPatternDetails pattern, KeyCounter[] inputs,
                                               Object handle, IActionSource source,
                                               AdapterPersistentScope scope) {
        return planWithBinding(level, mainPos, pattern, inputs, handle, source, scope);
    }

    /** Records adapter-owned state after a batch has been accepted for delivery. */
    default boolean onBatchAccepted(ServerLevel level, BlockPos mainPos,
                                    IPatternDetails pattern, Object handle, int copies,
                                    AdapterPersistentScope scope) {
        return true;
    }

    /** Renew only the remaining output claim while paid inputs await delivery. */
    default boolean refreshBatchOwnership(ServerLevel level, BlockPos mainPos,
                                          AdapterPersistentScope scope) {
        return false;
    }
}
