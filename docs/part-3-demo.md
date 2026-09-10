# Part 3: explainable monitoring

This part evaluates stored minute/currency metrics and retains findings in
PostgreSQL. It adds no machine learning or AI claims.

## Start the updated application

If the old local pipeline is running, press Enter in its terminal to stop it
cleanly. Then run:

```powershell
cd C:\Users\hassa\sentinelpay\backend
.\scripts\start-part2.ps1
```

The launcher name is unchanged: it now includes Part 3. Leave that terminal open.
Existing Kafka/PostgreSQL data is retained and Flyway applies migration V2.
In Eclipse, refresh the backend project with F5 to see the new `monitoring`
package and tests. The launcher uses test dependencies for local infrastructure.

## Generate a detectable sample

In a second PowerShell window:

```powershell
$batch = Invoke-RestMethod -Method Post 'http://localhost:8081/api/pipeline/simulations?count=100&scenario=OUTAGE&seed=42'
$batch
```

Expect `ACKNOWLEDGED` and 100 IDs. This acknowledges Kafka delivery; storage
finishes asynchronously. A new request creates new payments.

Wait 100 seconds so the payment minute closes, the ten-second grace period
passes, and an automatic scan can run:

```powershell
Start-Sleep -Seconds 100
Invoke-RestMethod -Method Post 'http://localhost:8081/api/monitoring/runs' | ConvertTo-Json -Depth 5
Invoke-RestMethod 'http://localhost:8081/api/monitoring/findings' | ConvertTo-Json -Depth 5
Invoke-RestMethod 'http://localhost:8081/api/monitoring/status' | ConvertTo-Json -Depth 5
```

For an otherwise quiet minute, the OUTAGE sample should produce failure-rate and
average-latency findings. If traffic shares that minute, rules evaluate the
combined totals. Exact timestamps and IDs vary. A batch crossing a minute boundary
is evaluated as separate samples. Small samples below 20 do not trigger those two
rules. Volume needs history and will initially be insufficient.

`COMPLETED` means the scan ran, not that all traffic is healthy. `NO_DATA` means
there were no eligible observed minutes in the scan window. The run response
reports observed minute/currency pairs, alert evaluations, and insufficient-data
evaluations. `BUSY` with HTTP 409 means another instance holds the scan lock.
`FAILED` in status records a failed scan and retains the previous successful run.
Status is process-local and resets on restart; findings are persistent.

## Understand a finding

Each result includes the bucket, currency, rule, state, sample count, observed
value, thresholds, explanation, first detection time, and latest evaluation time.

| Rule | Warning | Critical | Evidence needed |
| --- | --- | --- | --- |
| Failure rate | At least 20% | At least 50% | 20 payments in the bucket |
| Average latency | At least 1000 ms | At least 2000 ms | 20 payments in the bucket |
| Volume drop | At most 50% of baseline | At most 20% of baseline | Five qualifying baseline minutes |

Failure percentage is classified before display rounding. Average latency uses
the existing metric's three-decimal millisecond precision. The volume baseline is
the median of observed same-currency minutes in the preceding ten minutes, each
with at least 20 payments. Current/future minutes are excluded from its baseline.
The median reduces the influence of one unusually large baseline minute.

The scheduler waits five seconds at startup, then 30 seconds after each scan
finishes. It rechecks the latest 30 eligible completed minutes, fetching ten extra
minutes for baselines. Late arrivals within that window can change the result.
Evaluations are upserted by bucket/currency/rule, so repeated scans do not duplicate
findings. A transaction-scoped advisory lock serializes scans across instances.
All rules in a scan use the same committed metric snapshot.

To see findings that subsequently became normal:

```powershell
Invoke-RestMethod 'http://localhost:8081/api/monitoring/findings?includeResolved=true&limit=100' | ConvertTo-Json -Depth 5
```

Queries default to 50 results and allow 1–100, newest bucket first. The default
query includes only warning/critical results. These describe historical minutes;
an old critical finding is not evidence of a currently active incident. Compare
the bucket and evaluation timestamps. Normal evaluations that never alerted are
not returned as findings.

## Verify automatically

```powershell
cd C:\Users\hassa\sentinelpay\backend
.\scripts\verify-part3.ps1
```

The script runs the full JUnit suite and packages the JAR. It uses real PostgreSQL
and Kafka on random ports in isolated directories under ignored `target/`. It does
not stop the application you have running on port 8081. Expected final output:
`BUILD SUCCESS` followed by `Part 3 verification passed` and the check summary.

The monitoring integration test sends five baseline minutes of 100 successful
payments, followed by 20 payments with 18 failures and 2500 ms latency. It waits
for automatic detection of three critical findings. Eighty late successful
payments reduce failure rate to 18%, average latency to 580 ms, and restore volume
to 100. All three findings become normal; reruns do not duplicate them, and a
Kafka/PostgreSQL/application restart preserves the corrected history.

Other checks cover threshold boundaries, insufficient samples/history, currency
isolation, old/future baseline exclusion, grace-period boundaries, concurrent
scan contention, invalid API limits, and scan failure/recovery.

## Limits and next part

- Thresholds are explicit demo choices, not calibrated operating limits.
- Missing minutes are unknown, not zero-volume observations. Complete feed silence
  needs a separate heartbeat/expected-traffic design.
- Corrections older than the 30-minute rescan window require a future backfill
  feature. Old findings are retained; automatic retention is not implemented.
- Findings are per-rule observations. Incident grouping, forecasting and AI
  explanations belong to Part 4.
- Monitoring is enabled only in the `pipeline` profile. Set
  `monitoring.scheduling-enabled=false` to run scans manually.
- This remains a local development system; deployment and the frontend belong to
  Part 5. These tests are functional evidence, not throughput or accuracy benchmarks.
