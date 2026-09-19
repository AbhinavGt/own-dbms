package com.redislite.server;

import com.redislite.command.Reply;
import com.redislite.command.RequestProcessor;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Handles one client connection. */
public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final RequestProcessor processor;
    private final Consumer<String> logger;
    private final Consumer<Socket> finished;

    public ClientHandler(Socket socket, RequestProcessor processor, Consumer<String> logger) {
        this(socket, processor, logger, ignored -> {});
    }

    ClientHandler(Socket socket, RequestProcessor processor, Consumer<String> logger,
                   Consumer<Socket> finished) {
        this.socket = socket;
        this.processor = processor;
        this.logger = logger;
        this.finished = finished;
    }

    @Override
    public void run() {
        try (socket;
             var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // TCP is a byte stream, not a message stream; readLine reassembles partial packets.
                Reply reply;
                try {
                    reply = processor.process(line);
                } catch (RuntimeException exception) {
                    logger.accept("Request processing failed: " + exception);
                    exception.printStackTrace();
                    reply = new Reply("ERR internal error", false);
                }
                if (reply.text() != null) {
                    writer.write(reply.text());
                    writer.write("\n");
                    writer.flush();
                }
                if (reply.close()) break;
            }
        } catch (IOException exception) {
            logger.accept("Client disconnected: " + exception.getMessage());
        } finally {
            try {
                finished.accept(socket);
            } catch (RuntimeException ignored) {
                // Cleanup callbacks must not escape the handler.
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
