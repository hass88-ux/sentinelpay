# Transaction CSV format

Private uploads are under development. The Java parser and owner-scoped storage are implemented and tested; the public site does not accept user uploads yet. Authentication and the hosted Java API must be connected before uploads are enabled.

Use a UTF-8 CSV file with this exact header:

```csv
id,timestamp,amount,currency,status,latencyMs
pay-1,2026-01-01T12:00:00Z,19.99,USD,SUCCESS,120
pay-2,2026-01-01T12:00:01Z,8.50,USD,FAILED,950
```

- Maximum file size: 2 MiB. Maximum transactions: 5,000 per file.
- IDs must be unique within the file, contain 1–128 characters, and have no control characters.
- Timestamps must include a timezone and be from 1970 to before 2200.
- Amounts must be positive plain decimals, with at most 12 whole digits and 6 fractional digits. Exponents and thousands separators are not accepted.
- Currency codes must be valid uppercase codes such as USD or EUR. Different currencies are not converted or combined.
- Status must be SUCCESS or FAILED. Latency is a non-negative whole number of milliseconds within the Java `long` range.
- Quoted fields, escaped double quotes, UTF-8 BOM, and Windows line endings are supported. Embedded line breaks inside fields are not supported because none of these six fields needs them.
- Extra columns and blank records are rejected. Do not add card numbers, names, email addresses, or other personal information.

The parser validates the entire file before returning transactions. Errors include the CSV row number and do not echo the uploaded values. It does not write to a database or publish events itself.

## Private storage

`PrivateUploadStore` uses a separate `sentinelpay_private` database schema. Every list, lookup, metric query, and deletion includes the authenticated owner's ID. Import metadata and all rows are saved in one database transaction; a failed insert rolls everything back. Deleting an upload removes its transactions. Each owner can save up to 20 files; a database transaction lock protects the quota check against concurrent requests.

The owner must come from a verified login token, never from a submitted form field. The store is not currently exposed by a web controller. Its tests use real PostgreSQL and check cross-user isolation, per-file/currency metrics, atomic rollback, deletion, and quotas. The migration lives under `db/uploads` and is separate from the demo pipeline migrations.

Supabase setup uses `scripts/set-supabase-config.ps1`. It stores the publishable key and database details in a Windows DPAPI-encrypted file under ignored `.sites-runtime/`. No service-role key is needed for the browser. Use Supabase's session pooler connection for the Java service; the pooler supports IPv4.

The two example rows show the format only. They are not enough to satisfy the monitoring rules' sample requirements or build a five-minute forecast. A successful import must not imply that enough evidence exists for a useful analysis.

Run the parser tests from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=TransactionCsvReaderTest' test
```
