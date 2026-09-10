# Part 2: Kafka and PostgreSQL pipeline

The pipeline is `HTTP simulation request → Kafka → Java consumer → PostgreSQL events + minute metrics`. It uses real Kafka and PostgreSQL. The application still has no dashboard or anomaly detector; those are later parts.

## Start locally on Windows

From the repository root:

```powershell
cd backend
.\scripts\start-part2.ps1
```

Requirements: JDK 21 on PATH and internet access for the first Maven download. Docker, WSL, and a system PostgreSQL installation are not required. The script compiles a development-only Java launcher using test-scope dependencies. Wait for `SentinelPay pipeline ready at http://localhost:8081` before making requests. The preview on port 8080 can remain running.

To choose another port, use `.\scripts\start-part2.ps1 -Port 8082`. HTTP, PostgreSQL, and both Kafka listeners bind to loopback. Kafka and PostgreSQL use dynamically assigned ports. Press **Enter** in the launcher terminal to stop cleanly; it closes the Spring application, Kafka, then PostgreSQL. `Ctrl+C` also triggers a shutdown hook, but Enter is preferred on Windows. A file lock prevents two launchers from sharing the same data directories.

PostgreSQL data is retained in ignored `backend/.local/postgres/`; Kafka records, consumer offsets, and dead letters use `backend/.local/kafka/`. Both survive a normal restart. The verification script checks delivery of an acknowledged-but-unconsumed payment after restarting the stack. This is still a single local broker, not replicated or backed-up storage. Kafka's normal retention settings apply; automatic application-level retention and archival are not implemented. Do not delete or reset either data directory while the launcher is running.

The bundled native database is for local development and tests. It uses test credentials and is not a secured deployment. The integration code also supports separately managed Kafka/PostgreSQL as described below; test infrastructure is not included in the packaged application JAR.

## Publish, then check storage

In another PowerShell terminal:

```powershell
$batch = Invoke-RestMethod -Method Post 'http://localhost:8081/api/pipeline/simulations?count=10&scenario=DEGRADED&seed=42'
$batch | ConvertTo-Json -Depth 5
```

HTTP 202 returns `state: ACKNOWLEDGED`, `acknowledgedIds`, `uncertainId: null`, and a detail message. This means Kafka acknowledged the events; the database consumer runs asynchronously.

Check a stored payment and recent records:

```powershell
$id = $batch.acknowledgedIds[0]
Invoke-RestMethod "http://localhost:8081/api/pipeline/transactions/$id"
Invoke-RestMethod 'http://localhost:8081/api/pipeline/transactions?limit=10' | ConvertTo-Json -Depth 5
Invoke-RestMethod 'http://localhost:8081/api/pipeline/metrics' | ConvertTo-Json -Depth 5
```

An immediate 404 can mean the consumer has not processed the ID yet; check again shortly. A new simulation request creates new payment IDs even with the same seed. Seeds repeat generated amounts, outcomes, and latency, not event identity.

Publishing is bounded to 1–100 events per request. If publishing fails partway, HTTP 503 includes the acknowledged IDs and the currently uncertain ID, if any. That uncertain event may still be delivered. Check known IDs instead of blindly retrying a simulation batch. There is no HTTP idempotency-key protocol in this part.

## Understand the metrics

Metrics are grouped by the payment's UTC event-time minute and currency:

- `totalCount`, `successCount`, and `failedCount` count distinct stored event IDs.
- `totalAmount` sums attempted payment amounts, including failures. It is not settled revenue.
- `averageLatencyMs` is the exact numeric sum divided by count, rounded to three decimal places.
- `maxLatencyMs` is the largest observed simulated latency in that bucket.

Late events update their original minute. Empty minutes are omitted. The default query covers the current minute and previous 59 minutes. Results are newest first, with a default limit of 60 and maximum of 100. Set `from` and `to` together to query another window of at most 24 hours:

