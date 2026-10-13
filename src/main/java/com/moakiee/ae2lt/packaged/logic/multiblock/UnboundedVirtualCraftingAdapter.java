package com.moakiee.ae2lt.packaged.logic.multiblock;

/**
 * Pure virtual workbenches whose execution cost does not grow with the batch copy count.
 * CPU copy budgets, available inputs, energy and representable output amounts still apply;
 * the provider adds no per-lane, per-tick copy limit.
 */
public interface UnboundedVirtualCraftingAdapter extends LinearVirtualCraftingAdapter {
}
