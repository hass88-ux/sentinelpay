package com.sentinelpay.backend.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import com.sentinelpay.backend.transaction.TransactionEvent;
import com.sentinelpay.backend.transaction.TransactionStatus;

class PaymentStoreTest {
    private static EmbeddedPostgres postgres;
    private static PaymentStore store;
    private static JdbcTemplate jdbc;
    private static final Instant START = Instant.parse("2026-09-09T12:00:00Z");

    @BeforeAll
    static void start() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        var dataSource = postgres.getPostgresDatabase();
        Flyway.configure().dataSource(dataSource).load().migrate();
        store = new PaymentStore(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void stop() throws Exception {
        if (postgres != null) postgres.close();
    }

    @BeforeEach
    void clear() {
        jdbc.execute("TRUNCATE payment_event, payment_minute");
    }

    private TransactionEvent event(String id, int second, String currency, TransactionStatus status, long latency) {
        return new TransactionEvent(id, START.plusSeconds(second), new BigDecimal("1.25"),
                Currency.getInstance(currency), status, latency);
    }

    @Test
    void aggregatesEventTimeMinutesSeparatelyByCurrencyAndAcceptsLateEvents() {
        store.save(event("next", 60, "USD", TransactionStatus.SUCCESS, 300));
        store.save(event("first", 0, "USD", TransactionStatus.SUCCESS, 100));
        store.save(event("late", 59, "USD", TransactionStatus.FAILED, 200));
        store.save(event("eur", 1, "EUR", TransactionStatus.SUCCESS, 50));
        var metrics = store.metrics(START, START.plusSeconds(60), 100);
        assertEquals(2, metrics.size());
        var usd = metrics.stream().filter(metric -> metric.currency().equals("USD")).findFirst().orElseThrow();
        assertEquals(2, usd.totalCount());
        assertEquals(1, usd.successCount());
        assertEquals(1, usd.failedCount());
        assertEquals(0, new BigDecimal("2.50").compareTo(usd.totalAmount()));
        assertEquals(0, new BigDecimal("150").compareTo(usd.averageLatencyMs()));
        assertEquals(200, usd.maxLatencyMs());
        assertEquals("next", store.recent(1).getFirst().id());
    }

    @Test
    void duplicateReplayDoesNotCountTwiceAndConflictingIdentityIsRejected() {
        var original = event("same", 0, "USD", TransactionStatus.SUCCESS, 100);
        assertTrue(store.save(original));
        assertFalse(store.save(original));
        assertThrows(IllegalArgumentException.class, () -> store.save(event("same", 0, "USD", TransactionStatus.FAILED, 100)));
        assertEquals(1, store.metrics(START, START.plusSeconds(60), 100).getFirst().totalCount());
        assertEquals(original, store.find("same").orElseThrow());
    }

    @Test
    void concurrentReplaysAndWritesDoNotLoseOrDoubleCountEvents() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 20; i++) {
                String id = "event-" + (i % 10);
                tasks.add(() -> store.save(event(id, 0, "USD", TransactionStatus.SUCCESS, 100)));
            }
            int inserted = 0;
            for (var result : executor.invokeAll(tasks)) if (result.get()) inserted++;
            assertEquals(10, inserted);
            assertEquals(10, store.metrics(START, START.plusSeconds(60), 100).getFirst().totalCount());
        }
    }

    @Test
    void rollsBackRawInsertWhenMetricUpdateFails() {
        store.save(event("first", 0, "USD", TransactionStatus.SUCCESS, 100));
        jdbc.update("UPDATE payment_minute SET total_count = ?, success_count = ?", Long.MAX_VALUE, Long.MAX_VALUE);
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> store.save(event("rollback", 0, "USD", TransactionStatus.SUCCESS, 100)));
        assertTrue(store.find("rollback").isEmpty());
    }

    @Test
    void preservesMicrosecondTimeAndAvoidsLatencySumOverflow() {
        var event = new TransactionEvent("precise", START.plusNanos(123456789), new BigDecimal("1.25"),
                Currency.getInstance("USD"), TransactionStatus.SUCCESS, Long.MAX_VALUE);
        assertTrue(store.save(event));
        assertFalse(store.save(event));
        assertEquals(START.plusNanos(123456000), store.find("precise").orElseThrow().timestamp());
        store.save(event("second", 1, "USD", TransactionStatus.SUCCESS, Long.MAX_VALUE));
        assertEquals(0, BigDecimal.valueOf(Long.MAX_VALUE).compareTo(
                store.metrics(START, START.plusSeconds(60), 100).getFirst().averageLatencyMs()));
    }

    @Test
    void validatesQueryBounds() {
        assertThrows(IllegalArgumentException.class, () -> store.recent(101));
        assertThrows(IllegalArgumentException.class, () -> store.metrics(START, START, 10));
        assertEquals(0, store.metrics(START, START.plusSeconds(60), 10).size());
    }
}
