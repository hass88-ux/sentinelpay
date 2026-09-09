# SentinelPay

SentinelPay is a Java-first project working toward predictive monitoring and incident intelligence for payment systems. It builds on my previous Transaction Anomaly Detection Engine by progressing from individual transaction analysis toward payment-system monitoring, anomaly detection, prediction, and AI-assisted incident investigation.

## Current progress

Phase 1 is in progress. The backend currently includes:

- A Spring Boot application that runs on port 8080.
- An immutable `TransactionEvent` record representing one completed simulated payment attempt.
- Validation of required fields, nonblank IDs, positive amounts, and nonnegative latency.
- A `TransactionStatus` enum with `SUCCESS` and `FAILED` outcomes.
- A plain Java `PaymentSimulator` that generates one event per method call.
- A standalone console demo that prints ten simulated transactions and exits.
- Validated simulator settings and synthetic normal, degraded, and outage scenarios.
- JUnit coverage of the model, generator, settings, and Spring application startup.

Continuous generation, event streaming, storage, monitoring metrics, detection, prediction, and AI explanations are planned features. The simulator can be run through the console demo or tests and is not connected to an HTTP endpoint or Spring Boot application startup.

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
  src/main/resources/
    application.properties
  src/test/java/com/sentinelpay/backend/
    BackendApplicationTests.java
    transaction/TransactionEventTest.java
    simulator/PaymentSimulatorTest.java
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

## Run tests

From `backend/`:

```powershell
mvn test
```

Expected result:

```text
Failures: 0, Errors: 0, Skipped: 0
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

Each call to `generateTransaction()` returns one event with these demo values:

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
| 1 | Java transaction model, configurable simulator, console demo, HTTP preview, reliable build and tests (original Phase 1) | In progress |
| 2 | Kafka streaming, metric aggregation, PostgreSQL/TimescaleDB storage (original Phases 2–3) | Planned |
| 3 | Monitoring and anomaly detection (original Phase 4) | Planned |
| 4 | Predictive incident model and AI-assisted investigation (original Phases 5–6) | Planned |
| 5 | React UI, expanded observability and testing, Docker, AWS, and demo polish (original Phase 7 plus frontend) | Planned |

A React frontend is also planned for a later stage. Tests and documentation are added incrementally as the backend develops.

Each part is delivered as roughly six focused commits with README updates. Part 1 deliberately uses no Kafka, database, AI, authentication, Docker, or frontend dependencies.

### Part 1 checkpoints

1. Reliable Maven wrapper, explicit Spring Boot entry point, and five-part plan.
2. Validated simulation settings and named demo scenarios.
3. Repeatable simulation with injectable time and event IDs.
4. Configurable finite console runner with input validation and cancellation.
5. Bounded HTTP simulation preview with useful validation errors.
6. End-to-end verification and a reproducible demo guide.
