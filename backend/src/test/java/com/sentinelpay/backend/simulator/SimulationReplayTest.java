package com.sentinelpay.backend.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class SimulationReplayTest {
    private static final Instant TIME = Instant.parse("2026-09-09T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(TIME, ZoneOffset.UTC);

    private PaymentSimulator replay(long seed) {
        var sequence = new AtomicInteger();
        return new PaymentSimulator(new Random(seed), SimulationScenario.DEGRADED.settings(), CLOCK,
                () -> "replay-" + sequence.incrementAndGet());
    }

    @Test
    void reproducesCompleteEventsWhenAllInputsAreControlled() {
        var first = replay(27);
        var second = replay(27);
        for (int i = 1; i <= 100; i++) {
            var event = first.generateTransaction();
            assertEquals(event, second.generateTransaction());
            assertEquals(TIME, event.timestamp());
            assertEquals("replay-" + i, event.id());
        }
    }

    @Test
    void separateRunsDoNotShareRandomState() {
        var first = replay(27);
        var expected = first.generateTransaction();
        for (int i = 0; i < 20; i++) {
            first.generateTransaction();
        }
        assertEquals(expected, replay(27).generateTransaction());
    }

    @Test
    void usesSuppliedClockAndRejectsInvalidIds() {
        var settings = SimulationScenario.NORMAL.settings();
        var later = Clock.offset(CLOCK, java.time.Duration.ofSeconds(5));
        assertEquals(TIME.plusSeconds(5), new PaymentSimulator(new Random(1), settings, later,
                () -> "event-1").generateTransaction().timestamp());
        assertThrows(IllegalArgumentException.class, () -> new PaymentSimulator(
                new Random(1), settings, CLOCK, () -> " ").generateTransaction());
        assertThrows(NullPointerException.class, () -> new PaymentSimulator(
                new Random(1), settings, CLOCK, () -> null).generateTransaction());
    }

    @Test
    void rejectsMissingTimeOrIdentitySources() {
        var settings = SimulationScenario.NORMAL.settings();
        assertThrows(NullPointerException.class, () -> new PaymentSimulator(new Random(1), settings, null, () -> "1"));
        assertThrows(NullPointerException.class, () -> new PaymentSimulator(new Random(1), settings, CLOCK, null));
    }
}
