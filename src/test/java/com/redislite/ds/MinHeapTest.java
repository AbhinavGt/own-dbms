package com.redislite.ds;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Comparator;
import org.junit.jupiter.api.Test;

class MinHeapTest {
    @Test
    void emptyHeapReturnsNull() {
        var heap = new MinHeap<Integer>(Comparator.naturalOrder());
        assertTrue(heap.isEmpty());
        assertNull(heap.peek());
        assertNull(heap.poll());
    }

    @Test
    void pollsInSortedOrderAndGrows() {
        var heap = new MinHeap<Integer>(Comparator.naturalOrder());
        for (int i = 9999; i >= 0; i--) heap.add(i);
        for (int i = 0; i < 10000; i++) assertEquals(i, heap.poll());
        assertTrue(heap.isEmpty());
    }

    @Test
    void supportsDuplicatesAndClear() {
        var heap = new MinHeap<Integer>(Comparator.naturalOrder());
        heap.add(2); heap.add(1); heap.add(1);
        assertEquals(1, heap.poll());
        assertEquals(1, heap.poll());
        heap.clear();
        assertEquals(0, heap.size());
        assertNull(heap.poll());
    }
}
