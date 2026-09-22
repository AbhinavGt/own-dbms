package com.redislite.store;

final class Entry {
    static final long NO_EXPIRY = -1;
    final String key;
    String value;
    long expireAtMillis = NO_EXPIRY;
    Entry prev, next;
    Entry(String key, String value) { this.key = key; this.value = value; }
}
