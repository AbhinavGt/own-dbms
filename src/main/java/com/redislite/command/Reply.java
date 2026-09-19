package com.redislite.command;

/** Result of processing one request line. */
public record Reply(String text, boolean close) {
}
