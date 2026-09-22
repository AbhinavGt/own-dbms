# Features

Redis-lite is a Java 17 Maven project that currently supports:

• TCP clients
• Interactive REPL mode
• String key/value storage
• TTL expiration
• Active expiration sweeper
• LRU eviction
• AOF persistence
• Configurable fsync policies
• Concurrent client connections
• Stress and recovery benchmarks

# The complete request path is:

Client / REPL
      |
      v
TcpServer or Repl
      |
      v
ClientHandler
      |
      v
SynchronizedRequestProcessor
      |
      v
DefaultRequestProcessor
      |
      v
CommandParser
      |
      v
CommandDispatcher
      |
      v
PersistentStore
      |
      v
ExpiringLruStore
      |
      +--> HashMap
      +--> LRU doubly linked list
      +--> Expiry min-heap

Persistence runs alongside the store:

PersistentStore
      |
      +--> ExpiringLruStore
      |
      +--> AofWriter
                   |
                   v
             appendonly.aof

At startup, the AOF is replayed before the server accepts requests.

────────────────────

# Project structure

redis-lite/
├── pom.xml
├── README.md
├── checkpoint1.md
├── checkpoint2.md
├── checkpoint3.md
├── docs/
│   ├── BENCHMARKS.md
│   └── FINAL_REPORT.md
├── scripts/
│   └── run-fsync-benchmarks.sh
├── src/
│   ├── main/
│   │   └── java/com/redislite/
│   └── test/
│       └── java/com/redislite/
└── target/

 src  contains source code and tests.  target  contains generated build output and should not be edited manually.

────────────────────

# Root files

## pom.xml 

Maven project configuration.

It defines:

• Group ID:  com.redislite 
• Artifact ID:  redis-lite 
• Java release: Java 17
• JUnit 5 dependency
• Maven Surefire Plugin for tests
• Maven JAR Plugin for packaging
• Main class:  com.redislite.Main 

The project does not use third-party runtime libraries.

────────────────────

## README.md 

Documents:

• Project features
• Build and run commands
• Supported commands
• Configuration flags
• Architecture
• TTL and LRU behavior
• AOF persistence
• Concurrency design
• Limitations
• Future work

────────────────────

# docs 

## docs/BENCHMARKS.md 

Contains benchmark methodology information.

It intentionally does not contain fabricated performance numbers. Benchmark results should be recorded only after running them on a real system and real disk.

## docs/FINAL_REPORT.md 

Contains the stress-test results for this project.

It includes:

• Test environment
• Maven build result
• Throughput measurements
• Latency percentiles
• TTL test results
• LRU eviction results
• AOF persistence and recovery results
• Replay performance
• Limitations of the benchmark methodology

Measured examples from the report include:

Single-client GET:              9,299 ops/s
Mixed workload, 8 clients:     43,095 ops/s
Mixed workload, 32 clients:   102,813 ops/s
AOF always fsync:               6,868 ops/s
AOF replay:                     50,000 records in 70 ms

These are short development-machine measurements, not production guarantees.

────────────────────

# scripts 

 scripts/run-fsync-benchmarks.sh 

Script intended to compare:

• No AOF
• AOF with fsync  no 
• AOF with fsync  everysec 
• AOF with fsync  always 

It is used with  BenchmarkClient .

The benchmark should be run on a real disk rather than a RAM-backed temporary filesystem.

────────────────────

# Main application package

Location:

src/main/java/com/redislite/

## Main.java 

Application entry point.

It:

1. Parses command-line arguments using  Config .
2. Creates the  App .
3. Registers a JVM shutdown hook.
4. Starts TCP server mode or REPL mode.
5. Prints startup information.
6. Stops the application cleanly when the JVM shuts down.

Default mode is TCP server mode.

Default port:

6380

Typical startup output:

AOF replay: 0 records in 0 ms
Listening on port 6380

The object creation and lifecycle management are delegated to  App .

────────────────────

