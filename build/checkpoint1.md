# Redis-lite: Checkpoint 1 Implementation Plan (Java)

## Goal

Build a **single-threaded, in-memory key-value store** in Java that can be used from the command line. The user types commands into a terminal and gets correct replies.

This is Checkpoint 1 of a larger project. Later checkpoints will add TCP networking, concurrency, TTL, LRU eviction, and persistence. **Design so those can be added without rewriting Checkpoint 1**, but do NOT implement any of them now.

## Tech constraints

- Java 17 or newer
- Build tool: Maven (single module)
- Tests: JUnit 5 only
- **No other third-party dependencies**
- No threads, no networking, no file persistence in this checkpoint
- Use `java.util.HashMap` for storage (a custom hash table comes later)

## Project structure

```
redis-lite/
├── pom.xml
├── README.md
└── src/
    ├── main/java/com/redislite/
    │   ├── Main.java                  // entry point, starts the CLI
    │   ├── cli/
    │   │   └── Repl.java              // read-eval-print loop over stdin/stdout
    │   ├── protocol/
    │   │   ├── CommandParser.java     // raw line -> ParsedCommand
    │   │   ├── ParsedCommand.java     // record: name + args
    │   │   └── ParseException.java    // thrown for malformed input
    │   ├── command/
    │   │   ├── CommandDispatcher.java // ParsedCommand -> response String
    │   │   └── CommandHandler.java    // functional interface, one per command
    │   └── store/
    │       ├── KeyValueStore.java     // interface
    │       └── InMemoryStore.java     // HashMap-backed implementation
    └── test/java/com/redislite/
        ├── protocol/CommandParserTest.java
        ├── store/InMemoryStoreTest.java
        └── command/CommandDispatcherTest.java
```

## Architecture rules

1. **Layers only depend downward:** `Repl -> CommandDispatcher -> KeyValueStore`. The parser is used by the dispatcher or the REPL, and nothing below it knows about text.
2. **The store knows nothing about strings on the wire.** It exposes typed methods only.
3. **The dispatcher returns a `String` response** and never prints. Only `Repl` does I/O. This lets a future TCP server reuse the dispatcher unchanged.
4. **`KeyValueStore` is an interface** so later checkpoints can swap in a custom hash table or add wrappers (LRU, TTL, locking).
5. Handlers are registered in a `Map<String, CommandHandler>` inside the dispatcher, so adding a command means adding one entry.

## Component specifications

### `KeyValueStore` (interface)

```java
public interface KeyValueStore {
    void set(String key, String value);
    Optional<String> get(String key);
    boolean delete(String key);   // true if the key existed
    boolean exists(String key);
    int size();                   // number of keys
}
```

### `InMemoryStore`

- Backed by a `HashMap<String, String>`
- `null` keys or values must throw `IllegalArgumentException`
- `set` overwrites any existing value
- `delete` returns `true` only if the key was present

### `ParsedCommand`

A Java `record` with:
- `String name` (normalized to **uppercase**)
- `List<String> args`

### `CommandParser`

Input is one line of text, and output is a `ParsedCommand`.

Rules:
- Trim leading and trailing whitespace
- The first whitespace-delimited token is the command name, case-insensitive (`set`, `SET`, and `Set` are equivalent)
- For `SET`, the **key is the second token and the value is the entire rest of the line** after the key, preserving inner spaces. Example: `SET greeting hello big world` sets `greeting` to `hello big world`.
- For all other commands, arguments are whitespace-separated tokens
- Blank or whitespace-only input throws `ParseException("empty command")`
- Keys cannot contain whitespace (a consequence of the format, so document it in the README)

### `CommandDispatcher`

`String execute(ParsedCommand cmd)` returns exactly one response string (no trailing newline; the REPL adds it).

