package com.moakiee.ae2lt.packaged.logic.multiblock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a reflection bootstrap at most once.
 *
 * <p>The completion flag is published after the resolution block returns
 * or throws, so a reader that observes completion is also guaranteed to
 * observe every handle resolved inside the block. A resolution that fails
 * is never retried: unresolved handles keep the adapter disabled.
 */
public final class ReflectionBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(ReflectionBootstrap.class);

    private final String owner;
    private volatile boolean completed;

    public ReflectionBootstrap(String owner) {
        this.owner = owner;
    }

    public void run(Resolution resolution) {
        if (completed) {
            return;
        }
        synchronized (this) {
            if (completed) {
                return;
            }
            try {
                resolution.run();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.warn("[ae2ltpp] Reflection bootstrap failed for {}: {}", owner, e.toString());
            } finally {
                completed = true;
            }
        }
    }

    @FunctionalInterface
    public interface Resolution {
        void run() throws ReflectiveOperationException;
    }
}
