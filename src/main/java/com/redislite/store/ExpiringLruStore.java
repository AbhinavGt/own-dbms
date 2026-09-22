package com.redislite.store;

import com.redislite.ds.MinHeap;
import com.redislite.time.TimeSource;
import java.util.HashMap;
import java.util.Optional;

public class ExpiringLruStore implements KeyValueStore {
    private record ExpiryRecord(String key, long expireAtMillis) {}
    private final TimeSource time;
    private final HashMap<String, Entry> map = new HashMap<>();
    private final LruList lru = new LruList();
    private final MinHeap<ExpiryRecord> heap = new MinHeap<>((a,b) -> Long.compare(a.expireAtMillis(), b.expireAtMillis()));
    private int maxKeys;
    private EvictionListener listener;
    public ExpiringLruStore(TimeSource time) { this.time = java.util.Objects.requireNonNull(time); }
    public void setMaxKeys(int maxKeys) {
        if (maxKeys < 0) throw new IllegalArgumentException("maxKeys must not be negative");
        this.maxKeys = maxKeys; enforceLimit();
    }
    public void setEvictionListener(EvictionListener listener) { this.listener = listener; }
    private Entry findLive(String key) {
        require(key, "key"); Entry e = map.get(key);
        if (e != null && e.expireAtMillis != Entry.NO_EXPIRY && e.expireAtMillis <= time.nowMillis()) { removeEntry(e); return null; }
        return e;
    }
    private void removeEntry(Entry e) { map.remove(e.key); lru.remove(e); }
    @Override public void set(String key, String value) {
        require(key, "key"); require(value, "value"); Entry e = findLive(key);
        if (e != null) { e.value = value; e.expireAtMillis = Entry.NO_EXPIRY; lru.moveToFront(e); return; }
        makeRoom(); e = new Entry(key, value); map.put(key, e); lru.addFirst(e);
    }
    @Override public Optional<String> get(String key) { Entry e = findLive(key); if (e == null) return Optional.empty(); lru.moveToFront(e); return Optional.of(e.value); }
    @Override public boolean delete(String key) { Entry e = findLive(key); if (e == null) return false; removeEntry(e); return true; }
    @Override public boolean exists(String key) { return findLive(key) != null; }
    @Override public int size() { return map.size(); }
    @Override public boolean setExpiryAt(String key, long at) {
        Entry e = findLive(key); if (e == null) return false;
        if (at <= time.nowMillis()) { removeEntry(e); return true; }
        e.expireAtMillis = at; heap.add(new ExpiryRecord(key, at)); return true;
    }
    @Override public long ttlMillis(String key) {
        Entry e = findLive(key); if (e == null) return -2;
        return e.expireAtMillis == Entry.NO_EXPIRY ? -1 : e.expireAtMillis - time.nowMillis();
    }
    public int sweepExpired(int maxWork) {
        int removed = 0, work = 0; long now = time.nowMillis();
        while (work++ < maxWork && !heap.isEmpty() && heap.peek().expireAtMillis() <= now) {
            ExpiryRecord r = heap.poll(); Entry e = map.get(r.key());
            if (e != null && e.expireAtMillis == r.expireAtMillis()) { removeEntry(e); removed++; }
        }
        return removed;
    }
    private void makeRoom() {
        if (maxKeys == 0 || map.size() < maxKeys) return;
        sweepExpired(64);
        while (map.size() >= maxKeys) { Entry e = lru.removeLast(); if (e == null) return; map.remove(e.key); if (listener != null) listener.onEvict(e.key); }
    }
    private void enforceLimit() { while (maxKeys > 0 && map.size() > maxKeys) { Entry e = lru.removeLast(); map.remove(e.key); if (listener != null) listener.onEvict(e.key); } }
    private static void require(String s, String n) { if (s == null) throw new IllegalArgumentException(n + " must not be null"); }
}
