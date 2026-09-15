package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MekItemInsertionTest {

    /** Mutable view over a slot list simulating an IItemHandler. */
    private record SlotAccess(List<Integer> values) implements MekItemInsertion.SlotAccess<Integer> {
        @Override
        public int slots() {
            return values.size();
        }

        @Override
        public Integer insert(int slot, Integer stack, boolean simulate) {
            int capacity = 16 - values.get(slot);
            int accepted = Math.min(capacity, stack);
            if (!simulate && accepted > 0) {
                values.set(slot, values.get(slot) + accepted);
            }
            return stack - accepted;
        }

        @Override
        public int amount(Integer stack) {
            return stack;
        }
    }

    @Test
    void fillsSingleSlotFully() {
        var slots = new ArrayList<>(List.of(0, 0));
        assertEquals(10, MekItemInsertion.insertIntoAnySlot(10, false, new SlotAccess(slots)));
        assertEquals(List.of(10, 0), slots);
    }

    @Test
    void splitsAcrossSlotsWhenFirstIsPartiallyFull() {
        var slots = new ArrayList<>(List.of(10, 0));
        assertEquals(12, MekItemInsertion.insertIntoAnySlot(12, false, new SlotAccess(slots)));
        assertEquals(List.of(16, 6), slots);
    }

    @Test
    void simulateModeLeavesSlotsUntouched() {
        var slots = new ArrayList<>(List.of(10, 0));
        assertEquals(12, MekItemInsertion.insertIntoAnySlot(12, true, new SlotAccess(slots)));
        assertEquals(List.of(10, 0), slots);
    }

    @Test
    void reportsOnlyPartiallyAcceptedAmount() {
        var slots = new ArrayList<>(List.of(10, 14));
        assertEquals(8, MekItemInsertion.insertIntoAnySlot(20, false, new SlotAccess(slots)));
        assertEquals(List.of(16, 16), slots);
    }

    @Test
    void rejectsEmptyStack() {
        var slots = new ArrayList<>(List.of(0));
        assertEquals(0, MekItemInsertion.insertIntoAnySlot(0, false, new SlotAccess(slots)));
        assertEquals(List.of(0), slots);
    }
}
