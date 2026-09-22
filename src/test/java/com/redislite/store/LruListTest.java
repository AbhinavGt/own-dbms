package com.redislite.store;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LruListTest {
    @Test
    void maintainsMostRecentFirstOrder() {
        var list = new LruList();
        var a = new Entry("a", "1");
        var b = new Entry("b", "2");
        var c = new Entry("c", "3");
        list.addFirst(a); list.addFirst(b); list.addFirst(c);
        assertSame(a, list.removeLast());
        list.moveToFront(a);
        assertSame(b, list.removeLast());
        assertSame(c, list.removeLast());
        assertSame(a, list.removeLast());
        assertNull(list.removeLast());
        assertEquals(0, list.size());
    }
}
