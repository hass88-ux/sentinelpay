package com.sentinelpay.backend.imports;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import com.sentinelpay.backend.transaction.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

class PrivateUploadStoreTest {
    static EmbeddedPostgres postgres;
    static PrivateUploadStore store;
    static JdbcTemplate jdbc;
    static com.zaxxer.hikari.HikariDataSource runtime;
    final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    @BeforeAll static void start() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        var source = postgres.getPostgresDatabase();
        Flyway.configure().dataSource(source).locations("classpath:db/uploads").load().migrate();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE ROLE upload_test LOGIN NOSUPERUSER NOBYPASSRLS");
        jdbc.execute("GRANT USAGE ON SCHEMA sentinelpay_private TO upload_test");
        jdbc.execute("GRANT SELECT,INSERT,DELETE ON sentinelpay_private.upload, sentinelpay_private.upload_event TO upload_test");
        runtime = new com.zaxxer.hikari.HikariDataSource();
        runtime.setJdbcUrl(postgres.getJdbcUrl("upload_test", "postgres"));
        runtime.setMaximumPoolSize(1); // Force reuse to expose leaked account context.
        store = new PrivateUploadStore(runtime);
    }
    @AfterAll static void stop() throws Exception { if(runtime!=null)runtime.close(); if (postgres != null) postgres.close(); }
    @BeforeEach void clear() { jdbc.execute("TRUNCATE sentinelpay_private.upload CASCADE"); }
    TransactionEvent event(String id, String currency) {
        return new TransactionEvent(id, Instant.parse("2026-01-01T12:00:00Z"), new BigDecimal("19.99"), Currency.getInstance(currency), TransactionStatus.SUCCESS, 120);
    }
    @Test void isolatesListReadMetricsAndDeleteByOwner() {
        var upload = store.save(alice, "payments.csv", List.of(event("1", "USD")));
        assertEquals(1, store.list(alice).size()); assertTrue(store.list(bob).isEmpty());
        assertTrue(store.find(bob, upload.id()).isEmpty()); assertTrue(store.metrics(bob, upload.id()).isEmpty());
        assertFalse(store.delete(bob, upload.id())); assertTrue(store.find(alice, upload.id()).isPresent());
        assertTrue(store.delete(alice, upload.id()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sentinelpay_private.upload_event", Integer.class));
    }
    @Test void keepsFilesAndCurrenciesSeparateEvenWithMatchingTransactionIds() {
        var first = store.save(alice, "one.csv", List.of(event("1", "USD"), event("2", "EUR")));
        var second = store.save(alice, "two.csv", List.of(event("1", "USD")));
        store.save(bob, "one.csv", List.of(event("1", "USD")));
        assertEquals(2, store.metrics(alice, first.id()).size());
        assertEquals(1, store.metrics(alice, second.id()).getFirst().totalCount());
    }
    @Test void rollsBackEntireUploadOnPersistenceFailure() {
        assertThrows(RuntimeException.class, () -> store.save(alice, "bad.csv", List.of(event("1", "USD"), event("1", "USD"))));
        assertTrue(store.list(alice).isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sentinelpay_private.upload_event", Integer.class));
    }
    @Test void capsSavedUploadsPerOwner() {
        for (int i = 0; i < 20; i++) store.save(alice, "file.csv", List.of(event("1", "USD")));
        assertThrows(IllegalArgumentException.class, () -> store.save(alice, "extra.csv", List.of(event("1", "USD"))));
        assertEquals(1, store.save(bob, "allowed.csv", List.of(event("1", "USD"))).transactionCount());
    }

    @Test void databaseFiltersUnscopedReadsAndDeletesAndRejectsForeignWrites() throws Exception {
        var first=store.save(alice,"alice.csv",List.of(event("1","USD")));
        store.save(bob,"bob.csv",List.of(event("1","USD")));
        try(var c=runtime.getConnection();var s=c.createStatement()) {
            c.setAutoCommit(false);
            s.execute("SELECT set_config('sentinelpay.owner_id','"+bob+"',true)");
            try(var rows=s.executeQuery("SELECT owner_id FROM sentinelpay_private.upload")) {
                assertTrue(rows.next()); assertEquals(bob,rows.getObject(1,UUID.class)); assertFalse(rows.next());
            }
            try(var rows=s.executeQuery("SELECT owner_id FROM sentinelpay_private.upload_event")) {
                assertTrue(rows.next()); assertEquals(bob,rows.getObject(1,UUID.class)); assertFalse(rows.next());
            }
            assertEquals(0,s.executeUpdate("DELETE FROM sentinelpay_private.upload WHERE id='"+first.id()+"'"));
            assertThrows(java.sql.SQLException.class,()->s.executeUpdate("INSERT INTO sentinelpay_private.upload VALUES ('"+UUID.randomUUID()+"','"+alice+"','forbidden.csv',now(),1)"));
            c.rollback();
            s.execute("SELECT set_config('sentinelpay.owner_id','"+bob+"',true)");
            assertThrows(java.sql.SQLException.class,()->s.executeUpdate("INSERT INTO sentinelpay_private.upload_event VALUES ('"+first.id()+"','"+alice+"','forbidden',now(),1,'USD','SUCCESS',1)"));
            c.rollback();
        }
        assertTrue(store.find(alice,first.id()).isPresent());
    }

    @Test void missingContextCannotReadOrWriteAndCommitDoesNotLeakIdentity() throws Exception {
        store.save(alice,"alice.csv",List.of(event("1","USD")));
        assertEquals(1,store.list(alice).size());
        try(var c=runtime.getConnection();var s=c.createStatement()) {
            for(String table:List.of("upload","upload_event")) {
                try(var rows=s.executeQuery("SELECT count(*) FROM sentinelpay_private."+table)) { rows.next();assertEquals(0,rows.getInt(1)); }
            }
            assertThrows(java.sql.SQLException.class,()->s.executeUpdate("INSERT INTO sentinelpay_private.upload VALUES ('"+UUID.randomUUID()+"','"+alice+"','forbidden.csv',now(),1)"));
        }
        assertTrue(store.list(bob).isEmpty());
    }

    @Test void rollbackDoesNotLeakIdentityToReusedConnection() throws Exception {
        store.save(alice,"alice.csv",List.of(event("1","USD")));
        assertThrows(RuntimeException.class,()->store.save(bob,"bad.csv",List.of(event("1","USD"),event("1","USD"))));
        try(var c=runtime.getConnection();var s=c.createStatement();var rows=s.executeQuery("SELECT count(*) FROM sentinelpay_private.upload")) {
            rows.next();assertEquals(0,rows.getInt(1));
        }
        assertEquals(1,store.list(alice).size());assertTrue(store.list(bob).isEmpty());
    }
}
