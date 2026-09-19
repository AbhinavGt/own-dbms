package com.redislite.protocol;

/** Indicates that a command line cannot be parsed. */
public class ParseException extends Exception {
    public ParseException(String message) {
        super(message);
    }
}
