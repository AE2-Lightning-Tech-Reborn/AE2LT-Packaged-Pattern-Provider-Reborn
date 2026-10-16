package com.moakiee.ae2lt.packaged.logic;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PendingAmountsTest {
    @Test
    void partialDeliveryKeepsExactRemainderAndOtherKeysCanProgress() {
        var buffer = new PendingAmounts<String>();
        buffer.add("iron", 64);
        buffer.add("gold", 12);
        buffer.deliver(entry -> entry.key().equals("iron") ? 20 : entry.amount());
        assertEquals(List.of(new PendingAmounts.Entry<>("iron", 44L)), buffer.snapshot());
    }

    @Test
    void fullInventoryOrPowerLossDoesNotDiscardOrDuplicateAmounts() {
        var buffer = new PendingAmounts<String>();
        buffer.add("iron", 64);
        for (int tick = 0; tick < 100; tick++) buffer.deliver(entry -> 0);
        assertEquals(List.of(new PendingAmounts.Entry<>("iron", 64L)), buffer.snapshot());
        var delivered = new ArrayList<PendingAmounts.Entry<String>>();
        buffer.deliver(entry -> { delivered.add(entry); return entry.amount(); });
        buffer.deliver(entry -> fail("Already delivered quantity was replayed"));
        assertEquals(List.of(new PendingAmounts.Entry<>("iron", 64L)), delivered);
        assertTrue(buffer.isEmpty());
    }

    @Test
    void laterSinkFailureDoesNotRestoreEarlierReceipts() {
        var buffer = new PendingAmounts<String>();
        buffer.add("iron", 64);
        buffer.add("gold", 12);
        assertThrows(IllegalStateException.class, () -> buffer.deliver(entry -> {
            if (entry.key().equals("gold")) throw new IllegalStateException("unavailable");
            return entry.amount();
        }));
        assertEquals(List.of(new PendingAmounts.Entry<>("gold", 12L)), buffer.snapshot());
    }

    @Test
    void combinedQuantitiesCannotOverflowLong() {
        var buffer = new PendingAmounts<String>();
        buffer.add("iron", Long.MAX_VALUE);
        buffer.add("iron", Long.MAX_VALUE);
        buffer.add("iron", 7);
        BigInteger expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO).add(BigInteger.valueOf(7));
        assertEquals(expected, total(buffer));
        buffer.deliver(entry -> 1);
        assertEquals(expected.subtract(BigInteger.ONE), total(buffer));
        buffer.deliver(PendingAmounts.Entry::amount);
        assertTrue(buffer.isEmpty());
    }

    @Test
    void invalidReceiptsCannotChangeStoredAmounts() {
        var buffer = new PendingAmounts<String>();
        buffer.add("iron", 10);
        assertThrows(IllegalStateException.class, () -> buffer.deliver(entry -> 11));
        assertThrows(IllegalStateException.class, () -> buffer.deliver(entry -> -1));
        assertEquals(List.of(new PendingAmounts.Entry<>("iron", 10L)), buffer.snapshot());
    }

    @Test
    void snapshotRestoresAllOverflowChunksInOrder() {
        var buffer = new PendingAmounts<String>();
        buffer.add("iron", Long.MAX_VALUE);
        buffer.add("iron", 8);
        buffer.add("gold", 12);
        var restored = new PendingAmounts<String>();
        buffer.snapshot().forEach(entry -> restored.add(entry.key(), entry.amount()));
        assertEquals(buffer.snapshot(), restored.snapshot());
        restored.clear();
        assertTrue(restored.isEmpty());
        assertFalse(buffer.isEmpty());
    }

    private BigInteger total(PendingAmounts<String> buffer) {
        return buffer.snapshot().stream().map(entry -> BigInteger.valueOf(entry.amount()))
                .reduce(BigInteger.ZERO, BigInteger::add);
    }
}
