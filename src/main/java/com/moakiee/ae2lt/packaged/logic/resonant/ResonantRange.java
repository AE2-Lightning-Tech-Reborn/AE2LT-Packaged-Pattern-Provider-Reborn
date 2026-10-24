package com.moakiee.ae2lt.packaged.logic.resonant;

import com.moakiee.ae2lt.packaged.PPConfig;
import net.minecraft.core.BlockPos;

/** Check before subtracting/rotating BlockPos, so malformed coordinates cannot wrap into range. */
public final class ResonantRange {
    private ResonantRange() {}

    public static boolean contains(BlockPos anchor, BlockPos child) {
        return contains(anchor, child, PPConfig.RESONANT_INPUT_RANGE.get());
    }

    public static boolean contains(BlockPos anchor, BlockPos child, int radius) {
        long dx = (long) child.getX() - anchor.getX();
        long dy = (long) child.getY() - anchor.getY();
        long dz = (long) child.getZ() - anchor.getZ();
        if (radius < 0 || Math.abs(dx) > radius || Math.abs(dy) > radius || Math.abs(dz) > radius) return false;
        return dx * dx + dy * dy + dz * dz <= (long) radius * radius;
    }
}
