package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class VirtualBatchAccumulatorNbtTest {
    @Test
    void declaresNbtRoundTripAndFailureRecovery() throws IOException {
        var source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/packaged/logic/multiblock/binding/"
                        + "VirtualBatchAccumulator.java"));
        assertTrue(source.contains("writeToNBT(CompoundTag tag)"));
        assertTrue(source.contains("readFromNBT(CompoundTag tag)"));
        assertTrue(source.contains("private static CompoundTag writeStack(AEKey key, long amount)"));
        assertTrue(source.contains("private static GenericStack readStack(CompoundTag tag)"));
        assertTrue(source.contains("if (!tag.contains(TAG_KIND, Tag.TAG_STRING))"));
        assertTrue(source.contains("case \"i\""));
        assertTrue(source.contains("case \"f\""));
        assertTrue(source.contains("case \"g\""));
        assertTrue(source.contains("mergePending(stack.what(), stack.amount())"));
        assertTrue(source.contains("Math.addExact(previous, amount)"));
        assertTrue(source.contains("Math.subtractExact(gameTick, lastFlushTick)"));
        assertTrue(source.contains("elapsed = Long.MAX_VALUE"));
        assertTrue(source.contains("Virtual batch flush failed; retaining"));
        var writer = source.substring(source.indexOf("public void writeToNBT("), source.indexOf("public void readFromNBT("));
        assertFalse(writer.contains("snapshotStacks()"));
        assertTrue(writer.contains("pending.entrySet()"));
    }
}
