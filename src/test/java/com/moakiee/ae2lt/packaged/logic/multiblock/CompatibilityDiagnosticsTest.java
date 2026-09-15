package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CompatibilityDiagnosticsTest {
    @Test
    void reportsRootCauseOnceEvenWhenPositionsAndMessagesChange() {
        var failures = new ArrayList<Throwable>();
        var diagnostics = new CompatibilityDiagnostics(8, (operation, failure) -> failures.add(failure));
        var cause = new IllegalStateException("first position");
        diagnostics.report("adapter#insert", new InvocationTargetException(cause));
        for (int i = 0; i < 100; i++) diagnostics.report("adapter#insert", new IllegalStateException("position " + i));
        assertEquals(1, failures.size());
        assertSame(cause, failures.getFirst());
        diagnostics.report("adapter#extract", cause);
        assertEquals(2, failures.size());
    }

    @Test
    void boundsDistinctReportsWithoutEvictingAndReloggingOldFailures() {
        var count = new AtomicInteger();
        var diagnostics = new CompatibilityDiagnostics(2, (operation, failure) -> count.incrementAndGet());
        for (int i = 0; i < 100; i++) diagnostics.report("operation" + i, new Exception());
        diagnostics.report("operation0", new Exception());
        assertEquals(2, count.get());
    }

    @Test
    void concurrentFailuresOnlyReportOnce() {
        var count = new AtomicInteger();
        var diagnostics = new CompatibilityDiagnostics(8, (operation, failure) -> count.incrementAndGet());
        IntStream.range(0, 1000).parallel().forEach(i -> diagnostics.report("adapter#lookup", new Exception()));
        assertEquals(1, count.get());
    }
}
