package com.redislite.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.redislite.store.ExpiringLruStore;
import com.redislite.time.TimeSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AofPersistenceTest {
    @TempDir Path directory;

    @Test
    void writesAndReplaysMutationRecords() throws Exception {
        Path file = directory.resolve("appendonly.aof");
        try (var writer = new AofWriter(file, FsyncPolicy.NO)) {
            writer.appendSet("greeting", "hello world");
            writer.appendExpireAt("greeting", 10_000);
        }
        var store = new ExpiringLruStore((TimeSource) () -> 1_000);
        var result = new AofLoader().replay(file, store);
        assertEquals(2, result.recordsApplied());
        assertEquals("hello world", store.get("greeting").orElseThrow());
        assertEquals(9_000, store.ttlMillis("greeting"));
    }

    @Test
    void truncatesTornTailBeforeNextAppend() throws Exception {
        Path file = directory.resolve("appendonly.aof");
        Files.writeString(file, "SET complete value\nSET partial value", StandardCharsets.UTF_8);
        var store = new ExpiringLruStore((TimeSource) () -> 0);
        var result = new AofLoader().replay(file, store);
        assertEquals(1, result.recordsApplied());
        assertTrue(result.tornBytesTruncated() > 0);
        assertTrue(store.exists("complete"));
        assertFalse(store.exists("partial"));
        try (var writer = new AofWriter(file, FsyncPolicy.NO)) {
            writer.appendSet("next", "value");
        }
        assertTrue(Files.readString(file).endsWith("SET next value\n"));
    }
}
