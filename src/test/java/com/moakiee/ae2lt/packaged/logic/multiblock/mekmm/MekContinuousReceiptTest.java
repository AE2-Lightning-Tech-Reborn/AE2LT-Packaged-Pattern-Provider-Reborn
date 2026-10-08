package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;
import net.minecraft.world.item.Items;

class MekContinuousReceiptTest {
    @BeforeAll static void bootstrap() { MinecraftTestBootstrap.initialize(); }
    private static GenericStack output(long n) { return new GenericStack(AEItemKey.of(Items.STONE), n); }
    @Test void newOrdersRetainPriorUnharvestedOutputs() {
        var previous = List.of(output(3));
        var combined = MekanismMoreMachinesAdapter.mergeExpected(previous, List.of(output(5)));
        assertEquals(List.of(output(8)), combined);
        assertEquals(List.of(output(3)), previous);
    }
    @Test void receiptOverflowRejectsTheNewOrder() {
        assertNull(MekanismMoreMachinesAdapter.mergeExpected(List.of(output(Long.MAX_VALUE)), List.of(output(1))));
    }
    @Test void differentOutputKeysRemainSeparate() {
        var other = new GenericStack(AEItemKey.of(Items.DIRT), 2);
        assertEquals(List.of(output(3), other), MekanismMoreMachinesAdapter.mergeExpected(List.of(output(3)), List.of(other)));
    }
}
