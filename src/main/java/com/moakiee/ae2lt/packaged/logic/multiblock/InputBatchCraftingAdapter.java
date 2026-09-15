package com.moakiee.ae2lt.packaged.logic.multiblock;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * Opt-in for homogeneous input buffering. A one-copy plan must assign each input to a distinct
 * port, have no commit callback, and be safe to scale. Accepted but undelivered inputs remain
 * owned by PP and can be replayed after reload through this stable insertion contract.
 */
public interface InputBatchCraftingAdapter extends MultiblockAdapter {
    ResourceLocation batchAdapterId();

    long insertBatchInput(ServerLevel level, BlockPos pos, Direction side,
                          GenericStack stack, Actionable mode, IActionSource source);
}
