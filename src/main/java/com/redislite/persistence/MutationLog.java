package com.redislite.persistence;
public interface MutationLog extends AutoCloseable {
    void appendSet(String key,String value); void appendDelete(String key); void appendExpireAt(String key,long epochMillis);
    boolean isHealthy(); void close();
}
