# Part 4: forecasts and incident investigation

## Start the reproducible demo

```powershell
cd C:\Users\hassa\sentinelpay\backend
.\scripts\start-part4.ps1
```

This starts real Kafka, PostgreSQL, and SentinelPay on port **8082**, with a fresh
temporary database under `backend/target/part4-demo-*`. It does not modify the
persistent pipeline data in `backend/.local/`. Use `-Port 8083` if 8082 is occupied.
Leave the terminal open; press Enter to stop its services cleanly. Each demo launch
starts fresh. For the persistent pipeline on 8081, restart `start-part2.ps1` instead.

The demo sends 520 synthetic payments through Kafka, waits for storage, and runs
monitoring. It prints two URLs: a historical forecast and an incident report.
Open those exact URLs in the browser. They return JSON; the frontend is Part 5.

In a second PowerShell terminal:

```powershell
$cases = @(Invoke-RestMethod 'http://localhost:8082/api/intelligence/incidents')
$caseId = $cases[0].id
$report = Invoke-RestMethod "http://localhost:8082/api/intelligence/incidents/$caseId"
$report | ConvertTo-Json -Depth 12
Invoke-RestMethod -Method Post "http://localhost:8082/api/intelligence/incidents/$caseId/explanation" | ConvertTo-Json -Depth 12
```

Expect one `ALERT` case with `CRITICAL` severity and three pieces of rule evidence.
Its hypotheses reference those rules, and its checks suggest what to inspect next.
Root cause remains unknown. By default, the explanation endpoint returns
`mode: DETERMINISTIC` and an AI-disabled notice; no model is required.

## What the fixture demonstrates

| Minute | Count | Failure rate | Average latency |
| --- | --- | --- | --- |
| 1 | 100 | 0% | 200 ms |
| 2 | 100 | 4% | 350 ms |
| 3 | 100 | 8% | 500 ms |
| 4 | 100 | 12% | 650 ms |
| 5 | 100 | 16% | 800 ms |
| 6 | 20 | 90% | 2500 ms |

The printed forecast URL uses `asOf` minute 5. With only minutes 1–5 as inputs, the
three-minute linear projection is **28% failures and 1250 ms latency**, both above
their warning limits. `THRESHOLD_RISK` describes this extrapolation. Minute 6 is
excluded from those inputs, even though it is already stored. These numbers are
arithmetic results for a designed fixture, not measured predictive accuracy.

Minute 6 triggers failure-rate, latency, and volume-drop rules. Volume is 20% of
the preceding five-minute median of 100. The three findings become one case for
that minute and currency. Forecasts inside the minute-6 case are anchored to
minute 6 and may say `ALREADY_ELEVATED`; use the separate printed URL to see the
earlier projection.

The fixture is historical and does not wait six real minutes. A fresh current-time
forecast query may return `INSUFFICIENT_DATA` because there is no continuous producer.

## Forecast behavior and boundaries

`GET /api/intelligence/forecasts?currency=USD` uses the latest completed minute after
the monitoring grace period. Optional `asOf` accepts an aligned UTC minute within
the last 24 hours, never a future/current incomplete minute.

The model uses ordinary least-squares regression over five consecutive observations
for one currency. Every minute needs at least 20 payments. It extrapolates three
minutes, bounds failure percentage to 0–100 and latency to nonnegative values, and
returns the slope and historical R-squared fit. It does not train a classifier or
estimate an incident probability.

| State | Meaning |
| --- | --- |
| `INSUFFICIENT_DATA` | A required minute is missing or has too few payments |
| `ALREADY_ELEVATED` | Latest observed value already meets the warning threshold |
| `UNSTABLE_TREND` | Historical R-squared is below the illustrative 0.6 fit gate |
| `THRESHOLD_RISK` | A rising fitted trend projects a warning-threshold crossing |
| `NO_CROSSING_PROJECTED` | The fitted trend does not project a crossing |

R-squared is not confidence. Five points cannot establish model reliability, and
a changing trend can invalidate the projection. Production calibration, labeled
incident data, forecast backtesting, seasonality, and missing-feed detection remain
future work. Historical queries use currently stored corrections and do not
reconstruct what was known at the original ingestion time.

## Cases, evidence, and corrections

Migration V3 creates cases and backfills previously recorded Part 3 findings.
Subsequent case updates commit atomically with monitoring evaluations. A stable
case ID groups one minute/currency, preserving first detection time. It does not
merge adjacent minutes into one outage or infer processor/merchant identity.

