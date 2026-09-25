package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * Homogeneous input buffering with explicit ownership of all expected outputs.
 * Planning must be pure. The batch ownership hook replaces the single-craft commit callback;
 * it must only store idempotent provider metadata, before any input is delivered.
 */
public interface InputBatchCraftingAdapter extends MultiblockAdapter {
    ResourceLocation batchAdapterId();
    long insertBatchInput(ServerLevel level, BlockPos pos, Direction side,
                          GenericStack stack, Actionable mode, IActionSource source);
    /** Keep the remaining claim live while paid inputs are queued; never restore already harvested amounts. */
    boolean refreshBatchOwnership(ServerLevel level, BlockPos mainPos, AdapterPersistentScope scope);
    boolean initializeBatchOwnership(ServerLevel level, BlockPos mainPos,
                                     List<GenericStack> outputs, AdapterPersistentScope scope);
}
