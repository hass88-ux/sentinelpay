# SentinelPay

SentinelPay is a Java-first project working toward predictive monitoring and incident intelligence for payment systems. It builds on my previous Transaction Anomaly Detection Engine by progressing from individual transaction analysis toward payment-system monitoring, anomaly detection, prediction, and AI-assisted incident investigation.

## Current progress

Part 1 implements the Java simulator foundation. The backend currently includes:

- A Spring Boot application that runs on port 8080.
- An immutable `TransactionEvent` record representing one completed simulated payment attempt.
- Validation of required fields, nonblank IDs, positive amounts, and nonnegative latency.
- A `TransactionStatus` enum with `SUCCESS` and `FAILED` outcomes.
- A plain Java `PaymentSimulator` that generates one event per method call.
- A standalone console demo that prints ten simulated transactions and exits.
- Validated simulator settings and synthetic normal, degraded, and outage scenarios.
- A bounded HTTP preview with structured validation errors and request-local random state.
- JUnit coverage of the model, generator, settings, and Spring application startup.

Continuous generation, event streaming, storage, monitoring metrics, detection, prediction, and AI explanations are planned features. The simulator runs through the console demo, HTTP preview, or tests. It does not start a background producer on application startup.

## Current stack

- Java 21
- Spring Boot 4.1.1
- Maven
- JUnit

## Structure

```text
backend/
  pom.xml
  src/main/java/com/sentinelpay/backend/
    BackendApplication.java
    transaction/
      TransactionEvent.java
      TransactionStatus.java
    simulator/
      PaymentSimulator.java
      PaymentSimulatorDemo.java
      SimulationSettings.java
      SimulationScenario.java
      SimulationRunner.java
      DemoOptions.java
      api/
        SimulationController.java
        SimulationPreview.java
        SimulationErrorHandler.java
  src/main/resources/
    application.properties
  src/test/java/com/sentinelpay/backend/
    BackendApplicationTests.java
    transaction/TransactionEventTest.java
    simulator/PaymentSimulatorTest.java
    simulator/SimulationSettingsTest.java
    simulator/SimulationReplayTest.java
    simulator/PaymentSimulatorDemoTest.java
    simulator/SimulationApiTest.java
  scripts/verify-part1.ps1
docs/part-1-demo.md
```

The transaction package defines valid payment event data. The simulator depends on that model and creates events without requiring Spring. Spring Boot provides the application entry point for later integration work.

## Run the backend

Install JDK 21 and Maven, and make sure `java` and `mvn` are available on your terminal's PATH. From the repository root:

```powershell
cd backend
mvn spring-boot:run
```

Expect Spring Boot startup logs and an embedded server on port 8080. Stop it with `Ctrl+C`. Starting the backend does not print simulated transactions yet.

The included Maven wrapper can also be used: `./mvnw` on macOS/Linux or `.\mvnw.cmd` on Windows. It downloads Maven on first use, so initial setup requires internet access. JDK 21 is required. The Windows wrapper handles both ordinary and linked Maven repository directories. Spring Boot's main class is explicitly configured so the console demo does not interfere with executable JAR packaging.

## Run the console demo

From `backend/`:

```powershell
mvn compile
java -cp target/classes com.sentinelpay.backend.simulator.PaymentSimulatorDemo
```

Compilation should end with `BUILD SUCCESS`. The demo prints exactly ten `TransactionEvent[...]` lines, each containing an ID, timestamp, amount, currency, status, and simulated latency, then exits and returns to the terminal prompt. Values vary between runs; all ten transactions may have `SUCCESS` status. The simulated latency does not delay printing.

In Eclipse, refresh the backend project, then right-click `PaymentSimulatorDemo.java` in `com.sentinelpay.backend.simulator` and choose **Run As → Java Application**. The ten events appear in the Console view. This demo runs independently of the Spring Boot server.

Choose a finite run with optional arguments:

```powershell
java -cp target/classes com.sentinelpay.backend.simulator.PaymentSimulatorDemo --count=20 --scenario=DEGRADED --seed=42 --interval-ms=100
```

`--count` accepts 1–10,000 (default 10), `--interval-ms` accepts 0–1,000 (default 0), `--scenario` accepts NORMAL, DEGRADED, or OUTAGE (case-insensitive), and `--seed` accepts a Java long integer. Use `--help` for usage. In Eclipse, put these options in **Run Configurations → Arguments → Program arguments**.

The interval is a delay between emissions, separate from the event's simulated latency; output and generation time also affect wall-clock spacing. The runner streams events to its output without storing the batch. Press `Ctrl+C` to stop a long console run. Invalid or duplicate options produce an error on stderr and exit code 2 before emitting anything. Java thread interruption is preserved and returns code 130; the operating system may choose its own exit code for Ctrl+C. No-argument runs still print exactly ten event lines.