## App.java 

Central application wiring and lifecycle class.

It creates:

TimeSource
ExpiringLruStore
AofLoader
AofWriter
PersistentStore
CommandDispatcher
DefaultRequestProcessor
SynchronizedRequestProcessor
ExpirySweeper
TcpServer or Repl

Startup sequence:

1. Create raw ExpiringLruStore
2. Replay appendonly.aof
3. Open AofWriter
4. Configure eviction logging
5. Apply max-keys limit
6. Create PersistentStore
7. Create command dispatcher
8. Create synchronized processor
9. Start expiry sweeper
10. Start TCP server or REPL

Shutdown sequence:

1. Stop accepting clients
2. Stop expiry sweeper
3. Flush/close AOF writer
4. Finish application shutdown

This ensures TCP mode and REPL mode use the same store and persistence stack.

────────────────────

# Config.java 

Parses and stores command-line configuration.

Configuration fields include:

port
repl
maxKeys
noAof
aofFile
aofFsync
sweepIntervalMillis

Supported options:

--port <number>
--repl
--max-keys <number>
--no-aof
--aof-file <path>
--aof-fsync <always|everysec|no>
--sweep-interval-ms <number>

Invalid flags or values cause a usage message and non-zero exit status.

────────────────────

# cli  package

Location:

src/main/java/com/redislite/cli/

 Repl.java 

The interactive command-line interface.

It:

1. Prints  >  
2. Reads one line
3. Sends it to  RequestProcessor 
4. Prints the response
5. Stops on  QUIT  or end-of-input

Example:

> SET name Redis
> GET name
> Redis
> QUIT
> BYE

The REPL uses the same command-processing pipeline as TCP clients. This prevents the REPL and network server from having different behavior.

────────────────────

# protocol  package

Location:

src/main/java/com/redislite/protocol/

 CommandParser.java 

Converts a raw text line into a  ParsedCommand .

Example:

SET greeting hello large world

becomes:

name = SET
args = ["greeting", "hello large world"]

Special behavior:

• Command names are case-insensitive.
• Command names are normalized to uppercase.
•  SET  preserves spaces inside the value.
• Other commands split arguments by whitespace.
• Blank input causes a  ParseException .

 ParsedCommand.java 

Java record containing:

String name
List<String> args

The command name is normalized to uppercase.

Examples:

get name
Get name
GET name

all become:

name = GET

 ParseException.java 

Exception used when a command cannot be parsed.

For example:

empty command

The request processor converts parsing errors into protocol responses beginning with:

ERR

────────────────────

# command  package

Location:

src/main/java/com/redislite/command/

 CommandHandler.java 

Functional interface used by the dispatcher.

Each command is registered as a handler in a map.

This makes adding a command straightforward.

────────────────────

# CommandDispatcher.java 

Maps parsed commands to store operations.

Supported commands:

SET
GET
DEL
EXISTS
PING
QUIT
EXPIRE
TTL
DBSIZE

 SET 

SET key value

Stores or overwrites a value.

Response:

OK

 SET  also:

• Moves the key to the front of the LRU list.
• Clears any previous TTL.
• Can trigger LRU eviction when capacity is full.

 GET 

GET key

Returns the value, or:

(nil)

if missing or expired.

A successful  GET  refreshes LRU recency.

 DEL 

DEL key

Returns:

1

if removed, otherwise:

0

 EXISTS 

EXISTS key

Returns:

1

if the key exists and is live, otherwise:

0

This command does not change LRU order.

 PING 

PING

Returns:

PONG

 QUIT 

QUIT

Returns:

BYE

and closes the client connection in TCP mode.

 EXPIRE 

EXPIRE key seconds

Sets a TTL.

Response:

1

if the key exists and the expiry is applied.

Response:

0

if the key does not exist.

 TTL 

TTL key

Responses:

-2

if the key does not exist,

-1

if the key has no expiration,

or the remaining number of seconds rounded upward.

 DBSIZE 

