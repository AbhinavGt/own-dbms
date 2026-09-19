package com.redislite.store;

import java.util.Optional;

/** Typed storage abstraction for string keys and values. */
public interface KeyValueStore {
    void set(String key, String value);
    Optional<String> get(String key);
    boolean delete(String key);
    boolean exists(String key);
    int size();
}
