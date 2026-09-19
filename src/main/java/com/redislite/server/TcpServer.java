package com.redislite.server;

import com.redislite.command.RequestProcessor;
import java.io.IOException;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/** Blocking TCP server for the line-oriented protocol. */
public final class TcpServer {
    private static final Logger LOG = Logger.getLogger(TcpServer.class.getName());
    private final int requestedPort;
    private final RequestProcessor processor;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final AtomicLong clientNumber = new AtomicLong();
    private final ExecutorService executor;
    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private Thread acceptThread;

    public TcpServer(int port, RequestProcessor processor) {
        this.requestedPort = port;
        this.processor = processor;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "client-" + clientNumber.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() throws IOException {
        if (running) return;
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(requestedPort));
        serverSocket = server;
        running = true;
        acceptThread = new Thread(this::acceptLoop, "acceptor");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int getPort() {
        ServerSocket server = serverSocket;
        return server == null ? -1 : server.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                clients.add(socket);
                executor.execute(new ClientHandler(socket, processor, message -> LOG.fine(message), clients::remove));
            } catch (IOException exception) {
                if (running) LOG.warning("Accept failed: " + exception.getMessage());
            } catch (RuntimeException exception) {
                if (running) LOG.warning("Client setup failed: " + exception);
            }
        }
    }

    public synchronized void stop() {
        running = false;
        ServerSocket server = serverSocket;
        if (server != null) try { server.close(); } catch (IOException ignored) { }
        for (Socket socket : clients) try { socket.close(); } catch (IOException ignored) { }
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        if (acceptThread != null && acceptThread != Thread.currentThread()) {
            try { acceptThread.join(2000); } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        clients.clear();
    }
}
