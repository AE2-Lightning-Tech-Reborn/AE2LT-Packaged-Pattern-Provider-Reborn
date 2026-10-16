package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import org.slf4j.LoggerFactory;

/** Reports a failing API operation once per process, without retaining worlds or exceptions. */
public final class CompatibilityDiagnostics {
    private static final CompatibilityDiagnostics INSTANCE = new CompatibilityDiagnostics(512,
            (operation, failure) -> LoggerFactory.getLogger(CompatibilityDiagnostics.class).warn(
                    "Packaged provider compatibility failure at {}. Repeated failures are suppressed; "
                            + "check the installed optional-mod version.", operation, failure));

    private final int limit;
    private final Set<String> reported = new HashSet<>();
    private final BiConsumer<String, Throwable> reporter;

    CompatibilityDiagnostics(int limit, BiConsumer<String, Throwable> reporter) {
        this.limit = limit;
        this.reporter = reporter;
    }

    public static void failure(Class<?> owner, String operation, Throwable failure) {
        INSTANCE.report(owner.getName() + "#" + operation, failure);
    }

    void report(String operation, Throwable failure) {
        while (failure instanceof InvocationTargetException wrapped && wrapped.getCause() != null) {
            failure = wrapped.getCause();
        }
        // Messages may contain positions or recipe IDs. Do not use those as cache keys.
        synchronized (reported) {
            if (reported.size() >= limit || !reported.add(operation)) return;
        }
        reporter.accept(operation, failure);
    }
}
