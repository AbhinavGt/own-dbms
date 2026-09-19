# Redis-lite: Checkpoint 2 Implementation Plan (Java)

## Goal

Extend the working Checkpoint 1 project so **multiple clients can connect over TCP at the same time**, using the same commands (`SET`, `GET`, `DEL`, `EXISTS`, `PING`, `QUIT`) and the same newline-delimited text protocol.

Requirements:
- TCP server with **one thread per connection**
- **One global lock** around command execution (no fine-grained locking)
- Correct handling of **partial reads** and **client disconnects**
- Integration tests with real sockets, including a **concurrency test** with several clients hammering the server

## Before you start

Inspect the existing Checkpoint 1 code and reuse it. All Checkpoint 1 tests must still pass at every step. Tag the Checkpoint 1 state first if it is not tagged already: `git tag checkpoint-1`.

## Tech constraints

- Java 17 or newer, Maven, JUnit 5 (same as Checkpoint 1)
- **No new third-party dependencies**
- Use blocking I/O from `java.net` (`ServerSocket`, `Socket`). **Do not use NIO, `epoll`-style selectors, or Netty.**
- Explicit UTF-8 everywhere text is read or written
- Store implementation stays `HashMap`-backed. Do not add TTL, LRU, or persistence.

## Target structure

New or changed files are marked.

```
src/main/java/com/redislite/
├── Main.java                              // CHANGED: server mode by default, --repl for old behavior
├── cli/Repl.java                          // CHANGED: uses RequestProcessor
├── protocol/                              // unchanged
├── command/
│   ├── CommandDispatcher.java             // unchanged behavior
│   ├── CommandHandler.java                // unchanged
│   ├── Reply.java                         // NEW: record Reply(String text, boolean close)
│   ├── RequestProcessor.java              // NEW: interface, Reply process(String line)
│   ├── DefaultRequestProcessor.java       // NEW: parse + dispatch, no locking
│   └── SynchronizedRequestProcessor.java  // NEW: wraps another processor in a global lock
├── server/
│   ├── TcpServer.java                     // NEW: accept loop, lifecycle
│   └── ClientHandler.java                 // NEW: one per connection, Runnable
└── store/                                 // unchanged

src/test/java/com/redislite/
├── command/SynchronizedRequestProcessorTest.java   // NEW
└── server/
    ├── TcpServerTest.java                           // NEW: protocol and lifecycle tests
    └── TcpServerConcurrencyTest.java                // NEW: multi-client stress test
```

## Architecture rules

1. **Shared request path.** The REPL and the TCP server must both go through `RequestProcessor`. Parsing and dispatch logic must exist in exactly one place.
2. **The lock wraps the whole command, not individual store calls.** `SynchronizedRequestProcessor` acquires the lock, runs `process(line)`, and releases it. This keeps every command atomic, which matters in Checkpoint 3 when TTL and LRU will touch several structures in one command.
3. **Never hold the lock during network I/O.** Read from the socket, then take the lock only for `process(...)`, then release it, then write the reply. A slow client must never block other clients.
4. **The store, parser, and dispatcher stay lock-free and unaware of threads.** Thread-safety lives in one place only.
5. **Each connection owns its own reader, writer, and socket.** Nothing about a connection is shared between threads.
6. **A failure in one connection must never affect the server or other clients.**

## Component specifications

### `Reply`

```java
public record Reply(String text, boolean close) {
    // text == null means "send nothing" (e.g. blank input line)
}
```

### `RequestProcessor`

```java
public interface RequestProcessor {
    Reply process(String line);
}
```

### `DefaultRequestProcessor`

Takes the raw line and does what the REPL currently does between reading a line and printing a response:
- Blank or whitespace-only line: `new Reply(null, false)`
- Parse the line. On `ParseException`, return `ERR <message>`.
- Dispatch the command and return its response.
- `QUIT`: return `BYE` with `close = true`.
- It must **never throw** for any input string.

