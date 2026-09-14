# Live incident questions

Open the public dashboard, select **Incidents**, open the case, and choose **Live AI (Groq)** under **Answer source**. Ask why the case was flagged, what changed before the spike, or what to investigate next. Answers show their evidence references and a warning that generated conclusions may be wrong.

## How it works

The Java simulator, Kafka pipeline, PostgreSQL metrics, detection rules, and reports remain the source of the demo evidence. A small JavaScript server on the website's host accepts a case ID and a question. It loads that case from the server's exported dataset and calls Groq's `openai/gpt-oss-20b` model. The browser cannot replace the evidence or choose another model or provider endpoint.

This server exists because the public demo host does not run the Java pipeline. It does not implement payment detection or forecasting. Local Java users still have the existing Ollama adapter; Groq support here does not enable Groq in the Java backend.

Only aggregate evidence, observation timestamps/counts/latency, investigation checks, and the question go to Groq. Raw payment IDs and amounts are excluded. Questions and generated answers are not persisted by SentinelPay. Groq processes the submitted content under its own policies; do not enter personal or confidential data.

## Controls and limitations

- The API key is a server secret, never a React environment variable or part of the shipped browser code.
- Questions are limited to 500 characters; request bodies to 4 KiB and model responses to 64 KiB.
- Requests have a 25-second deadline. The server does not automatically retry provider requests.
- Responses must match a JSON schema, fit output bounds, and cite rules that actually exist in the case. This checks structure and references, not factual correctness.
- Generated text is rendered as text. It cannot execute commands, browse, or modify payment data.
- The endpoint checks the requesting origin and applies a best-effort limit of 3 questions per visitor per minute and 12 total per minute **per server instance**. These are not global or billing caps; independent instances can exceed them collectively. Provider quotas are the account-wide limit. An origin check alone is not authentication.
- The site reports provider failures, timeouts, and exhausted quotas. Saved backend answers remain an explicit alternative; they are not labeled AI.

Keep the provider account on the free plan if you require no paid API usage. Free quotas are shared across the account and may change. Consult [Groq rate limits](https://console.groq.com/docs/rate-limits) and [structured outputs](https://console.groq.com/docs/structured-outputs).

## Key setup and evaluation

Create a dedicated API key, then run `scripts/set-groq-key.ps1` on Windows. The prompt saves a DPAPI-encrypted file in ignored `.sites-runtime/`, readable by the same Windows user. Configure its value as the hosting secret `GROQ_API_KEY`; redeploy to apply it. Do not put it in a `VITE_` variable or commit it. Key rotation means replacing the provider key and hosting secret.

From the repository root:

```powershell
npm test
npm run build
node scripts/check-build.mjs
.\scripts\evaluate-ai.ps1
```

The last command makes real model calls using the encrypted local key. It evaluates three prompts: explaining an alert/root-cause uncertainty, comparing the minutes before the spike, and resisting a request to invent a named provider and supporting logs. It writes the responses to ignored `.sites-runtime/ai-evaluation.json` for manual review. It consumes the provider's quota and pauses between calls.

These are a small demonstration sample, not an accuracy benchmark or a comprehensive prompt-injection evaluation. Review the numbers, time windows, citations, and causal language. A schema-valid answer can still be misleading; the Java evidence remains visible for comparison.

In the initial live review, the model preserved root-cause uncertainty, correctly compared pre-spike latency of 200–800 ms and failure rates of 0–16%, and did not follow the request to fabricate a provider and logs. Review also caught imprecise wording around volume thresholds, so the prompt now explains that volume breaches a lower threshold and bucket timestamps mark minute starts. This does not guarantee that future wording will be correct.
