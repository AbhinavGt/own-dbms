package com.redislite.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SynchronizedRequestProcessorTest {
    @Test
    @Timeout(10)
    void delegatesAndReturnsReply() {
        var processor = new SynchronizedRequestProcessor(line -> new Reply(line, false));

        assertEquals(new Reply("PING", false), processor.process("PING"));
    }

    @Test
    @Timeout(10)
    void releasesLockWhenDelegateThrows() {
        var calls = new AtomicInteger();
        var processor = new SynchronizedRequestProcessor(line -> {
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("expected");
            }
            return new Reply("OK", false);
        });

        assertThrows(IllegalStateException.class, () -> processor.process("first"));
        assertEquals(new Reply("OK", false), processor.process("second"));
    }

    @Test
    @Timeout(10)
    void allowsOnlyOneDelegateCallAtATime() throws Exception {
        var inside = new AtomicInteger();
        var maximum = new AtomicInteger();
        var start = new CountDownLatch(1);
        RequestProcessor delegate = line -> {
            int current = inside.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(10);
                return new Reply("OK", false);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("delegate interrupted", exception);
            } finally {
                inside.decrementAndGet();
            }
        };
        var processor = new SynchronizedRequestProcessor(delegate);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        start.await();
                        processor.process("PING");
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("worker interrupted", exception);
                    }
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(1, maximum.get());
    }
}
