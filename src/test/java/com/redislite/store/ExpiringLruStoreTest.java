package com.redislite.store;

import static org.junit.jupiter.api.Assertions.*;

import com.redislite.TestTimeSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpiringLruStoreTest {
    @Test
    void expiresLazilyAndSetClearsExpiry() {
        var time = new TestTimeSource(1000);
        var store = new ExpiringLruStore(time);
        store.set("key", "value");
        assertTrue(store.setExpiryAt("key", 2000));
        assertEquals(1000, store.ttlMillis("key"));
        store.set("key", "new");
        time.advance(5000);
        assertEquals("new", store.get("key").orElseThrow());
        assertEquals(-1, store.ttlMillis("key"));
    }

    @Test
    void staleExpiryRecordCannotDeleteReexpiredKey() {
        var time = new TestTimeSource(0);
        var store = new ExpiringLruStore(time);
        store.set("key", "value");
        store.setExpiryAt("key", 10);
        store.setExpiryAt("key", 100);
        time.advance(50);
        store.sweepExpired(100);
        assertTrue(store.exists("key"));
        time.advance(50);
        store.sweepExpired(100);
        assertFalse(store.exists("key"));
    }

    @Test
    void evictsLeastRecentlyUsedKeys() {
        var evicted = new ArrayList<String>();
        var store = new ExpiringLruStore(new TestTimeSource(0));
        store.setEvictionListener(evicted::add);
        store.setMaxKeys(2);
        store.set("a", "1"); store.set("b", "2");
        assertEquals("1", store.get("a").orElseThrow());
        store.set("c", "3");
        assertFalse(store.exists("b"));
        assertEquals(List.of("b"), evicted);
    }
}
