# Dashboard demo

The React UI has three views: payment health, recent transactions, and incident investigation. Its default source is a saved export from the Java demo. This makes the walkthrough available without running services or paying for backend hosting.

[Open the hosted demo](https://sentinelpay-monitor.w61983961.chatgpt.site). Anyone with the link can view the saved demo without signing in.

## Run locally

From the repository root, with Node.js 22.12 or newer:

```powershell
npm ci
npm run dev
```

Open http://127.0.0.1:5173. The saved scenario contains 520 attempts across six observed minutes, including 58 failures and one critical incident. The weighted average latency rounds to 577 ms. These are synthetic scenario results, not performance benchmarks.

For live local data, leave `backend/scripts/start-part4.ps1` running in another terminal and select **Live pipeline**. API requests go through Vite to the Java application on port 8082. A connection error stays visible; the UI does not silently replace failed live requests with demo data.

## Walkthrough

1. In **Overview**, switch between latency, failure rate, and volume. Expand **View chart data** for exact observations. Missing minutes remain gaps, and currencies are kept separate.
2. Compare the historical forecast, calculated before the spike, with the observed spike. Its linear fit describes the historical samples; it is not an incident probability.
3. In **Transactions**, filter by status or search a payment ID. The table contains the latest 100 returned attempts, so its filtered count differs from the overall metric total.
4. In **Incidents**, open the critical case. Read the observed values, thresholds, and investigation steps.
5. With **Live AI (Groq)** selected, ask a question about the case. The server sends its aggregate evidence and your question to the model and checks the returned citations. Switch to **Saved backend answers** to replay recorded Java answers without calling AI. Saved mode only supports its recorded questions.

## Refresh the saved data

```powershell
.\backend\scripts\export-dashboard.ps1
```

The exporter starts isolated local Kafka and PostgreSQL instances, generates the synthetic scenario, and writes `frontend/public/demo/snapshot.json`. It closes the temporary services afterward. IDs and timestamps change on each export. The exported values and answers come from the same backend used by the live local demo; no model is called during export.

## Checks

```powershell
npm test
npm run build
.\backend\scripts\verify-part4.ps1
```

Frontend tests cover weighted metrics, missing data, separate currencies, timeline gaps, case-specific saved answers, API errors, invalid responses, and request timeouts. Browser checks cover navigation, transaction filtering, saved questions, unsupported questions, and desktop/mobile layout.

The dashboard release passed 7 frontend tests and the full 113-test backend suite, with no failures or skipped backend tests. The production frontend build also passed. Browser checks confirmed that the local source can load existing backend data and leaves missing metric windows empty. Generating live payments from the dashboard was not part of this saved-demo validation.

The hosted build now includes a small AI server alongside the React assets. The Java pipeline remains local. Expanded observability and Docker verification remain open in Part 5; AWS remains planned for October. See [hosted AI](hosted-ai.md) for setup, limits, and evaluation details.
