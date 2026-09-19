package com.redislite;

import com.redislite.cli.Repl;
import com.redislite.command.CommandDispatcher;
import com.redislite.store.InMemoryStore;

/** Application entry point for the Redis-lite command line interface. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        var dispatcher = new CommandDispatcher(new InMemoryStore());
        new Repl(dispatcher, System.in, System.out).run();
    }
}
