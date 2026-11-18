# Vercel deployment

The Vercel configuration is ready in the repository; production deployment is still pending. The local build and 65 automated tests passed. Vercel account authorization, server environment configuration, Supabase redirect setup, and live sign-in/upload verification still need to be completed.

Once deployed, Vercel will serve the React dashboard and Node API routes. The Java upload service remains on Render, and Supabase continues to provide Google sign-in and PostgreSQL storage. This does not move Kafka or Java into a Vercel function.

## Build and configuration

Use the repository root, the Other framework preset, `npm run build:vercel`, and output directory `dist/client`. `vercel.json` supplies these settings. The existing `npm run build` still packages the OpenAI Sites deployment.

Production environment variables:

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`
- `UPLOADS_API_URL` — the existing HTTPS Render origin
- `GROQ_API_KEY` — server secret; never use a `VITE_` prefix
- `GOOGLE_AUTH_READY=true`
- `EMAIL_AUTH_READY=false`

After signing into the Vercel CLI and linking the project, `scripts/configure-vercel.ps1` transfers the existing account configuration and locally encrypted Groq key using stdin. It does not print secrets, copy the database password, or configure preview environments. The `.vercelignore` file excludes local encrypted credentials, backend runtime data, and build archives.

## Upload routing

The browser uses same-origin `/api/uploads` routes. The Vercel gateway forwards only the bearer token and content type to the configured Render origin, refuses redirects, and never caches private responses. Java still authenticates the token and enforces account ownership, database isolation, quotas, and CSV validation. This allows the existing site's CORS configuration to stay unchanged.

AI questions use the existing evidence-validation handlers on Vercel. Private questions still require consent and owner-authorized aggregate retrieval from Java. Function duration is configured to cover the existing upload-service and provider deadlines; hosting quotas and cold starts still apply.

## Google sign-in

Add the exact production `https://…vercel.app/?account=1` URL to Supabase Authentication → URL Configuration → Redirect URLs. Keep the existing site's redirect while both sites are active. The Google OAuth provider callback stays the same Supabase callback URL. Avoid allowing arbitrary Vercel preview domains for private accounts.

## Verify

Run `npm test` and `npm run build:vercel`. After publishing, check the overview, privacy/terms pages, `/api/account-config`, demo AI, Google sign-in, and a synthetic CSV upload. Confirm unauthenticated upload requests return 401. Do not call migration complete until sign-in redirects and private uploads have been checked on the new origin.

The existing Sites URL remains available during migration. Roll back the Vercel deployment through its dashboard if necessary; no database migration is needed.
