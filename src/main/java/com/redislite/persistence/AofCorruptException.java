package com.redislite.persistence;
public class AofCorruptException extends RuntimeException {
    public AofCorruptException(String message) { super(message); }
    public AofCorruptException(String message, Throwable cause) { super(message,cause); }
}