If the current REPL or dispatcher already contains this logic, extract it here and update `Repl` and its tests to use it. Behavior must not change.

### `SynchronizedRequestProcessor`

- Constructor takes a `RequestProcessor delegate`
- Holds one `ReentrantLock` (or uses `synchronized`, your choice, but be consistent)
- `process(line)`: acquire the lock, call the delegate inside `try`, release in `finally`
- Nothing else. Keep it tiny.

### `ClientHandler` (implements `Runnable`)

Constructor takes the `Socket`, the shared `RequestProcessor`, and a logger or a simple `Consumer<String>` for log messages.

`run()` behavior:
1. Wrap the socket streams: `BufferedReader` over `InputStreamReader(..., UTF_8)`, and a `BufferedWriter` over `OutputStreamWriter(..., UTF_8)`.
2. Loop: `line = reader.readLine()`.
   - `readLine()` returns `null` when the client closed the connection. Exit the loop cleanly.
   - `readLine()` blocks until a full line arrives, so **commands split across several TCP packets are reassembled correctly**. This is the partial-read requirement. Add a code comment explaining that TCP is a byte stream, not a message stream.
3. Call `processor.process(line)` (with the lock held inside the processor, not here).
4. If `reply.text() != null`, write `reply.text()` followed by an explicit `"\n"` (**not** `println`, which uses the platform line separator), then `flush()`.
5. If `reply.close()` is true, exit the loop.
6. Catch `IOException` (including `SocketException` for resets and disconnects). Log at low severity and exit the loop. This is expected behavior, not an error.
7. Catch `RuntimeException` around the process call: reply `ERR internal error`, log the stack trace, and **keep the connection open**.
8. In a `finally` block, always close the socket. Closing must never throw out of `run()`.

### `TcpServer`

Public API:
```java
public TcpServer(int port, RequestProcessor processor)   // port 0 = pick a free port
public void start() throws IOException                    // binds and starts the accept loop in a background thread, returns immediately
public int getPort()                                      // the actual bound port (needed by tests)
public void stop()                                        // stops accepting, closes all client sockets, shuts down threads; idempotent
```

Behavior:
- `start()` creates the `ServerSocket` (binding to `port`), then launches the accept loop on its own thread.
- Accept loop: `Socket s = serverSocket.accept()`, then hand a new `ClientHandler` to an executor. Use `Executors.newCachedThreadPool()` with a thread factory that names threads `client-<n>` and sets them as daemon threads. (This is effectively one thread per connection.)
- Track open client sockets in a `Set<Socket>` from `ConcurrentHashMap.newKeySet()` so `stop()` can close them. Remove a socket when its handler finishes.
- If `accept()` throws `IOException` while the server is **not** stopping, log it and continue accepting. If it throws because the server is stopping, exit the loop quietly.
- `stop()`: set a `running = false` flag, close the `ServerSocket` (this unblocks `accept()`), close all tracked client sockets, call `executor.shutdownNow()`, and wait briefly with `awaitTermination`.
- Restore the interrupt flag if you catch `InterruptedException`. Never swallow it silently.
- Set `serverSocket.setReuseAddress(true)` before binding so quick restarts do not fail.

### `Main`

- Default: start the TCP server on port **6380** (avoiding 6379 so it does not clash with a real Redis) and print `Listening on port 6380`.
- `--port <n>` overrides the port. Invalid values print a usage message and exit with a non-zero code.
- `--repl` runs the Checkpoint 1 interactive REPL, backed by the same `DefaultRequestProcessor`.
- Build the object graph in one place:
  `InMemoryStore -> CommandDispatcher -> DefaultRequestProcessor -> SynchronizedRequestProcessor -> TcpServer`
- Register a JVM shutdown hook that calls `server.stop()`.

## Protocol (unchanged from Checkpoint 1, restated for the network)

- Requests are lines terminated by `\n`. A trailing `\r` (from telnet or Windows clients) must be tolerated and ignored.
- Each request that produces output gets exactly one response line terminated by `\n`.
- No prompt (`> `) is sent over the network.
- Blank lines produce no response.
- `QUIT` replies `BYE`, then the server closes **that connection only**.