```powershell
Invoke-RestMethod 'http://localhost:8081/api/pipeline/metrics?from=2026-09-09T12:00:00Z&to=2026-09-09T13:00:00Z&limit=100'
```

Both timestamps must be minute-aligned. The lower bound is inclusive; the upper bound is exclusive. Use a window containing your data. Query smaller time ranges when more than 100 buckets would be returned. The API does not calculate p95/p99 or zero-fill missing minutes yet.

## Delivery and failure semantics

Each Kafka value is a JSON object with `schemaVersion: 1` and an `event`. The Kafka key equals `event.id`. Decimals are preserved as `BigDecimal`. Wire timestamps must be from 1970 to before 2200, and IDs are limited to 128 characters. PostgreSQL stores timestamps at microsecond precision.

The consumer uses record acknowledgments with auto-commit disabled. It inserts the raw event and updates the rollup in one PostgreSQL transaction before returning. An identical replay is a no-op. The same ID with different data is rejected. This provides idempotent database effects under at-least-once delivery; it is not a distributed exactly-once transaction.

Invalid JSON, unsupported schemas, mismatched keys, and conflicting IDs are routed to `sentinelpay.payments.v1.dlt`, with source/error headers. The source record is acknowledged only after dead-letter publishing succeeds. Database and unexpected processing errors remain uncommitted and retry with backoff. A persistent failure can block progress on that consumer; inspection and repair are required. There is no dead-letter replay UI or automated repair workflow yet.

The producer uses idempotence and `acks=all`, but the development topics have replication factor 1. This does not provide broker high availability. HTTP publishing waits within a bounded deadline and reports uncertainty rather than claiming an unacknowledged event succeeded.

## Run all checks

```powershell
.\scripts\verify-part2.ps1
```

This runs Maven tests and packages the JAR, then starts an isolated local pipeline, publishes five events for each scenario, waits for all 15 stored IDs, and checks their metrics and HTTP validation. It then pauses the consumer, publishes one more payment, restarts the stack against the same Kafka/PostgreSQL files, and verifies both the stored history and delivery of that pending payment. Temporary verification data stays under ignored `target/`; your `.local/` data is not used or reset.

Failure tests intentionally emit Kafka and database exception logs. Inspect the final Maven result and verification message. `Part 2 verification passed` is the success marker. First-time dependency downloads and native database initialization take longer than simulator-only tests.

## Separately managed services

Create a PostgreSQL database and role with migration privileges. Provide your service addresses and credentials through environment variables, then run the packaged application:

```powershell
$env:SENTINELPAY_KAFKA_SERVERS = 'localhost:9092'
$env:SENTINELPAY_DB_URL = 'jdbc:postgresql://localhost:5432/sentinelpay'
$env:SENTINELPAY_DB_USER = 'sentinelpay'
# Set SENTINELPAY_DB_PASSWORD to your database role password in this terminal.
.\mvnw.cmd verify
java -jar target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=pipeline --server.port=8081
```

Flyway applies versioned migrations on startup. The app creates payment and dead-letter topics with three partitions and replication factor 1, so the local Kafka principal needs topic-creation access. Do not use these local defaults as production configuration. Kafka TLS/SASL, HTTP authentication, rate limiting, retention, backups, monitoring of consumer lag, and deployment hardening remain later work.

PostgreSQL is tested in this checkpoint. TimescaleDB hypertables are deferred; the current tables use PostgreSQL indexes and explicit minute rollups. Linux/macOS native tooling has not been verified in this Windows session.

## Code to explain in an interview

`PipelineConfiguration` owns infrastructure wiring under one profile. `PaymentPublisher` sends versioned messages and reports partial success. `PaymentConsumer` decodes and saves each record. `PaymentStore` owns a short SQL transaction with unique-ID deduplication and atomic upserts. `PipelineController` keeps HTTP validation and response semantics separate from storage. The simulator and transaction model remain usable without this profile. The development-only `LocalKafka` launches a native KRaft broker with explicit listeners and log paths; it does not replace Kafka with an in-memory queue.