- `ALERT`: at least one current rule evaluation warns or is critical.
- `CLEARED`: all previously alerting rules are now normal.
- `UNDETERMINED`: no rule currently alerts, but a formerly alerting rule lacks data.

An insufficient volume baseline that never alerted does not prevent a corrected
failure/latency case from clearing. A historical alert does not prove an incident
is ongoing now. Read the bucket and evaluation timestamps.

List requests default to 50 results, bounded to 1–100. Include cleared cases with:

```powershell
Invoke-RestMethod 'http://localhost:8082/api/intelligence/incidents?includeCleared=true&limit=100' | ConvertTo-Json -Depth 8
```

Reports contain deterministic summaries, rule evidence, forecasts, hypotheses
citing rule names, suggested investigation checks, and limitations. Evidence is
read with the case in a repeatable-read database transaction. Forecasts use current
stored metrics; pending late arrivals can make them newer than the recorded rule
evaluation. Rerun `POST /api/monitoring/runs` to apply eligible corrections. The
existing 30-minute rescan limit still applies.

## Optional AI narration

The adapter targets Ollama's [generate API](https://docs.ollama.com/api/generate),
using nonstreaming JSON. Ollama is not installed or downloaded by SentinelPay.
Use a local model already available on your machine; inspect names with `ollama list`.
Configure Ollama's server with `OLLAMA_NO_CLOUD=1` and restart it to disable cloud
forwarding, as described in the [Ollama FAQ](https://docs.ollama.com/faq).

In the PowerShell window that will launch SentinelPay, set:

```powershell
$env:SENTINELPAY_AI_ENABLED = 'true'
$env:SENTINELPAY_AI_MODEL = 'your-installed-local-model-name'
.\scripts\start-part4.ps1
```

Replace the model placeholder with an installed local model name. The adapter calls
only `http://127.0.0.1:11434/api/generate` and follows no redirects. A loopback URL
alone does not guarantee local inference if the Ollama server allows cloud models.

`POST /api/intelligence/incidents/{id}/explanation` returns `AI_ASSISTED` when valid
model suggestions are available. The original report remains a separate field.
Prompts contain aggregate rule evidence and forecasts, not raw payment IDs or
amounts. Suggestions never change case state, metrics, or forecasts, and are never
executed as commands.

The client allows one in-flight request per application process, times out after
30 seconds, and cancels responses exceeding 64 KiB. It validates text sizes,
structure, and cited rule names. This does **not** verify the truth of generated
claims. Disabled AI, provider errors, timeout, busy requests, malformed output,
unknown citations, or excessive output fall back to the deterministic report.
Cancelling the client request does not guarantee that server-side inference stops.
Cold model startup may exceed the deadline; deterministic reporting still works.

No live model was used during this checkpoint. HTTP contract and failure
behavior are tested against a local stub; actual narration quality and hardware
latency remain unverified. Generated explanations are returned on demand and are
not persisted as an audit history.

## Ask about a case

With the demo running and `$caseId` set as above:

```powershell
$body = @{ question = 'What changed before the spike?' } | ConvertTo-Json
Invoke-RestMethod -Method Post "http://localhost:8082/api/intelligence/incidents/$caseId/questions" -ContentType 'application/json' -Body $body | ConvertTo-Json -Depth 12
```

Try `Why was this flagged?` or `What should I investigate next?` as well. The
response includes the answer, mode, evidence references, recent observations, and
the underlying report. Questions must contain 1–500 characters. Requests are
independent: no conversation history is stored or sent, and the endpoint cannot
retrieve other cases based on the text of a question.

When AI is disabled or unavailable, basic questions receive a deterministic answer.
Comparisons about before the incident exclude the incident minute itself. Unknown
causes and unsupported questions are identified as such. With AI enabled, the
question is passed as untrusted input alongside case evidence. References are
validated, but that does not guarantee the model's claims are correct.

The React question interface and free-hosted demo are planned next; AWS deployment
is deferred until October. The current provider remains local Ollama.

## Verification

```powershell
cd C:\Users\hassa\sentinelpay\backend
.\scripts\verify-part4.ps1
```

Expect `BUILD SUCCESS` and `Part 4 verification passed`. The suite checks regression
arithmetic and input boundaries, database upgrade/backfill, stable case identity,
late correction and restart, evidence references, AI response limits and fallback,
and real Kafka-to-HTTP forecast/report behavior. Tests disable real AI requests,
even if your normal application environment enables them.

Refresh Eclipse with F5 to see `incident` under both main and test sources. All
code remains Java 21; no AI framework or additional production dependency was added.
