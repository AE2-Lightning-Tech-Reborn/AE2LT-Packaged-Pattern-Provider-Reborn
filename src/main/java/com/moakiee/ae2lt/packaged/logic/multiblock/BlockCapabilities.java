package com.moakiee.ae2lt.packaged.logic.multiblock;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;

/**
 * Resolves Forge 1.20.1 block-entity capabilities without loading chunks.
 */
public final class BlockCapabilities {

    private BlockCapabilities() {
    }

    /**
     * @return the capability instance exposed at {@code pos} on {@code side}, or
     *         {@code null} when the chunk is unloaded, there is no block entity,
     *         or the block entity does not expose that capability.
     */
    @Nullable
    public static <T> T find(
            Level level, BlockPos pos, Capability<T> capability, @Nullable Direction side) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        return blockEntity.getCapability(capability, side).resolve().orElse(null);
    }
}