| Command | Syntax | Success response | Errors |
|---|---|---|---|
| `SET` | `SET key value` | `OK` | `ERR wrong number of arguments for 'SET'` if key or value is missing |
| `GET` | `GET key` | the value, or `(nil)` if the key does not exist | wrong-arg-count error unless exactly 1 arg |
| `DEL` | `DEL key` | `1` if deleted, `0` if the key did not exist | wrong-arg-count error unless exactly 1 arg |
| `EXISTS` | `EXISTS key` | `1` if present, `0` if not | wrong-arg-count error unless exactly 1 arg |
| `PING` | `PING` | `PONG` | wrong-arg-count error if any args given |
| `QUIT` | `QUIT` | `BYE` (the REPL then exits) | none |

Error format: always start with `ERR ` followed by a message. Unknown commands return `ERR unknown command 'FOO'`. `ParseException` is caught and returned as `ERR <message>`. The dispatcher must **never throw** for bad user input.

### `Repl`

- Reads lines from an `InputStream` and writes to an `OutputStream` (inject both, don't hard-code `System.in` and `System.out`, so it can be tested)
- Prints a prompt such as `> ` before each read
- Passes each line through the parser, then the dispatcher, then prints the response
- Exits on `QUIT` or end of input (Ctrl+D)
- Ignores blank lines without printing an error

### `Main`

Create an `InMemoryStore`, a `CommandDispatcher`, and a `Repl`, then run it.

## Implementation order

Complete and test each step before moving on. Commit after each step.

1. **Project setup:** `pom.xml` with Java 17, JUnit 5, and the Surefire plugin. Verify `mvn test` runs.
2. **Store:** `KeyValueStore` + `InMemoryStore` + `InMemoryStoreTest`.
3. **Parser:** `ParsedCommand`, `ParseException`, `CommandParser` + `CommandParserTest`.
4. **Dispatcher:** `CommandHandler`, `CommandDispatcher` + `CommandDispatcherTest`.
5. **REPL and Main:** wire everything together. Add one end-to-end test that feeds a multi-line script into `Repl` and asserts the full output.
6. **README:** build and run instructions, command reference, and a "Known limitations" section.

## Required tests

**Store**
- set then get returns the value
- get on a missing key returns empty
- set overwrites an existing key
- delete returns true for an existing key and false for a missing one
- exists is true after set and false after delete
- size tracks inserts and deletes
- null key or value throws `IllegalArgumentException`

**Parser**
- normal command with args
- lowercase and mixed-case command names are normalized
- extra leading, trailing, and repeated whitespace
- `SET` with a multi-word value preserves inner spaces
- blank line throws `ParseException`

**Dispatcher**
- each command's success path
- each command with too few and too many arguments
- unknown command
- `GET` after `DEL` returns `(nil)`
- values containing spaces round-trip through `SET` and `GET`

**End-to-end REPL**
- a script such as `SET a 1`, `GET a`, `EXISTS a`, `DEL a`, `GET a`, `QUIT` produces the expected output sequence
- input that ends without `QUIT` exits cleanly

## Definition of done

- [ ] `mvn clean test` passes with no failures
- [ ] `mvn package` produces a runnable build, and running it gives an interactive prompt
- [ ] All six commands behave exactly as in the table above
- [ ] No command can crash the REPL, whatever the input
- [ ] Layers are separated as described, with no `System.out` outside `Repl` and `Main`
- [ ] README documents how to build, run, and use each command, plus known limitations

## Explicitly out of scope (do not implement)

- TCP server or any networking
- Threads, locks, or thread safety
- TTL, `EXPIRE`, or any time-based behavior
- LRU eviction or a max key count
- File persistence or AOF
- RESP protocol
- Custom hash table implementation
- Additional data types (lists, sets, hashes, sorted sets)

## Style guidance

- Small classes, one responsibility each
- Constructor injection for dependencies (dispatcher takes a `KeyValueStore`, REPL takes the dispatcher and streams)
- Meaningful names, brief Javadoc on public types and methods
- No premature abstraction beyond the interfaces listed above
- If any requirement is ambiguous, choose the simplest reasonable behavior and note the decision in the README
