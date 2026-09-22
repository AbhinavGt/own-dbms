package com.redislite.ds;

import java.util.Comparator;

public final class MinHeap<T> {
    private Object[] items = new Object[16];
    private int size;
    private final Comparator<? super T> comparator;
    public MinHeap(Comparator<? super T> comparator) {
        this.comparator = java.util.Objects.requireNonNull(comparator);
    }
    public void add(T value) {
        if (size == items.length) {
            Object[] next = new Object[items.length * 2];
            System.arraycopy(items, 0, next, 0, size);
            items = next;
        }
        items[size] = value;
        siftUp(size++);
    }
    @SuppressWarnings("unchecked") public T peek() { return size == 0 ? null : (T) items[0]; }
    @SuppressWarnings("unchecked") public T poll() {
        if (size == 0) return null;
        T result = (T) items[0];
        items[0] = items[--size];
        items[size] = null;
        if (size > 0) siftDown(0);
        return result;
    }
    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public void clear() { java.util.Arrays.fill(items, 0, size, null); size = 0; }
    @SuppressWarnings("unchecked") private int compare(int a, int b) {
        return comparator.compare((T) items[a], (T) items[b]);
    }
    private void siftUp(int i) {
        while (i > 0) {
            int p = (i - 1) / 2;
            if (compare(i, p) >= 0) break;
            swap(i, p); i = p;
        }
    }
    private void siftDown(int i) {
        while (true) {
            int left = i * 2 + 1, right = left + 1, best = i;
            if (left < size && compare(left, best) < 0) best = left;
            if (right < size && compare(right, best) < 0) best = right;
            if (best == i) return;
            swap(i, best); i = best;
        }
    }
    private void swap(int a, int b) { Object x = items[a]; items[a] = items[b]; items[b] = x; }
}
