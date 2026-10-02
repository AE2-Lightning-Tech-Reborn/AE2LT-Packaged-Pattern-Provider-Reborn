package com.moakiee.ae2lt.packaged.logic.multiblock;

import net.minecraft.resources.ResourceLocation;

/** A core-powered conversion that needs no adjacent block or wireless target. */
public interface ProviderInternalCraftingAdapter extends LinearVirtualCraftingAdapter {
    ResourceLocation internalLaneId();
}
