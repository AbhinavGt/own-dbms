package com.redislite.command;

/** Processes one line of the text protocol. */
public interface RequestProcessor {
    Reply process(String line);
}
