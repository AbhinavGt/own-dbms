package com.redislite;

import com.redislite.cli.Repl;
import com.redislite.command.CommandDispatcher;
import com.redislite.command.DefaultRequestProcessor;
import com.redislite.command.SynchronizedRequestProcessor;
import com.redislite.store.InMemoryStore;
import com.redislite.server.TcpServer;
import java.util.concurrent.CountDownLatch;

/** Application entry point for Redis-lite server and REPL modes. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int port = 6380;
        boolean repl = false;
        for (int i = 0; i < args.length; i++) {
            if ("--repl".equals(args[i])) repl = true;
            else if ("--port".equals(args[i]) && i + 1 < args.length) {
                try { port = Integer.parseInt(args[++i]); }
                catch (NumberFormatException e) { usage(); System.exit(2); return; }
                if (port < 0 || port > 65535) { usage(); System.exit(2); return; }
            } else { usage(); System.exit(2); return; }
        }
        var dispatcher = new CommandDispatcher(new InMemoryStore());
        var processor = new SynchronizedRequestProcessor(new DefaultRequestProcessor(dispatcher));
        if (repl) {
            new Repl(processor, System.in, System.out).run();
            return;
        }
        var server = new TcpServer(port, processor);
        var stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            stopped.countDown();
        }, "shutdown"));
        try {
            server.start();
            System.out.println("Listening on port " + server.getPort());
            try {
                stopped.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        } catch (java.io.IOException exception) {
            System.err.println("Unable to start server: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void usage() {
        System.err.println("Usage: java -jar redis-lite.jar [--port <0-65535>] [--repl]");
    }
}
