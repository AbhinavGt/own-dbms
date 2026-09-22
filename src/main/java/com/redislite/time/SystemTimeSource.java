package com.redislite.time;

public final class SystemTimeSource implements TimeSource {
    @Override public long nowMillis() { return System.currentTimeMillis(); }
}
