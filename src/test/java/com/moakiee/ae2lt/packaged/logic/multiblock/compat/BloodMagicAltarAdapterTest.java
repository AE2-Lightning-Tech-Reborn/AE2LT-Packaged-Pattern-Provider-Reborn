package com.moakiee.ae2lt.packaged.logic.multiblock.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.stacks.AEItemKey;

import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;

class BloodMagicAltarAdapterTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void recoversOnlyTheExactSingleInsertedInput() {
        var handler = new ItemStackHandler(1);
        var expected = markedDiamond("owned");
        handler.setStackInSlot(0, expected.copy());

        assertEquals(1, BloodMagicAltarAdapter.recoverInput(handler, expected).getCount());
        assertTrue(handler.getStackInSlot(0).isEmpty());

        handler.setStackInSlot(0, markedDiamond("other"));
        assertTrue(BloodMagicAltarAdapter.recoverInput(handler, expected).isEmpty());
        assertEquals(1, handler.getStackInSlot(0).getCount());

        handler.setStackInSlot(0, com.moakiee.ae2lt.packaged.compat.LegacyStacks.copyWithCount(expected, 2));
        assertTrue(BloodMagicAltarAdapter.recoverInput(handler, expected).isEmpty());
        assertEquals(2, handler.getStackInSlot(0).getCount());
    }

    @Test
    void idOnlyOutputMatchesDifferentNbtButStillRequiresExactCountAndItem() {
        var output = markedDiamond("crafted");
        var expected = AEItemKey.of(markedDiamond("pattern"));

        assertFalse(BloodMagicAltarAdapter.matchesOutput(expected, 1, output, false));
        assertTrue(BloodMagicAltarAdapter.matchesOutput(expected, 1, output, true));
        assertFalse(BloodMagicAltarAdapter.matchesOutput(expected, 2, output, true));
        assertFalse(BloodMagicAltarAdapter.matchesOutput(expected, 1, new ItemStack(Items.EMERALD), true));
    }

    @Test
    void minimumTierUsesBloodMagicOrdinalComparison() {
        assertTrue(BloodMagicAltarAdapter.meetsMinimumTier(1, 0));
        assertTrue(BloodMagicAltarAdapter.meetsMinimumTier(3, 2));
        assertFalse(BloodMagicAltarAdapter.meetsMinimumTier(2, 2));
        assertFalse(BloodMagicAltarAdapter.meetsMinimumTier(1, 2));
    }

    private static ItemStack markedDiamond(String mark) {
        var stack = new ItemStack(Items.DIAMOND);
        stack.getOrCreateTag().putString("mark", mark);
        return stack;
    }
}