## HTTP preview

### Pipeline endpoints (opt-in `pipeline` profile)

- `POST /api/pipeline/simulations?count=10&scenario=NORMAL&seed=42` publishes 1–100 generated events. HTTP 202 reports Kafka-acknowledged IDs; database consumption is asynchronous. HTTP 503 reports acknowledged IDs plus an uncertain ID if publishing stops partway. A timed-out event can still arrive, and repeating a simulation request creates new IDs rather than replaying the old batch.
- `GET /api/pipeline/transactions/{id}` returns a stored event or 404. An immediate 404 after publishing can mean consumption is still pending.
- `GET /api/pipeline/transactions?limit=20` returns the newest stored events (limit 1–100).
- `GET /api/pipeline/metrics?limit=60` returns the latest minute/currency buckets from the last hour. Supply both `from` and `to` as minute-aligned ISO UTC timestamps to query another range, up to 24 hours. The lower bound is inclusive and upper bound exclusive; limits are 1–100 and results are newest first. Empty minutes are absent, not zero-filled. For more than 100 buckets, query smaller time ranges.

These endpoints are unavailable in the default preview-only application. The pipeline profile binds to loopback by default and is for local development; it has no authentication or rate limiting. Query responses disable caching and invalid input receives HTTP 400 problem details.

### Standalone preview

With the backend running on port 8080:

```powershell
Invoke-RestMethod 'http://localhost:8080/api/simulator/transactions?count=5&scenario=DEGRADED&seed=42' | ConvertTo-Json -Depth 5
```

`GET /api/simulator/transactions` returns an object with `scenario`, `count`, and an `events` array. Each event contains the six fields listed below. Query parameters are optional: `count` defaults to 10 and is limited to 1–100, `scenario` defaults to NORMAL and is case-insensitive, and `seed` accepts a Java long integer. Unlike the CLI, HTTP preview has no pacing option and immediately generates a bounded batch.

Invalid counts, scenarios, or numeric values return HTTP 400 with an `application/problem+json` body containing `title`, `status`, and a useful `detail`. Successful responses use `Cache-Control: no-store`. Each request has its own generator: identical seeds/settings repeat simulated fields, while IDs and timestamps are fresh. Events are not stored and there is no transaction-history endpoint. This is a development preview with no authentication or rate limiting; production deployment belongs to a later part.

## Run tests

From `backend/`:

```powershell
mvn test
```

Expected result:

