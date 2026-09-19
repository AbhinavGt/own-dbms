package com.redislite.protocol;

import java.util.List;
import java.util.Locale;

/** A parsed command name and its arguments. */
public record ParsedCommand(String name, List<String> args) {
    public ParsedCommand {
        if (name == null || args == null) {
            throw new IllegalArgumentException("command name and args must not be null");
        }
        name = name.toUpperCase(Locale.ROOT);
        args = List.copyOf(args);
    }
}
