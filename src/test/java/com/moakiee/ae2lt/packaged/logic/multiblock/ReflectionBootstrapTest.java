package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ReflectionBootstrapTest {

    @Test
    void resolutionRunsExactlyOnceAcrossConcurrentCallers() throws Exception {
        var bootstrap = new ReflectionBootstrap("test-once");
        var runs = new AtomicInteger();
        int threads = 16;
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        bootstrap.run(runs::incrementAndGet);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS));
            assertEquals(1, runs.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void throwingResolutionIsNeverRetried() {
        var bootstrap = new ReflectionBootstrap("test-throwing");
        var runs = new AtomicInteger();
        Runnable resolution = () -> {
            runs.incrementAndGet();
            throw new IllegalStateException("drifted member");
        };
        bootstrap.run(resolution::run);
        bootstrap.run(resolution::run);
        assertEquals(1, runs.get());
    }

    /**
     * The holder's completion flag is the publication point: any thread that
     * observes a finished run() must also observe every plain-field write the
     * resolution made before returning. Hammers that edge without volatile
     * support on the payload field.
     */
    @Test
    void completionPublishesResolutionWritesToConcurrentReaders() throws Exception {
        for (int round = 0; round < 200; round++) {
            var bootstrap = new ReflectionBootstrap("test-publish");
            var payload = new Payload();
            var readerFailures = new AtomicInteger();
            int readers = 8;
            var published = new CountDownLatch(1);
            var done = new CountDownLatch(readers);
            var pool = Executors.newFixedThreadPool(readers);
            try {
                payload.value = 41;
                bootstrap.run(() -> {
                    payload.value = 42;
                    published.countDown();
                });
                assertTrue(published.await(5, TimeUnit.SECONDS));
                var start = new CountDownLatch(1);
                for (int i = 0; i < readers; i++) {
                    pool.submit(() -> {
                        try {
                            start.await();
                            bootstrap.run(() -> { });
                            if (payload.value != 42) {
                                readerFailures.incrementAndGet();
                            }
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                start.countDown();
                assertTrue(done.await(30, TimeUnit.SECONDS));
                assertEquals(0, readerFailures.get(), "round " + round);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private static final class Payload {
        int value;
    }
}