```text
Tests run: 57, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

To run only the simulator tests:

```powershell
mvn -Dtest=PaymentSimulatorTest test
```

Expect four passing tests. These check generated values and bounds, coverage of both statuses, repeatable random fields with a fixed seed, distinct event IDs, and rejection of a missing random generator. They do not measure throughput or establish production performance.

In Eclipse, import `backend/` as an existing Maven project using JDK 21. Refresh the project after external file changes. Right-click a test class and choose **Run As → JUnit Test**.

## Simulator behavior

`SimulationSettings` validates positive, ordered amount bounds, nonnegative ordered latency bounds, and a finite success probability between 0 and 1. Both bounds are inclusive, including when they are equal. Custom settings can be supplied through `new PaymentSimulator(random, settings)`.

| Scenario | Success probability | Latency range |
| --- | --- | --- |
| `NORMAL` (default) | 90% | 10–500 ms |
| `DEGRADED` | 70% | 300–2,000 ms |
| `OUTAGE` | 10% | 1,000–5,000 ms |

All three use $0.01–$500.00 USD amounts. Scenario names describe generated conditions; they are not detection results. These are illustrative independent random samples, not a calibrated model of correlated payment traffic.

With the default NORMAL settings, each call to `generateTransaction()` returns one event with these demo values:

| Field | Behavior |
| --- | --- |
| `id` | Newly generated UUID string |
| `timestamp` | Current time as an `Instant` |
| `amount` | Random amount from $0.01 to $500.00, inclusive |
| `currency` | USD |
| `status` | 90% success probability; otherwise failed |
| `latencyMs` | Random duration from 10 to 500 milliseconds, inclusive |

Amounts are generated as integer cents and converted to `BigDecimal` to avoid floating-point money calculations. Latency is an event value; the generator does not sleep to simulate processing time. The ranges and probability are illustrative settings, not measurements from a real payment system. A sample is not guaranteed to contain exactly 90% successes.

The constructor accepts a `Random` instance. Using the same seed reproduces the sequence of amounts, statuses, and latencies. UUIDs and timestamps are generated independently and are not reproduced by that seed.

For complete event replay in tests, the four-argument constructor also accepts a `Clock` and `Supplier<String>` for IDs. A fixed clock plus a reset ID sequence and identical random seed/settings reproduce complete events. Normal constructors retain real UTC time and random UUIDs. Create one simulator per run rather than sharing stateful sources between concurrent runs. The domain model validates supplied IDs, but uniqueness remains the ID supplier's responsibility.

## Five-part delivery plan

| Part | Scope | Status |
| --- | --- | --- |
| 1 | Java transaction model, configurable simulator, console demo, HTTP preview, reliable build and tests (original Phase 1) | Complete for this checkpoint |
| 2 | Kafka streaming, metric aggregation, PostgreSQL storage (original Phases 2–3; TimescaleDB extension deferred) | In progress |
| 3 | Monitoring and anomaly detection (original Phase 4) | Planned |
| 4 | Predictive incident model and AI-assisted investigation (original Phases 5–6) | Planned |
| 5 | React UI, expanded observability and testing, Docker, AWS, and demo polish (original Phase 7 plus frontend) | Planned |

A React frontend is also planned for a later stage. Tests and documentation are added incrementally as the backend develops.

Each part is delivered as roughly six focused commits with README updates. Part 1 deliberately uses no Kafka, database, AI, authentication, Docker, or frontend dependencies.

### Part 2 implementation

Part 2 adds opt-in Kafka and PostgreSQL integration. The local development path uses [Spring Kafka's embedded broker](https://docs.spring.io/spring-kafka/reference/testing.html) and [Zonky's native embedded PostgreSQL](https://github.com/zonkyio/embedded-postgres), both test-scope dependencies, to run real processes without Docker or a system database installation. They are excluded from the application JAR. The default application remains a standalone preview; infrastructure is only required for the upcoming `pipeline` profile. Local database files belong under ignored `backend/.local/`.

Six checkpoints: infrastructure and configuration; transactional event storage and minute metrics; Kafka publishing and consumption; pipeline HTTP controls and queries; failure/replay integration checks; local launch, end-to-end verification, and documentation. PostgreSQL is the supported storage engine in this part. TimescaleDB-specific hypertables and retention are deferred until the extension can be run and tested; no TimescaleDB compatibility claim is made yet.

Storage now uses Flyway migration `V1__payment_events_and_minute_metrics.sql`. `PaymentStore` inserts a raw payment and updates its UTC event-time minute/currency rollup in one database transaction. Replaying an identical ID is a no-op; reusing an ID with different data is rejected. PostgreSQL timestamps are normalized to microseconds. Each minute stores count, successes, failures, total attempted amount, latency sum, and maximum latency; average latency is derived from the sum/count. Late events update their original minute. Amounts of different currencies are never added together. Real PostgreSQL tests cover exact rollups, concurrent replay, rollback, time precision, and numeric overflow handling.

### Part 1 checkpoints

The `pipeline` Spring profile now wires Kafka and PostgreSQL explicitly. Version-1 JSON envelopes preserve decimal amounts and require the Kafka key to equal the event ID. Database commits occur before record acknowledgments. Invalid records are published to a separate dead-letter topic with Kafka error headers; a failed dead-letter publish does not acknowledge the original. Database and unexpected processing failures remain retryable rather than being discarded. This is at-least-once delivery with idempotent database effects, not a claim of distributed exactly-once transactions. A real-broker integration test verifies delivery and replay through the full consumer into PostgreSQL.

- [x] Reliable Maven wrapper, explicit Spring Boot entry point, and five-part plan.
- [x] Validated simulation settings and named demo scenarios.
- [x] Repeatable simulation with injectable time and event IDs.
- [x] Configurable finite console runner with input validation and cancellation.
- [x] Bounded HTTP simulation preview with useful validation errors.
- [x] End-to-end verification and a reproducible demo guide.

## Verify and demonstrate Part 1

From `backend/`, run `.\scripts\verify-part1.ps1` in PowerShell. It runs the test suite and packages the application, verifies ten-event console output, then starts the JAR on a temporary loopback port and checks health, all three HTTP scenarios, seed replay, and oversized-request rejection. It stops its own server in a `finally` block and keeps logs under ignored `target/`. No existing server on port 8080 is stopped.

For manual commands, expected behavior, and architecture talking points, see the [Part 1 demo guide](docs/part-1-demo.md). Tests are functional checks, not performance benchmarks. The wrapper and smoke script have been exercised on Windows with Java 21; other operating systems have not yet been verified.

Verified on September 9, 2026: all 57 JUnit tests passed with no failures, errors, or skips. The smoke script passed against the executable JAR, including real HTTP responses for health, NORMAL/DEGRADED/OUTAGE previews, repeatable seeded fields, fresh IDs, disabled response caching, and HTTP 400 for oversized batches. The Unix wrapper is marked executable in Git; its platform-specific behavior remains untested here.
