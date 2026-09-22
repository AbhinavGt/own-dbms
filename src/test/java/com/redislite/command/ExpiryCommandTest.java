package com.redislite.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redislite.TestTimeSource;
import com.redislite.store.ExpiringLruStore;
import org.junit.jupiter.api.Test;

class ExpiryCommandTest {
    @Test
    void supportsExpireTtlAndDbsize() {
        var time = new TestTimeSource(1_000);
        var store = new ExpiringLruStore(time);
        var dispatcher = new CommandDispatcher(store, time);
        var parser = new com.redislite.protocol.CommandParser();
        java.util.function.Function<String, String> execute = line -> {
            try {
                return dispatcher.execute(parser.parse(line));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        };
        assertEquals("OK", execute.apply("SET key value"));
        assertEquals("1", execute.apply("EXPIRE key 2"));
        assertEquals("2", execute.apply("TTL key"));
        time.advance(1);
        assertEquals("2", execute.apply("TTL key"));
        assertEquals("1", execute.apply("DBSIZE"));
        assertEquals("ERR invalid expire time", execute.apply("EXPIRE key 0"));
    }
}
