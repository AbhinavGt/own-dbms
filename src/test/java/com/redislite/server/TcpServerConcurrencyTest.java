package com.redislite.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redislite.command.CommandDispatcher;
import com.redislite.command.DefaultRequestProcessor;
import com.redislite.command.SynchronizedRequestProcessor;
import com.redislite.store.InMemoryStore;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class TcpServerConcurrencyTest {
    private InMemoryStore store;
    private TcpServer server;

    @BeforeEach
    void setUp() throws Exception {
        store = new InMemoryStore();
        var processor = new SynchronizedRequestProcessor(
                new DefaultRequestProcessor(new CommandDispatcher(store)));
        server = new TcpServer(0, processor);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    @Timeout(10)
    void clientsCanWriteDisjointKeysConcurrently() throws Exception {
        int clients = 10;
        int iterations = 100;
        var ready = new CountDownLatch(clients);
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(clients);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        try {
            for (int client = 0; client < clients; client++) {
                int id = client;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try (Socket socket = new Socket("127.0.0.1", server.getPort());
                         var reader = new BufferedReader(new InputStreamReader(
                                 socket.getInputStream(), StandardCharsets.UTF_8));
                         var writer = new BufferedWriter(new OutputStreamWriter(
                                 socket.getOutputStream(), StandardCharsets.UTF_8))) {
                        for (int i = 0; i < iterations; i++) {
                            String key = "c" + id + "_k" + i;
                            writer.write("SET " + key + " v" + id + "_" + i + "\n");
                            writer.flush();
                            assertEquals("OK", reader.readLine());
                            writer.write("GET " + key + "\n");
                            writer.flush();
                            assertEquals("v" + id + "_" + i, reader.readLine());
                        }
                    }
                    return null;
                }));
            }
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("clients did not become ready");
            }
            start.countDown();
            for (var future : futures) {
                future.get(8, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(clients * iterations, store.size());
    }
}
