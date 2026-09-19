package com.redislite.command;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.redislite.protocol.CommandParser;

class CommandDispatcherTest {
    private final CommandParser parser = new CommandParser();
    private final CommandDispatcher dispatcher = new CommandDispatcher(new com.redislite.store.InMemoryStore());

    private String execute(String line) throws Exception {
        return dispatcher.execute(parser.parse(line));
    }

    @Test void commandSuccessPaths() throws Exception {
        assertEquals("OK", execute("SET a 1"));
        assertEquals("1", execute("GET a"));
        assertEquals("1", execute("EXISTS a"));
        assertEquals("1", execute("DEL a"));
        assertEquals("0", execute("EXISTS a"));
        assertEquals("PONG", execute("PING"));
        assertEquals("BYE", execute("QUIT"));
    }

    @Test void getMissingAndDeletedReturnsNil() throws Exception {
        assertEquals("(nil)", execute("GET missing"));
        execute("SET a value");
        execute("DEL a");
        assertEquals("(nil)", execute("GET a"));
    }

    @Test void setValuesWithSpacesRoundTrip() throws Exception {
        assertEquals("OK", execute("SET greeting hello big world"));
        assertEquals("hello big world", execute("GET greeting"));
    }

    @Test void wrongArgumentCountsReturnErrors() throws Exception {
        for (String command : new String[]{"SET", "SET a"}) {
            assertEquals("ERR wrong number of arguments for 'SET'", execute(command));
        }
        assertEquals("ERR wrong number of arguments for 'SET'",
                dispatcher.execute(new com.redislite.protocol.ParsedCommand(
                        "SET", java.util.List.of("a", "b", "c"))));
        for (String name : new String[]{"GET", "GET a b", "DEL", "DEL a b", "EXISTS", "EXISTS a b", "PING a"}) {
            String expectedName = name.split(" ")[0];
            assertEquals("ERR wrong number of arguments for '" + expectedName + "'", execute(name));
        }
        assertEquals("BYE", execute("QUIT extra arguments"));
    }

    @Test void unknownCommandReturnsError() throws Exception {
        assertEquals("ERR unknown command 'NOPE'", execute("nope"));
    }
}
