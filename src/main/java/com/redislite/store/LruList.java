package com.redislite.store;

public final class LruList {
    private final Entry head = new Entry(null, null), tail = new Entry(null, null);
    private int size;
    public LruList() { head.next = tail; tail.prev = head; }
    public void addFirst(Entry e) { insert(e, head, head.next); }
    public void remove(Entry e) {
        if (e.prev == null || e.next == null) return;
        e.prev.next = e.next; e.next.prev = e.prev; e.prev = e.next = null; size--;
    }
    public void moveToFront(Entry e) { remove(e); addFirst(e); }
    public Entry removeLast() { if (size == 0) return null; Entry e = tail.prev; remove(e); return e; }
    public int size() { return size; }
    private void insert(Entry e, Entry before, Entry after) {
        e.prev = before; e.next = after; before.next = e; after.prev = e; size++;
    }
}