DBSIZE

Returns the current number of entries in the store.

An expired entry may temporarily be counted until lazy or active cleanup removes it.

────────────────────

 Reply.java 

Represents the result of processing one request.

Reply(String text, boolean close)

Examples:

new Reply("PONG", false)
new Reply("BYE", true)
new Reply(null, false)

The  close  field tells the REPL or client handler whether to terminate the connection.

────────────────────

 RequestProcessor.java 

Common interface for processing one request line:

Reply process(String line);

Both TCP and REPL modes use this interface.

────────────────────

 DefaultRequestProcessor.java 

Contains the shared parse-and-dispatch logic.

It:

1. Ignores blank lines.
2. Parses commands.
3. Executes the dispatcher.
4. Converts parser errors into  ERR  responses.
5. Converts persistence failures into:

ERR persistence failure, server is read-only

6. Converts unexpected runtime errors into:

ERR internal error

7. Marks  QUIT  replies as connection-closing.

────────────────────

 SynchronizedRequestProcessor.java 

Wraps another request processor with a global  ReentrantLock .

The lock covers complete command processing:

parse
dispatch
store operation
AOF append

The lock does not cover socket reads or socket writes.

The same lock is shared by:

• Client command processing
• REPL command processing
• Expiry sweeper

This protects the non-thread-safe store structures.

────────────────────

 server  package

Location:

src/main/java/com/redislite/server/

 TcpServer.java 

Owns the listening TCP socket.

It:

1. Binds a  ServerSocket .
2. Starts an accept thread.
3. Accepts clients.
4. Tracks open sockets.
5. Creates one client handler per connection.
6. Stops and closes all clients during shutdown.

The server uses blocking I/O and a cached thread pool.

Client threads are daemon threads named like:

client-1
client-2
client-3

Port  0  is supported for tests and lets the operating system select a free port.

────────────────────

 ClientHandler.java 

Handles one TCP connection.

For each line received:

1. Reads with UTF-8.
2. Calls  RequestProcessor.process .
3. Writes the response with UTF-8.
4. Writes an explicit  \n .
5. Flushes the response.
6. Closes the connection if the reply says  close = true .

It correctly handles:

• Partial TCP packets
• Multiple commands in one packet
•  \n 
•  \r\n 
• Client disconnects
• Runtime request failures
•  QUIT 

A slow client does not hold the global command lock while network I/O is happening.

────────────────────

 store  package

Location:

src/main/java/com/redislite/store/

 KeyValueStore.java 

Storage abstraction.

It defines:

set
get
delete
exists
size
setExpiryAt
ttlMillis

The command layer depends on this interface rather than directly depending on  ExpiringLruStore .

────────────────────

 InMemoryStore.java 

The original Checkpoint 1  HashMap -backed store.

It remains in the repository to preserve older tests and historical implementation.

The current Checkpoint 3 application uses  ExpiringLruStore  instead.

────────────────────

 Entry.java 

Represents a key/value entry and links the entry into the LRU list.

Fields include:

key
value
expireAtMillis
prev
next

────────────────────

 LruList.java 

Hand-written doubly linked list.

It maintains:

Most recently used -> least recently used

The list supports constant-time insertion, removal, movement, and eviction from the back.

────────────────────

 ExpiringLruStore.java 

Main Checkpoint 3 storage implementation.

Internals:

HashMap<String, Entry>
LruList
MinHeap<ExpiryRecord>

Responsibilities:

• Store values
• Track expiry timestamps
• Perform lazy expiry
• Perform active expiry
• Track LRU order
• Enforce maximum key count
• Notify eviction listeners

It is intentionally not thread-safe. The global lock protects access to it.

────────────────────

 EvictionListener.java 

Callback called when LRU eviction removes a key.

In the application, it is connected to the AOF writer so eviction produces:

DEL evicted-key

This is necessary for correct replay.

────────────────────

 ExpirySweeper.java 

