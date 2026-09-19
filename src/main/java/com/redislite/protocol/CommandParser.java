package com.redislite.protocol;

import java.util.ArrayList;
import java.util.Arrays;

/** Parses the simple line-oriented command format. */
public final class CommandParser {
    public ParsedCommand parse(String line) throws ParseException {
        if (line == null) {
            throw new ParseException("empty command");
        }
        String input = line.trim();
        if (input.isEmpty()) {
            throw new ParseException("empty command");
        }

        int firstWhitespace = firstWhitespace(input);
        String name = firstWhitespace < 0 ? input : input.substring(0, firstWhitespace);
        String remainder = firstWhitespace < 0 ? "" : input.substring(firstWhitespace).trim();
        if (name.equalsIgnoreCase("SET")) {
            if (remainder.isEmpty()) {
                return new ParsedCommand(name, ListSupport.empty());
            }
            int keyEnd = firstWhitespace(remainder);
            if (keyEnd < 0) {
                return new ParsedCommand(name, ListSupport.of(remainder));
            }
            String key = remainder.substring(0, keyEnd);
            String value = remainder.substring(keyEnd).trim();
            return value.isEmpty()
                    ? new ParsedCommand(name, ListSupport.of(key))
                    : new ParsedCommand(name, ListSupport.of(key, value));
        }

        return new ParsedCommand(name,
                remainder.isEmpty() ? ListSupport.empty()
                        : new ArrayList<>(Arrays.asList(remainder.split("\\s+"))));
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static final class ListSupport {
        private static java.util.List<String> empty() {
            return java.util.List.of();
        }

        private static java.util.List<String> of(String... values) {
            return java.util.List.of(values);
        }
    }
}
