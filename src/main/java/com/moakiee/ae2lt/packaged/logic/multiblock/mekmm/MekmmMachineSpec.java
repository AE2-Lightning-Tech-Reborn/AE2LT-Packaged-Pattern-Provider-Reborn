package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.util.List;
import java.util.function.ToIntFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Controller identity, idle gating and facing-relative ports.
 * modeSelector chooses a port group from the live machine state.
 */
public record MekmmMachineSpec(
        ResourceLocation block,
        String tileClass,
        boolean idleGated,
        List<PortGroup> modes,
        @Nullable ToIntFunction<BlockEntity> modeSelector
) {

    public PortGroup activeMode(BlockEntity be) {
        int idx = modeSelector == null ? 0 : modeSelector.applyAsInt(be);
        if (idx < 0 || idx >= modes.size()) idx = 0;
        return modes.get(idx);
    }

    public enum PortType { ITEM, FLUID, CHEMICAL }

    public record PortDef(MekPortSpec spec, PortType type) {}

    public record PortGroup(List<PortDef> inputs, List<PortDef> outputs) {}
}
