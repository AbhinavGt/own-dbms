package com.redislite.store;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public final class ExpirySweeper {
    private static final Logger LOG = Logger.getLogger(ExpirySweeper.class.getName());
    private final ExpiringLruStore store; private final ReentrantLock lock; private final long interval; private final int batch;
    private ScheduledExecutorService executor;
    public ExpirySweeper(ExpiringLruStore store, ReentrantLock lock, long intervalMillis, int batchSize) {
        if (intervalMillis <= 0 || batchSize <= 0) throw new IllegalArgumentException();
        this.store=store; this.lock=lock; interval=intervalMillis; batch=batchSize;
    }
    public synchronized void start() {
        if (executor != null) return;
        executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"expiry-sweeper");t.setDaemon(true);return t;});
        executor.scheduleWithFixedDelay(this::tick, interval, interval, TimeUnit.MILLISECONDS);
    }
    private void tick() {
        try {
            for (int i=0;i<4;i++) {
                lock.lock(); int removed;
                try { removed=store.sweepExpired(batch); } finally { lock.unlock(); }
                if (removed < batch) break;
            }
        } catch (RuntimeException e) { LOG.warning("Expiry sweep failed: "+e); }
    }
    public synchronized void stop() {
        if (executor == null) return;
        executor.shutdownNow(); try { executor.awaitTermination(2,TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        executor=null;
    }
}
