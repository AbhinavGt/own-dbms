package com.redislite.store;

import java.util.HashMap;
import java.util.Optional;

/** HashMap-backed, single-threaded key-value store. */
public final class InMemoryStore implements KeyValueStore {
    private final HashMap<String, String> values = new HashMap<>();

    @Override
    public void set(String key, String value) {
        requireNonNull(key, "key");
        requireNonNull(value, "value");
        values.put(key, value);
    }

    @Override
    public Optional<String> get(String key) {
        requireNonNull(key, "key");
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public boolean delete(String key) {
        requireNonNull(key, "key");
        return values.remove(key) != null;
    }

    @Override
    public boolean exists(String key) {
        requireNonNull(key, "key");
        return values.containsKey(key);
    }

    @Override
    public int size() {
        return values.size();
    }

    private static void requireNonNull(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }
}
