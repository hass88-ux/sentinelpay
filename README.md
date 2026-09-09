# SentinelPay

SentinelPay is a Java-first project working toward predictive monitoring and incident intelligence for payment systems. It builds on my previous Transaction Anomaly Detection Engine by progressing from individual transaction analysis toward payment-system monitoring, anomaly detection, prediction, and AI-assisted incident investigation.

## Current progress

Phase 1 is in progress. The backend currently includes:

- A Spring Boot application that runs on port 8080.
- An immutable `TransactionEvent` record representing one completed simulated payment attempt.
- Validation of required fields, nonblank IDs, positive amounts, and nonnegative latency.
- A `TransactionStatus` enum with `SUCCESS` and `FAILED` outcomes.
- A plain Java `PaymentSimulator` that generates one event per method call.
- 11 JUnit tests: six model tests, four simulator tests, and one Spring application context test.

Continuous generation, event streaming, storage, monitoring metrics, detection, prediction, and AI explanations are planned features. The simulator is currently exercised through tests and is not connected to an HTTP endpoint or application startup.

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

The Maven wrapper is included, but its Windows script encountered a `Cannot index into a null array` error in the development environment. The commands documented here use installed Maven, which was used successfully for testing.

## Run tests

From `backend/`:

```powershell
mvn test
```

Expected result:

```text
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

To run only the simulator tests:

```powershell
mvn -Dtest=PaymentSimulatorTest test
```

Expect four passing tests. These check generated values and bounds, coverage of both statuses, repeatable random fields with a fixed seed, distinct event IDs, and rejection of a missing random generator. They do not measure throughput or establish production performance.

In Eclipse, import `backend/` as an existing Maven project using JDK 21. Refresh the project after external file changes. Right-click a test class and choose **Run As → JUnit Test**.

## Simulator behavior

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

## Roadmap

| Phase | Scope | Status |
| --- | --- | --- |
| 1 | Transaction domain model and Java payment simulator | In progress |
| 2 | Kafka event streaming | Planned |
| 3 | Metric aggregation and PostgreSQL/TimescaleDB storage | Planned |
| 4 | Anomaly detection | Planned |
| 5 | Predictive incident model | Planned |
| 6 | Incident intelligence and AI explanations | Planned |
| 7 | Expanded testing, observability, Docker, AWS, documentation, and demo polish | Planned |

A React frontend is also planned for a later stage. Tests and documentation are added incrementally as the backend develops.
