package com.redislite.protocol;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CommandParserTest {
    private final CommandParser parser = new CommandParser();

    @Test void parsesCommandWithArgs() throws Exception {
        assertEquals(new ParsedCommand("GET", java.util.List.of("key")), parser.parse("GET key"));
    }

    @Test void normalizesCommandName() throws Exception {
        assertEquals("SET", parser.parse("sEt key value").name());
    }

    @Test void ignoresOuterAndRepeatedWhitespace() throws Exception {
        assertEquals(java.util.List.of("a", "b"), parser.parse("  ping   a   b  ").args());
    }

    @Test void preservesInnerSpacesInSetValue() throws Exception {
        var command = parser.parse(" SET greeting   hello  big world ");
        assertEquals("SET", command.name());
        assertEquals(java.util.List.of("greeting", "hello  big world"), command.args());
    }

    @Test void blankInputFails() {
        assertThrows(ParseException.class, () -> parser.parse(" \t "));
    }

    @Test void setMissingKeyOrValueProducesMissingArgs() throws Exception {
        assertEquals(0, parser.parse("SET").args().size());
        assertEquals(java.util.List.of("key"), parser.parse("SET key").args());
    }
}
