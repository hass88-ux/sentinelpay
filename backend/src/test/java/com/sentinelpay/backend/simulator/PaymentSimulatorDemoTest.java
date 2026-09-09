package com.sentinelpay.backend.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sentinelpay.backend.transaction.TransactionEvent;

class PaymentSimulatorDemoTest {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream error = new ByteArrayOutputStream();

    private int run(String... args) {
        return PaymentSimulatorDemo.run(args, new PrintStream(output), new PrintStream(error));
    }

    @Test
    void defaultRunPrintsTenEventsAndNoErrors() {
        assertEquals(0, run());
        assertEquals(10, output.toString().lines().count());
        assertTrue(output.toString().lines().allMatch(line -> line.startsWith("TransactionEvent[")));
        assertEquals("", error.toString());
    }

    @Test
    void customRunAndHelpAreUsable() {
        assertEquals(0, run("--count=3", "--scenario=outage", "--seed=42", "--interval-ms=0"));
        assertEquals(3, output.toString().lines().count());
        output.reset();
        assertEquals(0, run("--help"));
        assertTrue(output.toString().startsWith("Usage:"));
        assertFalse(output.toString().contains("TransactionEvent["));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--count=0", "--count=10001", "--count=no", "--count=999999999999999",
            "--interval-ms=-1", "--interval-ms=1001", "--scenario=invalid", "--seed=no", "--seed=",
            "--unknown=3", "--count", "--help=1"})
    void rejectsBadOptionsBeforeEmittingEvents(String option) {
        assertEquals(2, run(option));
        assertEquals("", output.toString());
        assertTrue(error.toString().contains("Usage:"));
    }

    @Test
    void rejectsDuplicateOptions() {
        assertEquals(2, run("--count=1", "--count=2"));
        assertEquals("", output.toString());
    }

    @Test
    void runnerStopsOnInterruptionWithoutEmittingAnotherEvent() {
        var events = new ArrayList<TransactionEvent>();
        try {
            assertThrows(InterruptedException.class, () -> SimulationRunner.run(
                    new PaymentSimulator(new Random(1)), 10, 0, event -> {
                        events.add(event);
                        Thread.currentThread().interrupt();
                    }));
            assertEquals(1, events.size());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void consolePreservesInterruptAndReturnsCancellationCode() {
        try {
            Thread.currentThread().interrupt();
            assertEquals(130, run());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals("", output.toString());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void runnerPropagatesOutputFailureAndStops() {
        var events = new ArrayList<TransactionEvent>();
        assertThrows(IllegalStateException.class, () -> SimulationRunner.run(
                new PaymentSimulator(new Random(1)), 10, 0, event -> {
                    events.add(event);
                    throw new IllegalStateException("output unavailable");
                }));
        assertEquals(1, events.size());
    }
}
