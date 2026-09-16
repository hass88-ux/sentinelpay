package com.sentinelpay.backend.imports;

import com.sentinelpay.backend.transaction.TransactionEvent;
import com.sentinelpay.backend.transaction.TransactionStatus;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;

/** Validates a complete upload before callers may persist anything. Does not close the caller's stream. */
public final class TransactionCsvReader {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_ROWS = 5000;
    public static final String HEADER = "id,timestamp,amount,currency,status,latencyMs";

    public List<TransactionEvent> read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw invalid(0, "CSV must be at most 2 MiB");
        String csv;
        try {
            csv = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) { throw invalid(0, "CSV must use UTF-8 encoding"); }
        if (csv.startsWith("\uFEFF")) csv = csv.substring(1);
        var lines = csv.split("\\r\\n|\\n|\\r", -1);
        if (lines.length == 0 || !fields(lines[0], 1).equals(List.of(HEADER.split(","))))
            throw invalid(1, "Expected header: " + HEADER);
        var events = new ArrayList<TransactionEvent>();
        var ids = new HashSet<String>();
        for (int i = 1; i < lines.length; i++) {
            if (i == lines.length - 1 && lines[i].isEmpty()) continue;
            int row = i + 1;
            if (events.size() >= MAX_ROWS) throw invalid(row, "CSV must contain at most 5000 transactions");
            var cells = fields(lines[i], row);
            if (cells.size() != 6) throw invalid(row, "Expected exactly 6 columns");
            String id = cells.get(0);
            if (id.isBlank() || id.length() > 128 || id.chars().anyMatch(Character::isISOControl))
                throw invalid(row, "id must contain 1 to 128 characters without control characters");
            if (!ids.add(id)) throw invalid(row, "Duplicate transaction id in this file");
            Instant timestamp;
            try { timestamp = Instant.parse(cells.get(1)); }
            catch (RuntimeException ex) { throw invalid(row, "timestamp must be an ISO-8601 instant with a timezone, such as 2026-01-01T12:00:00Z"); }
            if (timestamp.isBefore(Instant.EPOCH) || !timestamp.isBefore(Instant.parse("2200-01-01T00:00:00Z")))
                throw invalid(row, "timestamp must be from 1970 to before 2200");
            String amountText = cells.get(2);
            if (!amountText.matches("[0-9]{1,12}(\\.[0-9]{1,6})?"))
                throw invalid(row, "amount must be a decimal with up to 12 whole digits and 6 fractional digits");
            var amount = new BigDecimal(amountText);
            if (amount.signum() <= 0) throw invalid(row, "amount must be positive");
            Currency currency;
            try { currency = Currency.getInstance(cells.get(3)); }
            catch (IllegalArgumentException ex) { throw invalid(row, "currency must be a valid uppercase currency code, such as USD"); }
            TransactionStatus status;
            try { status = TransactionStatus.valueOf(cells.get(4)); }
            catch (IllegalArgumentException ex) { throw invalid(row, "status must be SUCCESS or FAILED"); }
            long latency;
            try {
                if (!cells.get(5).matches("[0-9]{1,19}")) throw new NumberFormatException();
                latency = Long.parseLong(cells.get(5));
            } catch (NumberFormatException ex) { throw invalid(row, "latencyMs must be a non-negative whole number within the Java long range"); }
            events.add(new TransactionEvent(id, timestamp, amount, currency, status, latency));
        }
        if (events.isEmpty()) throw invalid(0, "CSV must contain at least one transaction");
        return List.copyOf(events);
    }

    // The six supported fields never require embedded line breaks; reject multiline records explicitly.
    private List<String> fields(String line, int row) {
        var result = new ArrayList<String>();
        int position = 0;
        while (true) {
            var value = new StringBuilder();
            if (position < line.length() && line.charAt(position) == '"') {
                position++; boolean closed = false;
                while (position < line.length()) {
                    char c = line.charAt(position++);
                    if (c != '"') { value.append(c); continue; }
                    if (position < line.length() && line.charAt(position) == '"') { value.append('"'); position++; }
                    else { closed = true; break; }
                }
                if (!closed || (position < line.length() && line.charAt(position) != ','))
                    throw invalid(row, "Invalid quoted field; embedded line breaks are not supported");
            } else {
                while (position < line.length() && line.charAt(position) != ',') {
                    char c = line.charAt(position++);
                    if (c == '"') throw invalid(row, "Quotes must surround the entire CSV field");
                    value.append(c);
                }
            }
            result.add(value.toString());
            if (result.size() > 6) throw invalid(row, "Expected exactly 6 columns");
            if (position == line.length()) break;
            position++;
        }
        return result;
    }

    private IllegalArgumentException invalid(int row, String message) {
        return new IllegalArgumentException((row > 0 ? "Row " + row + ": " : "") + message);
    }
}
