package com.redislite.command;

import com.redislite.protocol.CommandParser;
import com.redislite.protocol.ParseException;

/** Parses and dispatches requests without synchronization. */
public final class DefaultRequestProcessor implements RequestProcessor {
    private final CommandParser parser;
    private final CommandDispatcher dispatcher;

    public DefaultRequestProcessor(CommandDispatcher dispatcher) {
        this(new CommandParser(), dispatcher);
    }

    public DefaultRequestProcessor(CommandParser parser, CommandDispatcher dispatcher) {
        this.parser = parser;
        this.dispatcher = dispatcher;
    }

    @Override
    public Reply process(String line) {
        try {
            if (line == null || line.trim().isEmpty()) {
                return new Reply(null, false);
            }
            var command = parser.parse(line);
            return new Reply(dispatcher.execute(command), command.name().equalsIgnoreCase("QUIT"));
        } catch (ParseException exception) {
            return new Reply("ERR " + exception.getMessage(), false);
        } catch (RuntimeException exception) {
            return new Reply("ERR internal error", false);
        }
    }
}
