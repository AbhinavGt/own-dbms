# Redis-lite

Redis-lite is a small in-memory key-value store with a newline-delimited UTF-8
protocol and a blocking TCP server. The store remains HashMap-backed; command
execution is serialized by one global lock.

## Build and run

Requires Java 17 and Maven:

```sh
mvn clean test
mvn package
java -jar target/redis-lite-1.0-SNAPSHOT.jar
```

The server listens on port 6380 by default (6379 is intentionally avoided).
Use `java -jar target/redis-lite-1.0-SNAPSHOT.jar --port 7000` to select a
port, or `--repl` for the interactive Checkpoint 1 prompt.

## Commands

* `SET key value` stores a value and returns `OK`. The value is the rest of
  the line, so it may contain spaces.
* `GET key` returns the value or `(nil)`.
* `DEL key` returns `1` when a key was deleted, otherwise `0`.
* `EXISTS key` returns `1` or `0`.
* `PING` returns `PONG`.
* `QUIT` returns `BYE` and exits.

Commands are case-insensitive. Keys cannot contain whitespace. Invalid
argument counts return an `ERR` response; blank input is ignored.

## Network protocol

Clients connect with a normal TCP client such as `nc localhost 6380`. Requests
are UTF-8 lines terminated by `\n`; `\r\n` is also accepted. Every non-blank
request that produces output receives exactly one response line. `QUIT`
returns `BYE` and closes only that connection. Commands may be split across
TCP packets or combined in one packet.

## Concurrency notes

Each client has a daemon handler thread, while a `SynchronizedRequestProcessor`
holds one lock for the complete parse-and-dispatch operation. Socket I/O is
outside the lock, so slow clients do not prevent other clients from reading or
writing. The design is simple and correct, but serializes all commands and
limits throughput; a sharded lock or event loop would be natural next steps.
Removing the lock in a local experiment makes the HashMap-backed store unsafe:
repeated concurrent runs can lose entries or fail during concurrent mutation.

## Why the global lock exists.
To verify the lock is necessary, I temporarily ran the server without SynchronizedRequestProcessor.
In N of M runs of the concurrency test, ......
With the lock, the same test passed in M of M runs.
The trade-off is that the lock serializes all commands, so throughput doesn't scale with cores.
Sharded locks or a single-threaded event loop (as Redis uses) would be the next step.
## Known limitations

There is no TTL, eviction, persistence, RESP protocol, authentication, TLS,
connection limit, idle timeout, or data types beyond string keys and values.
