package com.moakiee.ae2lt.packaged.logic.multiblock;

import net.minecraft.resources.ResourceLocation;

/** A conversion supplied by the installed core without a neighbor or wireless target. */
public interface ProviderInternalCraftingAdapter extends VirtualCraftingAdapter {
    ResourceLocation internalLaneId();
}
