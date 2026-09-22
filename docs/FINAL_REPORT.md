# Redis-lite Final Stress-Test Report

**Test date:** 2026-09-20 (local time, UTC+05:30)  
**Repository:** `/home/a8hi9t/Work/kiwi-redis`  
**Build:** `redis-lite-1.0-SNAPSHOT.jar`

## Environment

- Linux `x86_64`, kernel `7.2.5-4-omarchy`
- 12 logical CPUs
- 14 GiB RAM (about 10 GiB available at capture time)
- ext4 filesystem
- OpenJDK `26.0.2.1`
- Maven build with Java release target 17

These are closed-loop TCP measurements from the included Java
`com.redislite.bench.BenchmarkClient`. Each request was flushed and its reply
read before the next request on that connection. The client reports the
completed-request latency distribution; no requests were reported as failed.

## Build and test validation

Command:

```text
mvn clean test package
```

Result: **PASS**. Maven reported 38 tests, 0 failures, 0 errors, and built
`target/redis-lite-1.0-SNAPSHOT.jar` successfully. The build completed at
`2026-09-20T00:50:57+05:30`.

## Throughput and latency

The server was started with `--no-aof --sweep-interval-ms 50` on port 6381.
The following are single measured runs (not averages):

| Workload | Clients | Requests | Throughput | p50 | p95 | p99 | Max |
|---|---:|---:|---:|---:|---:|---:|---:|
| GET, 1,000-key working set | 1 | 20,000 | 9,299.28 ops/s | 0.033 ms | 0.636 ms | 1.416 ms | 10.579 ms |
| Mixed (80% GET / 20% SET), 1,000-key working set | 8 | 80,000 | 43,094.85 ops/s | 0.051 ms | 0.830 ms | 1.778 ms | 13.055 ms |
| Mixed (80% GET / 20% SET), 1,000-key working set | 32 | 160,000 | 102,812.65 ops/s | 0.065 ms | 1.265 ms | 2.369 ms | 14.778 ms |

Commands:

```text
java -jar target/redis-lite-1.0-SNAPSHOT.jar --port 6381 --no-aof --sweep-interval-ms 50
java -cp target/classes com.redislite.bench.BenchmarkClient --port 6381 --clients 1 --requests 20000 --workload get
java -cp target/classes com.redislite.bench.BenchmarkClient --port 6381 --clients 8 --requests 80000 --workload mixed
java -cp target/classes com.redislite.bench.BenchmarkClient --port 6381 --clients 32 --requests 160000 --workload mixed
```

## TTL and expiry

On a server configured with a 25 ms sweep interval, the following TCP sequence
was run after filling the store:

```text
SET ttl value
EXPIRE ttl 1
TTL ttl
sleep 2 seconds
GET ttl
EXISTS ttl
```

Observed replies were `OK`, `1`, `1`, then `(nil)`, `0`. The final `DBSIZE`
was 99 after the expired entry was removed. This demonstrates both the
command-visible TTL and active expiry; it does not measure expiry precision
under heavy load.

## LRU eviction

The server was started with `--max-keys 100 --no-aof --sweep-interval-ms 25`.
Keys `k0` through `k99` were inserted, `GET k0` was issued to refresh its
recency, and then `SET k100 v` was issued.

Observed results:

- `DBSIZE`: 100 after insertion of `k100`
- `GET k1`: `(nil)` (least-recently-used key was evicted)
- `GET k0`: `v` (the explicitly touched key remained)
- After TTL expiry cleanup, final `DBSIZE`: 99

This validates capacity enforcement and recency-sensitive eviction for this
small deterministic case.

## AOF persistence and recovery

The server was run on port 6383 with:

```text
--aof-file bench-data/stress-always.aof --aof-fsync always --sweep-interval-ms 50
```

The persistence workload wrote durable and expiring keys, followed by a
40,000-request, eight-client mixed benchmark:

```text
java -cp target/classes com.redislite.bench.BenchmarkClient --port 6383 --clients 8 --requests 40000 --workload mixed
```

Measured result with `always` fsync: **6,867.62 ops/s**, p50 **1.233 ms**,
p95 **2.657 ms**, p99 **3.354 ms**, max **16.614 ms**. The AOF contained
8,003 replayable records after this workload, was 87,187 bytes, and restart
reported `AOF replay: 8003 records in 25 ms`.

After stopping and restarting with the same AOF, `GET durable` returned
`survived`, `EXISTS durable` returned `1`, and the one-second `short` key
returned `(nil)` / `0`. This confirms persistence across restart and expiry
state being honored after replay.

Standalone replay measurement:

```text
java -cp target/classes com.redislite.bench.RecoveryBenchmark bench-data/recovery-stress.aof 50000
```

Observed: 50,000 records, 838,890 bytes, replay time 70 ms, approximately
679,624 records/s.

## Pass/fail summary

| Area | Result | Evidence |
|---|---|---|
| Clean build, tests, package | PASS | 38 tests passed; jar built |
| Single-client GET | PASS | 20,000/20,000 completed |
| Concurrent mixed workload | PASS | 240,000/240,000 completed across 8 and 32 clients |
| TTL and active expiry | PASS | Expired key became absent after 2 seconds |
| LRU capacity/recency | PASS | `k1` evicted while touched `k0` remained |
| AOF always-fsync workload | PASS | 40,000/40,000 completed |
| AOF restart recovery | PASS | Durable key restored; expired key absent |
| AOF replay benchmark | PASS | 50,000 records replayed |

## Observations and limitations

- Throughput increased from 9,299 ops/s at one client to 102,813 ops/s at
  32 clients in this run, while tail latency increased from 1.416 ms to
  2.369 ms at p99.
- `always` fsync was substantially slower than the no-AOF mixed workload
  (6,868 ops/s versus 43,095 ops/s at eight clients), consistent with a
  synchronous durability cost.
- These are short, single-run measurements on a development workstation, not
  a production capacity claim. CPU frequency, filesystem cache, JVM state,
  background processes, and scheduling can affect the results.
- No sustained-duration soak, crash-power-loss test, fsync `everysec` comparison,
  or multi-node/network test was performed. Percentiles are client-observed
  and include socket scheduling overhead.
- The benchmark client uses a fixed 1,000-key working set and does not model
  pipelining or large values. The project’s known global request lock and
  unbounded AOF growth remain relevant scalability limitations.

## Conclusion

The packaged Java server passed the requested build, functional, concurrency,
TTL, LRU, persistence, and recovery checks. In this environment it sustained
approximately 103k ops/s for a short no-AOF mixed workload at 32 clients with
sub-2.4 ms p99 latency, while `always` AOF durability measured approximately
6.9k ops/s. The results support the documented behavior and trade-offs but
should not be interpreted as long-run production benchmarks.
