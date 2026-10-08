package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.Objects;

/**
 * Cached adapter-specific handle and dispatch mode. Only the producing
 * adapter interprets the opaque handle on subsequent pushes.
 */
public record BindingResult(Object handle, BindingMode mode) {
    public BindingResult {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(mode, "mode");
    }
}
