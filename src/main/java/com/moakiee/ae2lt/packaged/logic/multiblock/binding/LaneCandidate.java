package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.Objects;

import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;

/**
 * One bound target and adapter handle. A pattern may retain several candidates.
 */
public record LaneCandidate(LaneKey lane, MultiblockAdapter adapter, Object handle, BindingMode mode) {
    public LaneCandidate {
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(mode, "mode");
    }
}
