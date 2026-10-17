package com.moakiee.ae2lt.packaged.logic.multiblock;

/**
 * Explicit opt-in for homogeneous batches. Planning must have no world/resource side effects,
 * and identical concrete inputs must produce identical outputs and remainders for every copy.
 * Resource-consuming or stateful adapters must keep the ordinary single-copy contract.
 */
public interface LinearVirtualCraftingAdapter extends VirtualCraftingAdapter {
}
