package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;

import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

class VirtualCraftingResultTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void compactConstructorCommitsWithoutSideEffects() {
        var outputs = List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1));
        var result = new VirtualCraftingResult(outputs);

        assertEquals(1, result.outputs().size());
        assertTrue(result.commit());
        assertTrue(result.commit());
    }

    @Test
    void deferredCommitRunsOnlyWhenInvoked() {
        var calls = new AtomicInteger();
        var result = new VirtualCraftingResult(
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 2)),
                () -> {
                    calls.incrementAndGet();
                    return calls.get() == 1;
                });

        assertEquals(0, calls.get());
        assertTrue(result.commit());
        assertEquals(1, calls.get());
        assertFalse(result.commit());
        assertEquals(2, calls.get());
    }
}
