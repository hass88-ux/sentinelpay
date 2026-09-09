# Part 1 demo guide

This checkpoint demonstrates a Java payment-event generator and an HTTP preview. It does not yet monitor real payments or detect incidents.

## 1. Verify the checkpoint

Open PowerShell in the repository root:

```powershell
cd backend
.\scripts\verify-part1.ps1
```

This runs Maven tests and packaging, checks the default console demo, starts the packaged server on a dynamically assigned loopback port, checks health and the HTTP preview, and shuts down only the server it started. Logs remain under the ignored `target/` directory. It does not stop an existing server on port 8080. JDK 21 is required; the wrapper downloads Maven/dependencies if not cached.

If your PowerShell policy blocks local scripts, use the manual commands below or run the script through an environment allowed by your policy. There is no need to change a machine-wide policy.

## 2. Show generated events

```powershell
.\mvnw.cmd compile
java -cp target/classes com.sentinelpay.backend.simulator.PaymentSimulatorDemo --count=10 --scenario=DEGRADED --seed=42 --interval-ms=100
```

Expect ten event lines and a normal exit. Repeating the command keeps amounts, statuses, and simulated latency the same, while IDs and timestamps change. Change the scenario to `OUTAGE` to sample a lower success probability and higher latency range. These are synthetic settings, not conclusions produced by a detector.

## 3. Show the HTTP preview

In one terminal, from `backend/`:

```powershell
.\mvnw.cmd verify
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

The server starts on port 8080. If it is occupied, pass `--server.port=8081` and adjust the URLs below. Use `Ctrl+C` to stop it when finished.

In a second terminal:

```powershell
Invoke-RestMethod 'http://localhost:8080/actuator/health'
Invoke-RestMethod 'http://localhost:8080/api/simulator/transactions?count=5&scenario=OUTAGE&seed=42' | ConvertTo-Json -Depth 5
```

Expect `UP` from health and a JSON object containing `scenario`, `count`, and five `events` from the preview.

To demonstrate validation, request `count=101`. PowerShell reports HTTP 400. The response body is a problem document with a detail explaining the 1–100 limit. This avoids allocating an arbitrarily large response based on user input.

## 4. Explain the architecture

- `TransactionEvent` is an immutable record. It validates required values and numeric bounds at construction, so invalid events cannot be created through the normal constructor.
- `BigDecimal` represents the amount. The generator samples integer cents before conversion, avoiding binary floating-point arithmetic for money.
- `SimulationSettings` is an immutable set of inclusive ranges and a success probability. `SimulationScenario` supplies three example configurations.
- `PaymentSimulator` produces one event. It knows nothing about HTTP, Spring, databases, or Kafka. A random source, clock, and ID supplier make its behavior controllable in tests.
- `SimulationRunner` controls a finite sequence and optional spacing. It passes each event to a `Consumer<TransactionEvent>` so the console path does not retain a growing list.
- `SimulationController` creates a separate simulator for each request and returns at most 100 events. Request-local state keeps one client's seed from affecting another client.
- `SimulationErrorHandler` converts invalid preview inputs to structured HTTP 400 responses. The executable JAR always starts `BackendApplication`, even though the console demo also has a `main()` method.

## Limits to state honestly

No transaction history, live payment integration, Kafka pipeline, storage, anomaly detector, prediction model, AI explanation, UI, authentication, or production deployment exists yet. There is no throughput or accuracy benchmark. Independent random samples do not model correlated real-world outages. The preview has a per-request size limit but no rate limiting or authentication; it is intended for development use.

Part 2 will connect event generation to Kafka and storage. Part 1 supplies the tested event model and generator that work will build on.
