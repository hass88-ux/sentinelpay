# Private accounts and uploads

The `uploads` Spring profile runs a separate HTTP API without Kafka. CSV imports are finite batches: validating and saving them in one database transaction keeps a bad row from leaving a partial file. The local streaming demo continues to use Kafka and its own tables.

Supabase handles email/password accounts. Java verifies ES256 access-token signatures against the project's public signing keys, plus issuer, expiration, audience, authenticated role, and UUID subject. Anonymous sessions are rejected. Every storage operation uses that verified subject as the owner. The browser cannot choose an owner. Authentication uses bearer tokens, not cookies; no CSRF cookie session is enabled. Only the configured dashboard origin gets CORS access.

Each account may save 20 CSV files, each with at most 5,000 rows and 2 MiB of input. Imports are atomic. Owners can list files, read minute/currency metrics and threshold checks, and permanently delete a file with its rows. Other owners receive 404 for the same ID. Uploads are never passed to the public demo AI endpoint. A file with too few samples displays insufficient data instead of a healthy result.

Browser sessions are kept in session storage for the tab; payment records are stored in PostgreSQL. Sign-out clears the displayed account data. Confirmation and password-reset links return to `/?account=1`.

## Local run

Save Supabase configuration using `scripts/set-supabase-config.ps1`. The repository includes the public database CA certificate; it contains no private key. The connection uses hostname and certificate verification.

```powershell
cd backend
.\mvnw.cmd package
cd ..
.\scripts\start-uploads.ps1
```

In another terminal, run `npm run dev` from the repository root and open `http://localhost:5173`. Choose **My uploads**. The Vite proxy sends private requests to port 8083 and existing demo pipeline requests to 8082.

## Hosting setup

`render.yaml` defines a free Docker web service, using the Java 21 image built from `backend/Dockerfile`. Supply `UPLOADS_DB_PASSWORD` as a Render secret. The database URL includes the CA certificate path. Do not commit passwords or place them in the browser build.

The Sites server needs these runtime values:

- `SUPABASE_URL`: project URL.
- `SUPABASE_PUBLISHABLE_KEY`: public browser key, never a secret/service-role key.
- `UPLOADS_API_URL`: the deployed HTTPS Render origin.
- `EMAIL_AUTH_READY`: set to `true` only after confirmation and password-reset email delivery work. Until then, the site offers existing-account sign-in and explains that registration is not ready.
- `GOOGLE_AUTH_READY`: set to `true` after the [Google provider setup](google-sign-in.md). Google accounts can then register without a custom email sender.

The dashboard account configuration stays disabled until all three exist. Its CSP permits connections only to itself and those configured origins. Render accepts the dashboard origin through `ALLOWED_ORIGIN`. The hosted upload profile denies access to local simulator and pipeline endpoints.

In Supabase Auth URL Configuration, set the public site URL and allow the exact `https://sentinelpay-monitor.w61983961.chatgpt.site/?account=1` redirect. Add `http://localhost:5173/?account=1` for local development. Keep email confirmation enabled. Public confirmation and recovery emails require [custom SMTP](https://supabase.com/docs/guides/auth/auth-smtp); the default sender only serves project-team addresses. Verify signup, confirmation, password reset, and a real login before announcing public registration.

## Validation and limits

`UploadApiTest` verifies real signed ES256 tokens against a local JWKS endpoint and uses real PostgreSQL. It checks expired tokens, incorrect issuer/audience, forged signatures, cross-owner read/delete, invalid CSV rollback, CORS, and denied simulator access. Store tests cover quotas and atomic writes; parser tests cover the file contract.

This remains a portfolio deployment: Render may sleep, free database quotas apply, and there is no automatic retention or per-account request-rate limiter yet. The runtime currently uses the configured database role; it should be narrowed to an application-specific role before accepting sensitive production data. Do not upload cardholder data. Upload metrics use demo thresholds and are not a trained predictive model. Live AI still explains only the synthetic demo case.

## Deployment status

The Java upload service is deployed at `https://sentinelpay-api-xoln.onrender.com` on Render's free plan. Its health check returned UP and anonymous upload requests returned 401. Google sign-in is enabled for the configured test account. The user completed login and uploaded a synthetic CSV; browser checks confirmed the saved file survives a full reload and reopens successfully.

The sample contains 180 payments, 23 failures, and eight observed minutes. The dashboard shows a rounded 12.8% failure rate and 644 ms weighted average latency. Its minute-level chart values match the CSV: five normal minutes, a warning minute, a critical failure/latency minute, and a final critical volume drop. The five-payment final minute correctly shows insufficient data for failure and latency checks. Live Groq also returned an explanation for the separate synthetic demo incident.

Open registration, a second real Google account, cancellation, and sign-out/re-login still need live acceptance checks. Google remains in Testing pending branding and publishing requirements. Two observed UI issues remain: My uploads inherits the Saved demo label, and the file list briefly shows No files yet while the initial request is loading.

The full Java regression run passed 142 tests with no failures, errors, or skips, covering CSV validation, private storage, API authorization, and the monitoring pipeline. Frontend and server unit tests passed 30 tests. Live browser acceptance covers the saved upload and demo AI checks above; it does not replace cross-account security tests or establish production readiness.