Background scheduled task that removes expired entries without requiring client access.

It:

• Runs on a daemon thread.
• Uses the shared global lock.
• Removes expiry records in bounded batches.
• Catches runtime exceptions so one failure does not cancel future sweeps.
• Stops idempotently.

────────────────────

 time  package

Location:

src/main/java/com/redislite/time/

 TimeSource.java 

Abstract clock interface:

long nowMillis();

 SystemTimeSource.java 

Uses:

System.currentTimeMillis()

This is necessary because expiry timestamps must survive process restarts.

────────────────────

 ds  package

Location:

src/main/java/com/redislite/ds/

 MinHeap.java 

Custom array-backed binary min-heap used for expiry records.

It does not use Java’s  PriorityQueue .

The heap can contain stale expiration records. The store checks whether each record still matches the current entry before deleting anything.

────────────────────

 persistence  package

Location:

src/main/java/com/redislite/persistence/

 FsyncPolicy.java 

Defines:

ALWAYS
EVERYSEC
NO

 MutationLog.java 

Persistence interface for mutation records:

appendSet
appendDelete
appendExpireAt
isHealthy
close

 AofWriter.java 

Writes mutation records to the AOF.

Record formats:

SET key value
DEL key
PEXPIREAT key epochMillis

The file uses UTF-8 and one record per line.

 AofLoader.java 

Reads and replays the AOF at startup.

It handles:

• Missing AOF
• Empty AOF
• Strict UTF-8 validation
• Torn final records
• File truncation
• Corrupt complete records
• Absolute expiry timestamps

Replay is performed directly into the raw store to avoid writing new records while replaying.

 AofCorruptException.java 

Thrown when a complete AOF record is invalid.

Examples:

• Unknown command
• Missing fields
• Invalid timestamp
• Invalid UTF-8

 PersistenceException.java 

Unchecked exception for persistence failures.

It causes the server to reject future writes while continuing to serve reads.

 PersistentStore.java 

Decorator that combines:

KeyValueStore
+
MutationLog

Mutation flow:

1. Check AOF health
2. Mutate in-memory store
3. Append mutation to AOF
4. Return result

Reads do not write to the AOF.

Lazy expiration and sweeper expiration are not logged.

LRU evictions are logged as  DEL .

────────────────────

 bench  package

Location:

src/main/java/com/redislite/bench/

 BenchmarkClient.java 

Closed-loop TCP load generator.

It opens multiple client connections and measures:

• Throughput
• p50 latency
• p95 latency
• p99 latency
• Maximum latency

Supported workload values include:

get
set
mixed

 RecoveryBenchmark.java 

Generates AOF records and measures replay speed.

It reports:

• Record count
• File size
• Replay duration
• Records per second

────────────────────

Test structure

Location:

src/test/java/com/redislite/

Important tests include:

cli/ReplTest.java
command/CommandDispatcherTest.java
command/ExpiryCommandTest.java
command/SynchronizedRequestProcessorTest.java
ds/MinHeapTest.java
persistence/AofPersistenceTest.java
protocol/CommandParserTest.java
server/TcpServerTest.java
server/TcpServerConcurrencyTest.java
store/ExpiringLruStoreTest.java
store/LruListTest.java

The tests cover:

• Basic storage
• Parsing
• Command dispatch
• REPL behavior
• TCP behavior
• Concurrent clients
• Min-heap operations
• LRU ordering
• Expiry
• LRU eviction
• AOF writing
• AOF replay
• Torn-tail handling
• TTL commands

Run all tests:

mvn clean test

────────────────────

Project runtime flow

Startup flow

When running:

java -jar target/redis-lite-1.0-SNAPSHOT.jar

the application performs:

Main
  |
  v
Config.parse(args)
  |
  v
App
  |
  v
Create ExpiringLruStore
  |
  v
Replay appendonly.aof
  |
  v
Create AofWriter
  |
  v
Attach eviction listener
  |
  v
