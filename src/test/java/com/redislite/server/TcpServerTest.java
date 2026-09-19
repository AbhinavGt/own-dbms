package com.redislite.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class TcpServerTest {
    private TcpServer server;

    @BeforeEach
    void setUp() throws Exception {
        var store = new InMemoryStore();
        var dispatcher = new CommandDispatcher(store);
        var processor = new SynchronizedRequestProcessor(new DefaultRequestProcessor(dispatcher));
        server = new TcpServer(0, processor);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    @Timeout(10)
    void supportsRoundTripAndMultipleCommands() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.getPort());
             var reader = reader(socket);
             var writer = writer(socket)) {
            writer.write("SET a 1\nGET a\nPING\n");
            writer.flush();
            assertEquals("OK", reader.readLine());
            assertEquals("1", reader.readLine());
            assertEquals("PONG", reader.readLine());
        }
    }

    @Test
    @Timeout(10)
    void supportsPartialWritesAndCrLf() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.getPort());
             var reader = reader(socket);
             var writer = writer(socket)) {
            writer.write("SET k ");
            writer.flush();
            writer.write("hello ");
            writer.flush();
            writer.write("world\r\n");
            writer.flush();
            assertEquals("OK", reader.readLine());
            writer.write("GET k\n");
            writer.flush();
            assertEquals("hello world", reader.readLine());
        }
    }

    @Test
    @Timeout(10)
    void quitClosesOnlyItsConnection() throws Exception {
        try (Socket first = new Socket("127.0.0.1", server.getPort());
             var firstReader = reader(first);
             var firstWriter = writer(first);
             Socket second = new Socket("127.0.0.1", server.getPort());
             var secondReader = reader(second);
             var secondWriter = writer(second)) {
            firstWriter.write("QUIT\n");
            firstWriter.flush();
            assertEquals("BYE", firstReader.readLine());
            assertNull(firstReader.readLine());

            secondWriter.write("PING\n");
            secondWriter.flush();
            assertEquals("PONG", secondReader.readLine());
        }
    }

    private static BufferedReader reader(Socket socket) throws Exception {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private static BufferedWriter writer(Socket socket) throws Exception {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }
}