## Implementation order

Complete and commit each step before the next.

1. **Refactor:** introduce `Reply`, `RequestProcessor`, and `DefaultRequestProcessor`; switch `Repl` to use them. All Checkpoint 1 tests must pass unchanged in behavior. Commit.
2. **Global lock:** add `SynchronizedRequestProcessor` with its unit test. Commit.
3. **Server:** implement `ClientHandler` and `TcpServer`. Manually verify with `nc localhost 6380`. Commit.
4. **Main wiring:** server mode, `--port`, `--repl`, shutdown hook. Commit.
5. **Integration tests:** `TcpServerTest`. Commit.
6. **Concurrency test:** `TcpServerConcurrencyTest`. Commit.
7. **README and cleanup:** update the README, then `git tag checkpoint-2`.

## Required tests

All socket tests must use `new TcpServer(0, ...)` and read the real port from `getPort()`. **Never hard-code a port in tests.** Start the server in `@BeforeEach` and stop it in `@AfterEach`. Put `@Timeout(10)` (seconds) on every test so a hang fails fast instead of freezing the build.

### `SynchronizedRequestProcessorTest`
- Delegates correctly (a fake delegate records calls and returns a known reply)
- Releases the lock when the delegate throws (a second call afterwards must not deadlock)
- **Mutual exclusion:** several threads call `process` on a delegate that tracks "currently inside" with an `AtomicInteger`, and the maximum concurrent count never exceeds 1

### `TcpServerTest`
- **Basic round trip:** connect, send `SET a 1`, expect `OK`; send `GET a`, expect `1`
- **Full command set** works over TCP: `PING`, `EXISTS`, `DEL`, `GET` on a missing key returns `(nil)`
- **Partial reads:** send `SET k hello world\n` in pieces (for example three writes with a `flush()` and a short sleep between them, and also one test sending byte by byte). The reply must be `OK` and `GET k` must return `hello world`.
- **Multiple commands in one packet:** send `SET a 1\nGET a\nPING\n` in a single write and expect three response lines in order
- **`\r\n` line endings** are accepted
- **Client disconnects mid-command:** open a client, send `SET a` with no newline, close the socket abruptly. Then open a **second** client and verify the server still works, and the half-sent command did not modify the store.
- **Client disconnects without QUIT:** the server handler exits cleanly (no leaked thread). The server keeps serving others.
- **QUIT** returns `BYE` and the server closes that connection (the next `read` returns end of stream), while a second client stays connected and working
- **Blank line** gets no response, and the next real command gets the correct response (verify by sending a blank line then `PING`, and expecting exactly `PONG` as the next line)
- **Unknown command and bad arguments** return `ERR ...` without dropping the connection
- **Server stop:** after `stop()`, new connections are refused and existing client sockets are closed. Calling `stop()` twice does not throw.

### `TcpServerConcurrencyTest`
- **Disjoint keys:** start 10 client threads. Each opens its own socket and performs 1,000 iterations of `SET c<id>_k<i> v<id>_<i>` (expect `OK`) followed by `GET c<id>_k<i>` (expect exactly that value). Use a `CountDownLatch` so all clients start at the same moment. After all threads finish, assert:
  - no client saw an unexpected reply or exception
  - the store contains exactly 10 x 1,000 keys (read `store.size()` directly from the test, which holds a reference to the store)
- **Shared key contention:** 10 clients each perform 500 `SET shared <own-id>` and `GET shared` operations. Every `GET` must return a value that is one of the valid client ids (never garbled, never `(nil)` after the first SET has completed), and the server must not throw.
- **Connection churn:** 50 clients connect, run one command, and disconnect, in parallel waves. The server keeps working and the count of active client handlers returns to zero.
- Collect failures from worker threads (for example with `ExecutorService` and `Future.get()`, or a thread-safe error list). **Do not let an assertion failure inside a worker thread get silently lost.**

