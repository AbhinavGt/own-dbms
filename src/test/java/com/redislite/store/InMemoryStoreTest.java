package com.redislite.store;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class InMemoryStoreTest {
    @Test void setThenGetReturnsValue() {
        var store = new InMemoryStore();
        store.set("a", "1");
        assertEquals("1", store.get("a").orElseThrow());
    }

    @Test void missingGetIsEmpty() {
        assertTrue(new InMemoryStore().get("missing").isEmpty());
    }

    @Test void setOverwrites() {
        var store = new InMemoryStore();
        store.set("a", "1");
        store.set("a", "2");
        assertEquals("2", store.get("a").orElseThrow());
    }

    @Test void deleteReportsPresence() {
        var store = new InMemoryStore();
        store.set("a", "1");
        assertTrue(store.delete("a"));
        assertFalse(store.delete("a"));
    }

    @Test void existsTracksSetAndDelete() {
        var store = new InMemoryStore();
        store.set("a", "1");
        assertTrue(store.exists("a"));
        store.delete("a");
        assertFalse(store.exists("a"));
    }

    @Test void sizeTracksUniqueKeysAndDeletes() {
        var store = new InMemoryStore();
        assertEquals(0, store.size());
        store.set("a", "1");
        store.set("b", "2");
        store.set("a", "3");
        assertEquals(2, store.size());
        store.delete("a");
        assertEquals(1, store.size());
    }

    @Test void nullKeyOrValueIsRejected() {
        var store = new InMemoryStore();
        assertThrows(IllegalArgumentException.class, () -> store.set(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> store.set("x", null));
        assertThrows(IllegalArgumentException.class, () -> store.get(null));
        assertThrows(IllegalArgumentException.class, () -> store.delete(null));
        assertThrows(IllegalArgumentException.class, () -> store.exists(null));
    }
}
