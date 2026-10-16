package com.moakiee.ae2lt.packaged.logic.multiblock;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;

/**
 * Recipes without an in-world delay; outputs enter the provider's batch accumulator.
 * Virtual lanes bypass real-machine cooldowns and may serve multiple providers.
 */
public interface VirtualCraftingAdapter extends MultiblockAdapter {

    /**
     * Plans from the binding, or returns {@code null} if the live state cannot craft.
     * Must not mutate the world; effects belong in {@link VirtualCraftingResult#commit()}
     * after the provider acquires the virtual lane.
     */
    @Nullable
    VirtualCraftingResult planVirtualWithBinding(ServerLevel level, BlockPos mainPos,
                                                 IPatternDetails pattern, KeyCounter[] inputs,
                                                 Object handle, IActionSource source);

    /**
     * Sound played when a virtual batch reaches the return inventory.
     * {@code null} or an unavailable sound leaves the provider silent.
     */
    @Nullable
    default ResourceLocation flushSoundId() {
        return null;
    }

    /**
     * Machine feedback when the batch reaches the return inventory.
     * This hook must not place or consume recipe inputs.
     */
    default void onVirtualBatchFlush(ServerLevel level, BlockPos mainPos,
                                     Object handle, IActionSource source) {
    }
}