Apply max-key limit
  |
  v
Create PersistentStore
  |
  v
Create CommandDispatcher
  |
  v
Create synchronized request processor
  |
  v
Start expiry sweeper
  |
  v
Start TCP server

Request flow

For:

SET name Redis

the flow is:

Socket
  |
  v
ClientHandler.readLine()
  |
  v
SynchronizedRequestProcessor
  |
  v
DefaultRequestProcessor
  |
  v
CommandParser
  |
  v
ParsedCommand("SET", ["name", "Redis"])
  |
  v
CommandDispatcher
  |
  v
PersistentStore.set()
  |
  +--> ExpiringLruStore.set()
  |
  +--> AofWriter.appendSet()
  |
  v
Reply("OK", false)
  |
  v
ClientHandler writes "OK\n"

Shutdown flow

When Ctrl+C is pressed:

Shutdown hook
  |
  v
Stop TcpServer
  |
  v
Close client sockets
  |
  v
Stop ExpirySweeper
  |
  v
Close AofWriter
  |
  v
Final fsync

────────────────────

Commands to run the project

Go to the project directory:

cd /home/a8hi9t/Work/kiwi-redis

Run tests

mvn clean test

Build the project

mvn package

The JAR is created at:

target/redis-lite-1.0-SNAPSHOT.jar

Build and test together

mvn clean test package

Start the TCP server

java -jar target/redis-lite-1.0-SNAPSHOT.jar

Default port:

6380

Default AOF:

appendonly.aof

Start on a custom port

java -jar target/redis-lite-1.0-SNAPSHOT.jar --port 7000

Ask for a free port

java -jar target/redis-lite-1.0-SNAPSHOT.jar --port 0

The selected port appears in:

Listening on port <port>

Start without AOF persistence

java -jar target/redis-lite-1.0-SNAPSHOT.jar --no-aof

Start in REPL mode

java -jar target/redis-lite-1.0-SNAPSHOT.jar --repl

Set maximum key capacity

java -jar target/redis-lite-1.0-SNAPSHOT.jar --max-keys 1000

Use a custom AOF path

java -jar target/redis-lite-1.0-SNAPSHOT.jar \
  --aof-file data/appendonly.aof

Use  always  fsync

java -jar target/redis-lite-1.0-SNAPSHOT.jar \
  --aof-fsync always

Use  everysec  fsync

java -jar target/redis-lite-1.0-SNAPSHOT.jar \
  --aof-fsync everysec

Disable automatic fsync

java -jar target/redis-lite-1.0-SNAPSHOT.jar \
  --aof-fsync no

Change expiry sweep interval

java -jar target/redis-lite-1.0-SNAPSHOT.jar \
  --sweep-interval-ms 50

Combine options

java -jar target/redis-lite-1.0-SNAPSHOT.jar \
  --port 7000 \
  --max-keys 1000 \
  --aof-file data/appendonly.aof \
  --aof-fsync everysec \
  --sweep-interval-ms 100

Connect with netcat

Start the server:

java -jar target/redis-lite-1.0-SNAPSHOT.jar --no-aof

In another terminal:

nc localhost 6380

Then enter:

SET name Redis
GET name
EXPIRE name 60
TTL name
DBSIZE
QUIT

Expected responses:

OK
Redis
1
60
1
BYE

Scripted TCP commands

printf 'SET name Redis\nGET name\nEXPIRE name 60\nTTL name\nDBSIZE\nQUIT\n' \
  | nc localhost 6380

Run the benchmark client

Start a server first, for example:

java -jar target/redis-lite-1.0-SNAPSHOT.jar \
  --port 6380 \
  --no-aof

Then run:

java -cp target/classes com.redislite.bench.BenchmarkClient \
  --host 127.0.0.1 \
  --port 6380 \
  --clients 10 \
  --requests 10000 \
  --workload mixed

Other workload options:

--workload get
--workload set
--workload mixed

