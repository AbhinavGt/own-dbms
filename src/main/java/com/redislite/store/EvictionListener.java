package com.redislite.store;

@FunctionalInterface
public interface EvictionListener { void onEvict(String key); }
