package com.redislite.command;

import java.util.concurrent.locks.ReentrantLock;

/** Serializes complete command processing through one global lock. */
public final class SynchronizedRequestProcessor implements RequestProcessor {
    private final RequestProcessor delegate;
    private final ReentrantLock lock;

    public SynchronizedRequestProcessor(RequestProcessor delegate) {
        this(delegate, new ReentrantLock());
    }
    public SynchronizedRequestProcessor(RequestProcessor delegate, ReentrantLock lock) {
        this.delegate = delegate;
        this.lock = java.util.Objects.requireNonNull(lock);
    }

    @Override
    public Reply process(String line) {
        lock.lock();
        try {
            return delegate.process(line);
        } finally {
            lock.unlock();
        }
    }
}
