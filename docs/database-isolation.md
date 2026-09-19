# Database account isolation

The Java API verifies the Supabase token and passes its subject to the private upload store. Every file operation opens a transaction and sets `sentinelpay.owner_id` with `set_config(..., true)`. The setting lasts only for that transaction, including rollback. JDBC queries use the same transaction-bound connection.

PostgreSQL row-level security on `upload` and `upload_event` checks this owner for reads, deletes, and inserted rows. Missing or empty context sees no rows and cannot insert. Existing SQL owner predicates remain as a second check. Both tables enable and force row security; the deployed runtime role neither owns the tables nor has superuser or BYPASSRLS privileges.

This protects against accidentally omitted ownership filters. It does not protect against a compromised backend or someone holding its database credential: that trusted service can set the account context. Authentication, parameterized queries, and secret protection remain necessary. Request-limit counters are separate operational data, including one shared global counter, and are not covered by these file policies.

## Rollout order

1. Run `mvn -Dtest=PrivateUploadStoreTest,UploadApiTest test` inside `backend`.
2. Deploy the API containing transaction-local owner context. Keep `UPLOADS_MIGRATIONS_ENABLED=false` on Render.
3. After the deployment succeeds, run `scripts/provision-upload-runtime.ps1` locally. It applies V3 and verifies forced policies and that the runtime login sees no file rows without context.
4. Reopen a saved upload in the live app. Do not roll back to an API version without owner context after V3: it would fail closed and hide files. Repair forward with a compatible API.

The migration changes policies only; it does not delete or rewrite uploaded records. Tests use a restricted login and a single pooled connection, check unfiltered reads and cross-owner inserts/deletes, and verify that commit and rollback do not retain identity. Existing API tests also exercise signed-token authorization.

Reference: [PostgreSQL row security](https://www.postgresql.org/docs/current/ddl-rowsecurity.html).

## Verified deployment

The compatible API deployed successfully before V3 was applied to the hosted database. The provisioning check confirmed both tables enforce row security, the runtime role does not own them or bypass policies, and unscoped runtime reads return no rows. The signed-in second account then reopened its existing sample and saw the expected 180 payments, 12.8% failure rate, and 644 ms average latency. No upload data was rewritten.

All seven restricted-role storage tests and six signed-token API tests passed. This change did not rerun the unrelated streaming regression suite. The frontend was unchanged.
