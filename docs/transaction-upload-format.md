# Transaction CSV format

Private uploads are under development. The Java parser is implemented and tested; the public site does not accept user uploads yet. Authentication, isolated storage, and the hosted Java API must be connected before uploads are enabled.

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

The two example rows show the format only. They are not enough to satisfy the monitoring rules' sample requirements or build a five-minute forecast. A successful import must not imply that enough evidence exists for a useful analysis.

Run the parser tests from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=TransactionCsvReaderTest' test
```
