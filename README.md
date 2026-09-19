# own-dbms

It is a small, single-threaded, in-memory key-value store with a line-oriented command-line interface.

## Build and run

Requires Java 17 and Maven:

```sh
mvn clean test
mvn package
java -cp target/classes com.redislite.Main
```

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

## Known limitations

This checkpoint has no networking, concurrency, expiration, eviction,
persistence, RESP protocol, or data types beyond string keys and values.
