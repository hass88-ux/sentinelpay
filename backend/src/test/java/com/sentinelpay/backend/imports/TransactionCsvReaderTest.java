package com.sentinelpay.backend.imports;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionCsvReaderTest {
    private final TransactionCsvReader reader = new TransactionCsvReader();
    private static final String HEADER = TransactionCsvReader.HEADER;
    private static final String ROW = "pay-1,2026-01-01T12:00:00Z,19.99,USD,SUCCESS,120";
    private ByteArrayInputStream input(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }

    @Test void acceptsBomWindowsLinesAndQuotedFields() throws Exception {
        var events = reader.read(input("\uFEFF" + HEADER + "\r\n\"pay,\"\"1\"\"\",2026-01-01T12:00:00Z,19.99,USD,FAILED,0\r\n"));
        assertEquals(1, events.size());
        assertEquals("pay,\"1\"", events.getFirst().id());
        assertEquals("19.99", events.getFirst().amount().toPlainString());
        assertEquals(0, events.getFirst().latencyMs());
        assertThrows(UnsupportedOperationException.class, () -> events.clear());
    }
    @Test void allowsDifferentCurrenciesWithoutConversion() throws Exception {
        var events = reader.read(input(HEADER + "\n" + ROW + "\npay-2,2026-01-01T12:01:00Z,2.001,EUR,SUCCESS,5"));
        assertEquals("EUR", events.get(1).currency().getCurrencyCode());
        assertEquals("2.001", events.get(1).amount().toPlainString());
    }
    @Test void rejectsDuplicateIdsWithRowNumber() {
        var error = assertThrows(IllegalArgumentException.class, () -> reader.read(input(HEADER + "\n" + ROW + "\n" + ROW)));
        assertEquals("Row 3: Duplicate transaction id in this file", error.getMessage());
    }
    @ParameterizedTest
    @ValueSource(strings = {"", "id,amount", "id,timestamp,amount,currency,status,latencyMs"})
    void rejectsEmptyOrWrongHeader(String csv) { assertThrows(IllegalArgumentException.class, () -> reader.read(input(csv))); }
    @ParameterizedTest
    @ValueSource(strings = {
        "pay-1,invalid,19.99,USD,SUCCESS,120",
        "pay-1,2026-01-01T12:00:00,19.99,USD,SUCCESS,120",
        "pay-1,2026-01-01T12:00:00Z,1e99,USD,SUCCESS,120",
        "pay-1,2026-01-01T12:00:00Z,0,USD,SUCCESS,120",
        "pay-1,2026-01-01T12:00:00Z,1,usd,SUCCESS,120",
        "pay-1,2026-01-01T12:00:00Z,1,USD,PENDING,120",
        "pay-1,2026-01-01T12:00:00Z,1,USD,SUCCESS,-1",
        "pay-1,2026-01-01T12:00:00Z,1,USD,SUCCESS,9223372036854775808",
        "pay-1,2026-01-01T12:00:00Z,1,USD,SUCCESS,1,extra",
        "\"unterminated,2026-01-01T12:00:00Z,1,USD,SUCCESS,1",
        "pay\"1,2026-01-01T12:00:00Z,1,USD,SUCCESS,1",
        "\"pay\"suffix,2026-01-01T12:00:00Z,1,USD,SUCCESS,1"
    })
    void rejectsInvalidRowsWithoutReturningPartialResults(String row) {
        var error = assertThrows(IllegalArgumentException.class, () -> reader.read(input(HEADER + "\n" + row)));
        assertTrue(error.getMessage().startsWith("Row 2:"));
    }
    @Test void rejectsOversizedFilesBeforeParsing() {
        assertThrows(IllegalArgumentException.class, () -> reader.read(new ByteArrayInputStream(new byte[TransactionCsvReader.MAX_BYTES + 1])));
    }
    @Test void rejectsInvalidUtf8() {
        var error = assertThrows(IllegalArgumentException.class, () -> reader.read(new ByteArrayInputStream(new byte[]{(byte)0xff})));
        assertTrue(error.getMessage().contains("UTF-8"));
    }
    @Test void enforcesTransactionCountLimit() {
        var csv = new StringBuilder(HEADER);
        for (int i = 0; i <= TransactionCsvReader.MAX_ROWS; i++) csv.append('\n').append(ROW.replace("pay-1", "pay-" + i));
        assertThrows(IllegalArgumentException.class, () -> reader.read(input(csv.toString())));
    }
}
