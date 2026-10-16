package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

/**
 * VIRTUAL computes outputs for batched delivery, with adapter-specific costs.
 * REAL inserts inputs into the machine and waits for its ticks to produce outputs.
 */
public enum BindingMode {
    VIRTUAL,
    REAL
}
