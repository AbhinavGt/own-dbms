package com.redislite;

import java.util.concurrent.CountDownLatch;

/** Application entry point for Redis-lite server and REPL modes. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        final Config config;
        try { config=Config.parse(args); } catch(IllegalArgumentException e){usage();System.exit(2);return;}
        final App app;
        try { app=new App(config); } catch(RuntimeException e){System.err.println("Unable to start server: "+e.getMessage());System.exit(1);return;}
        var stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
            stopped.countDown();
        }, "shutdown"));
        try {
            app.start();
            if(!config.repl()) {
                System.out.println("AOF replay: " + app.replayResult().recordsApplied() + " records in " + app.replayResult().elapsedMillis() + " ms");
                System.out.println("Listening on port " + app.port());
            }
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
        System.err.println("Usage: java -jar redis-lite.jar [--port <0-65535>] [--repl] [--max-keys n] [--no-aof] [--aof-file path] [--aof-fsync always|everysec|no] [--sweep-interval-ms n]");
    }
}
