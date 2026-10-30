package com.moakiee.ae2lt.packaged.logic.resonant;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class ResonantRangeTest {
    @Test
    void rangeIsASphereWithInclusiveBoundary() {
        assertTrue(ResonantRange.contains(BlockPos.ZERO, new BlockPos(32, 0, 0), 32));
        assertTrue(ResonantRange.contains(BlockPos.ZERO, new BlockPos(0, -32, 0), 32));
        assertFalse(ResonantRange.contains(BlockPos.ZERO, new BlockPos(32, 1, 0), 32));
        assertFalse(ResonantRange.contains(BlockPos.ZERO, new BlockPos(33, 0, 0), 32));
    }

    @Test
    void subtractionCannotWrapExtremeCoordinatesIntoRange() {
        assertFalse(ResonantRange.contains(new BlockPos(Integer.MIN_VALUE, 0, 0),
                new BlockPos(Integer.MAX_VALUE, 0, 0), 32));
        assertFalse(ResonantRange.contains(BlockPos.ZERO, BlockPos.ZERO, -1));
        assertTrue(ResonantRange.contains(new BlockPos(-100, 70, 100), new BlockPos(-103, 74, 100), 5));
    }
}
