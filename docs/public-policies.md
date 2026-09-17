# SentinelPay public policies

Approved policy text. The site build publishes the two sections below as standalone pages.

## Privacy policy

SentinelPay is a payment-monitoring portfolio application. It lets you sign in, save payment transaction CSVs, and review metrics and threshold checks. Use synthetic or de-identified test data. Do not upload card numbers, bank account details, customer names, email addresses, or other sensitive personal information.

### Account information

Google sign-in is handled through Supabase Authentication. Google provides basic identity information, such as your email address, name, profile image, and Google account identifier, according to the permissions shown during sign-in. Supabase uses this information to create and manage your account. SentinelPay uses the authenticated account identifier to associate saved uploads with your account and displays your email address when signed in. SentinelPay does not request access to Gmail, Google Drive, or your Google password.

### Uploaded data

The Java API receives the CSV you select. It validates transaction identifiers, timestamps, amounts, currencies, payment outcomes, and latencies. It stores parsed transaction records and file metadata in PostgreSQL hosted by Supabase, and calculates metrics and threshold checks. Normal application requests are restricted to the signed-in account's uploads. The application operator and infrastructure providers may have administrative access needed to operate the service.

### AI explanations

The demo's AI feature sends your question and the selected synthetic incident's aggregate evidence to Groq. Do not enter personal or confidential information in questions. Private uploaded transactions are not currently sent to Groq. SentinelPay does not save a chat history, but the AI provider processes requests under its own policies. AI answers can be incorrect and do not establish a root cause.

### Service providers and browser storage

Google and Supabase provide authentication. Render hosts the Java API, Supabase hosts the database, and OpenAI Sites provides the dashboard hosting. Groq processes demo AI questions. These providers process information needed to deliver their services and may retain operational or security logs under their own policies. The dashboard also loads fonts from Google Fonts.

The dashboard keeps authentication session information in browser session storage to maintain sign-in in the current tab. It does not implement advertising trackers. Hosting and authentication services may use their own necessary storage and logs.

### Retention and deletion

Saved uploads remain until you delete them; automatic expiry is not implemented. The Delete action removes the saved file's metadata and parsed transactions from the application's active database. It does not promise immediate removal from provider backups or operational logs. Deleting an upload does not delete your sign-in account. Contact the operator to request account deletion or ask about your data.

### Contact

Support and privacy contact: muhammadhassanamir888@gmail.com.

## Terms of use

SentinelPay is a portfolio demonstration for inspecting payment-system telemetry. It does not process payments or move money. Use synthetic or de-identified data that you have permission to upload. Do not upload cardholder data, account credentials, personal customer information, or confidential production records.

Do not try to access another user's files, bypass access controls, or disrupt the service. Keep your Google account secure and sign out when using a shared device.

Metrics, threshold checks, forecasts, and AI explanations are aids to investigation. They are not guarantees of payment-system health, confirmed diagnoses, or financial advice. Independently verify results before relying on them for operational decisions.

This demonstration uses limited hosting resources. Service availability and data retention are not guaranteed; keep your own copy of any file you need. Current upload limits are 2 MiB per CSV, 5,000 transactions per file, and 20 saved files per account.

You can delete saved uploads in My uploads. For account deletion, privacy questions, or problems with the service, contact muhammadhassanamir888@gmail.com.
