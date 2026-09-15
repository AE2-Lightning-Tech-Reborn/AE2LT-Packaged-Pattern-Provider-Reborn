package com.moakiee.ae2lt.packaged.logic.multiblock.occultism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.packaged.testsupport.MinecraftTestBootstrap;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

class OccultismExactRecoverTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void recoversExactCountAndTagsThenEmptiesSlot() {
        var handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, tagged(3, "a"));
        var recovered = OccultismRitualAdapter.recoverExactInsertion(
                handler, new GenericStack(AEItemKey.of(tagged(3, "a")), 3));

        assertNotNull(recovered);
        assertEquals(3, recovered.amount());
        assertEquals(AEItemKey.of(tagged(3, "a")), recovered.what());
        assertTrue(handler.getStackInSlot(0).isEmpty());
    }

    @Test
    void refusesPartialCountWithoutMutatingSlot() {
        var handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, new ItemStack(Items.DIAMOND, 2));
        var recovered = OccultismRitualAdapter.recoverExactInsertion(
                handler, new GenericStack(AEItemKey.of(Items.DIAMOND), 1));

        assertNull(recovered);
        assertEquals(2, handler.getStackInSlot(0).getCount());
    }

    @Test
    void refusesNbtMismatchWithoutMutatingSlot() {
        var handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, tagged(1, "owned"));
        var recovered = OccultismRitualAdapter.recoverExactInsertion(
                handler, new GenericStack(AEItemKey.of(Items.DIAMOND), 1));

        assertNull(recovered);
        assertTrue(ItemStack.isSameItemSameTags(tagged(1, "owned"), handler.getStackInSlot(0)));
    }

    private static ItemStack tagged(int count, String mark) {
        var stack = new ItemStack(Items.DIAMOND, count);
        stack.getOrCreateTag().putString("mark", mark);
        return stack;
    }
}