Run the recovery benchmark

java -cp target/classes com.redislite.bench.RecoveryBenchmark

Specify a file and record count:

java -cp target/classes \
  com.redislite.bench.RecoveryBenchmark \
  bench-data/recovery.aof \
  100000

Run fsync benchmark script

sh scripts/run-fsync-benchmarks.sh

Run this only when the server/build setup expected by the script is available.

────────────────────

Out of scope after Checkpoint 3

The following are intentionally not implemented.

AOF compaction or rewrite

The AOF grows continuously.

There is no background rewrite that removes obsolete records.

Snapshots

There is no RDB-style snapshot mechanism.

Recovery always depends on AOF replay.

Checksums

AOF records do not have CRC or checksum fields.

Malformed records and invalid UTF-8 can be detected, but checksum-based corruption recovery is not implemented.

RESP protocol

The server uses a custom newline-delimited protocol.

It is not compatible with normal Redis clients that expect RESP.

Additional Redis data types

Only string values are supported.

Not implemented:

Lists
Sets
Hashes
Sorted sets
Streams
Bitmaps

Additional commands

Not implemented:

PERSIST
PEXPIRE
SET ... EX
KEYS
SCAN
INCR

Also not implemented:

• Transactions
• Pub/sub
• Scripting
• Replication
• Clustering
• Multiple databases
• Configuration files
• Authentication
• TLS

Custom hash table

The key index still uses Java’s  HashMap .

Only the heap and LRU list are custom data structures.

Fine-grained locking

The project does not use:

• Per-key locks
• Sharded locks
• Lock striping
• Read-write locks

Event-loop networking

The project uses blocking I/O and one thread per connection.

It does not use:

• NIO selectors
•  epoll 
• Netty
• An event-loop architecture

────────────────────

Current limitations

Global lock limits throughput

All commands use one global lock.

This keeps the  HashMap , LRU list, and expiry heap safe, but serializes all command processing.

Independent keys cannot be processed simultaneously.

 ALWAYS  fsync reduces throughput

With:

--aof-fsync always

every write waits for a filesystem force while the command is being processed.

This improves durability but increases latency and reduces throughput.

Data is process-local

All clients connected to one process share one store. A second process has a separate store.

There is no distributed state.

AOF grows indefinitely

A busy application can create a very large AOF over time.

Large AOF files increase restart time.

LRU order changes after restart

 GET  changes LRU recency, but  GET  is not written to the AOF.

After recovery, LRU order is reconstructed from mutation order rather than the complete pre-crash access history.

Eviction decisions after restart can therefore differ.

TTL uses wall-clock time

Production expiry uses  System.currentTimeMillis()  because absolute timestamps must survive restart.

Clock changes from NTP synchronization or manual adjustment can affect expiration.

 DBSIZE  may count expired entries temporarily

A key can be logically expired while still physically present until:

• A client accesses it, or
• The expiry sweeper removes it.

During that interval,  DBSIZE  may include it.

One thread per connection

Each client gets a separate handler thread.

A very large number of clients can consume many threads and increase scheduling overhead.

No request size limit

A client can send an extremely long line without a newline.

There is no hard maximum request-line size in the current implementation.

No idle timeout

A client that connects and never sends a complete command may keep a socket and handler thread open indefinitely.

Apply-then-log persistence trade-off

Mutations update memory before they are appended to the AOF.

If the AOF write fails:

• The in-memory mutation may already have happened.
• The server becomes read-only for later mutations.
• The failed mutation may not survive a restart.

This behavior is deliberate and documented.

No production security

The server has no authentication, encryption, or access control.

It should only be used in a trusted environment.

Benchmark limitations

The stress numbers in  docs/FINAL_REPORT.md  are:

• Short-duration tests
• Single-machine tests
• Closed-loop workloads
• Non-pipelined
• Based on a fixed keyspace
• Affected by JVM, CPU, disk, and background processes

They should not be treated as production capacity guarantees.

