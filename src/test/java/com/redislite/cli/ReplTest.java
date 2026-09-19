package com.redislite.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.redislite.command.CommandDispatcher;
import com.redislite.store.InMemoryStore;

class ReplTest {
    @Test void runsScriptAndStopsAtQuit() {
        String script = "SET a 1\nGET a\nEXISTS a\nDEL a\nGET a\nQUIT\n";
        var output = new ByteArrayOutputStream();
        new Repl(new CommandDispatcher(new InMemoryStore()),
                new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)), output).run();
        assertEquals("> OK\n> 1\n> 1\n> 1\n> (nil)\n> BYE\n", output.toString(StandardCharsets.UTF_8));
    }

    @Test void endOfInputExitsCleanly() {
        var output = new ByteArrayOutputStream();
        new Repl(new CommandDispatcher(new InMemoryStore()),
                new ByteArrayInputStream("PING\n".getBytes(StandardCharsets.UTF_8)), output).run();
        assertEquals("> PONG\n> ", output.toString(StandardCharsets.UTF_8));
    }

    @Test void blankLinesAreIgnored() {
        var output = new ByteArrayOutputStream();
        new Repl(new CommandDispatcher(new InMemoryStore()),
                new ByteArrayInputStream("\n  \nPING\n".getBytes(StandardCharsets.UTF_8)), output).run();
        assertEquals("> > > PONG\n> ", output.toString(StandardCharsets.UTF_8));
    }
}
