package com.moakiee.ae2lt.packaged.logic.multiblock;

import net.minecraft.resources.Identifier;

/** A core-powered conversion that needs no adjacent block or wireless target. */
public interface ProviderInternalCraftingAdapter extends LinearVirtualCraftingAdapter {
    Identifier internalLaneId();
}
