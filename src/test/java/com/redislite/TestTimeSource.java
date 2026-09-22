package com.redislite;

import com.redislite.time.TimeSource;

public final class TestTimeSource implements TimeSource {
    private long now;
    public TestTimeSource(long initial) { now=initial; }
    public void advance(long millis) { now += millis; }
    public void set(long millis) { now=millis; }
    @Override public long nowMillis() { return now; }
}