### Lock-necessity experiment (do this once, do not commit it)
Temporarily replace `SynchronizedRequestProcessor` with the plain `DefaultRequestProcessor` in the concurrency test setup and run the disjoint-keys test several times. It may lose entries, throw exceptions, or hang from concurrent `HashMap` modification. Record what you observe in the README under a "Concurrency notes" heading. Then restore the lock. This gives a concrete story for interviews: "I proved the lock was necessary by removing it."

## Common pitfalls (avoid all of these)

- Using `println` or `PrintWriter.println` (writes `\r\n` on Windows, platform-dependent)
- Forgetting `flush()` after writing a reply, so the client hangs waiting
- Holding the global lock while reading from or writing to the socket
- Not releasing the lock in a `finally` block
- Sharing one `BufferedReader` or `Socket` between threads
- Letting one `IOException` in the accept loop kill the server
- Not closing the client socket on every exit path
- Hard-coded ports in tests, which makes tests flaky and unable to run in parallel
- Catching `InterruptedException` and ignoring it
- Assertions inside worker threads that never propagate to the test thread
- Tests without timeouts

## Manual verification checklist

Run these against the built jar (`java -jar target/redis-lite-1.0-SNAPSHOT.jar`):

1. `nc localhost 6380`, then type `PING`, and expect `PONG`.
2. Open a **second** terminal with `nc localhost 6380`. Run `SET a 1` in one and `GET a` in the other. Both see the same data.
3. `printf 'SET x 1\nGET x\nQUIT\n' | nc localhost 6380` prints `OK`, `1`, `BYE`.
4. Kill a client with Ctrl+C mid-session. The server keeps running.
5. Ctrl+C the server. It shuts down cleanly, and restarting on the same port works immediately.
6. `java -jar target/redis-lite-1.0-SNAPSHOT.jar --repl` still gives the interactive prompt.
7. `java -jar target/redis-lite-1.0-SNAPSHOT.jar --port 7000` listens on 7000.

## Definition of done

- [ ] `mvn clean test` passes, including all Checkpoint 1 tests
- [ ] Server accepts many simultaneous clients and all commands behave as in Checkpoint 1
- [ ] Partial reads, multiple commands per packet, `\r\n`, and abrupt disconnects are all covered by passing tests
- [ ] Concurrency tests pass reliably: run `mvn test` 10 times in a row with no failures or hangs
- [ ] The global lock is only held during `process(...)`, never during socket I/O
- [ ] No leaked threads or sockets after `stop()`
- [ ] README updated: how to run server and REPL modes, the port option, protocol description, a "Concurrency notes" section describing the global lock design and its trade-offs (simple and correct, but serializes all commands and limits throughput; a sharded lock or an event loop are the next steps), and known limitations
- [ ] `git tag checkpoint-2`

## Explicitly out of scope (do not implement)

- TTL, `EXPIRE`, LRU eviction, or a max key count (Checkpoint 3)
- AOF or any persistence (Checkpoint 3)
- Benchmarks (Checkpoint 3)
- NIO, selectors, or an event loop
- Thread pool tuning, connection limits, or idle timeouts
- Fine-grained, striped, or read-write locking
- RESP protocol, authentication, TLS
- Additional data types

## Optional hardening (only if everything above is done and green)

- Maximum line length (for example 64 KB): a client that sends endless bytes without a newline must not exhaust server memory. Reply `ERR line too long` and close that connection. This requires replacing `readLine()` with a small bounded reader.
- Idle timeout with `socket.setSoTimeout(...)` to drop dead clients.
- A simple counter of active connections, exposed for the tests.

## Style guidance

- Small classes with one responsibility each, constructor injection, no static mutable state
- Brief Javadoc on public types, and short comments only where the reason is non-obvious (partial reads, lock scope, socket cleanup)
- Log with `java.util.logging` or a tiny logger wrapper. No logging library.
- If a requirement is ambiguous, choose the simplest reasonable behavior and document the decision in the README
