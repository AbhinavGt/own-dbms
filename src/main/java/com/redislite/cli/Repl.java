package com.redislite.cli;

import com.redislite.command.CommandDispatcher;
import com.redislite.protocol.CommandParser;
import com.redislite.protocol.ParseException;
import com.redislite.protocol.ParsedCommand;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Runs the line-oriented command prompt over injected streams. */
public final class Repl {
    private final CommandDispatcher dispatcher;
    private final InputStream input;
    private final OutputStream output;
    private final CommandParser parser = new CommandParser();

    public Repl(CommandDispatcher dispatcher, InputStream input, OutputStream output) {
        this.dispatcher = dispatcher;
        this.input = input;
        this.output = output;
    }

    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(output, true, StandardCharsets.UTF_8)) {
            String line;
            while (true) {
                writer.print("> ");
                writer.flush();
                line = reader.readLine();
                if (line == null) break;
                if (line.trim().isEmpty()) continue;
                try {
                    ParsedCommand command = parser.parse(line);
                    writer.println(dispatcher.execute(command));
                    if (command.name().equals("QUIT")) break;
                } catch (ParseException exception) {
                    writer.println("ERR " + exception.getMessage());
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("I/O error in REPL", exception);
        }
    }
}
