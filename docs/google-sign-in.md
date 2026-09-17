# Google sign-in

The dashboard uses Supabase's Google provider with PKCE. The SDK stores the verifier in session storage, redirects through Supabase and Google, exchanges the returned code, and issues the same Supabase access token that the Java upload API already verifies. No Google API token is sent to Java. Only the standard identity scopes (openid, email, profile) are needed; never request Gmail or Drive access.

## Provider setup

In Google Cloud, select or create the SentinelPay project. Configure Google Auth Platform branding and an external audience. While the app is in Testing, add the intended test accounts. Publish the OAuth app for public users when ready, following any requirements shown by Google. Do not claim public access based only on a test-user login.

Create a **Web application** OAuth client with:

- JavaScript origin: `https://sentinelpay-monitor.w61983961.chatgpt.site`
- Authorized redirect URI: `https://bsbhfwnmzsvbvnnivpsl.supabase.co/auth/v1/callback`

Copy the client ID and secret into **Supabase > Authentication > Sign In / Providers > Google**, and enable the provider. The secret belongs only in Supabase; never put it in Git, the dashboard bundle, or chat. Keep nonce checking enabled.

In Supabase Auth URL Configuration:

- Site URL: `https://sentinelpay-monitor.w61983961.chatgpt.site`
- Allowed redirect: `https://sentinelpay-monitor.w61983961.chatgpt.site/?account=1`

These are two different redirects: Google returns to Supabase; Supabase returns to the dashboard. Configure exact URLs rather than broad wildcards. For a local test, separately allow `http://localhost:5173/?account=1` in Supabase.

Only set the Sites runtime variable `GOOGLE_AUTH_READY=true` after the provider is configured. Until then, the button is disabled with a setup message. Set it back to false if provider configuration becomes unavailable. Email/password registration remains independently controlled by `EMAIL_AUTH_READY`.

## Acceptance check

Use the same browser tab for the round trip so its PKCE verifier is available. Check a successful login, a cancelled login, sign-out, CSV import and refresh, and isolation between two Google accounts. Ensure an account outside Google's test-user list can sign in before announcing open registration. Free Render instances may take time to wake up; retrying a read is safe, but check the saved-file list before retrying an upload whose result was interrupted.

Reference: [Supabase Google authentication](https://supabase.com/docs/guides/auth/social-login/auth-google).
