package com.redislite.command;

import java.util.List;

/** Handles one command's argument list and returns its response. */
@FunctionalInterface
public interface CommandHandler {
    String handle(List<String> args);
}
