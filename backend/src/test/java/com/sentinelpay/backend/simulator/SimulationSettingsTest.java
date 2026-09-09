package com.sentinelpay.backend.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sentinelpay.backend.transaction.TransactionStatus;

class SimulationSettingsTest {
    @Test
    void rejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationSettings(0, 1, 0, 1, .9));
        assertThrows(IllegalArgumentException.class, () -> new SimulationSettings(-1, 1, 0, 1, .9));
        assertThrows(IllegalArgumentException.class, () -> new SimulationSettings(2, 1, 0, 1, .9));
        assertThrows(IllegalArgumentException.class, () -> new SimulationSettings(1, 1, -1, 1, .9));
        assertThrows(IllegalArgumentException.class, () -> new SimulationSettings(1, 1, 2, 1, .9));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidProbabilities(double probability) {
        assertThrows(IllegalArgumentException.class, () -> new SimulationSettings(1, 1, 0, 1, probability));
    }

    @Test
    void supportsFixedBoundsAndProbabilityExtremesWithoutOverflow() {
        var failure = new PaymentSimulator(new Random(1), new SimulationSettings(1, 1, 0, 0, 0))
                .generateTransaction();
        assertEquals(new BigDecimal("0.01"), failure.amount());
        assertEquals(0, failure.latencyMs());
        assertEquals(TransactionStatus.FAILED, failure.status());

        var success = new PaymentSimulator(new Random(1), new SimulationSettings(
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 1))
                .generateTransaction();
        assertEquals(new BigDecimal("21474836.47"), success.amount());
        assertEquals(Integer.MAX_VALUE, success.latencyMs());
        assertEquals(TransactionStatus.SUCCESS, success.status());
    }

    @ParameterizedTest
    @EnumSource(SimulationScenario.class)
    void scenariosRespectTheirConfiguredBounds(SimulationScenario scenario) {
        var settings = scenario.settings();
        var simulator = new PaymentSimulator(new Random(12), settings);
        for (int i = 0; i < 100; i++) {
            var event = simulator.generateTransaction();
            long cents = event.amount().movePointRight(2).longValueExact();
            assertTrue(cents >= settings.minAmountCents() && cents <= settings.maxAmountCents());
            assertTrue(event.latencyMs() >= settings.minLatencyMs() && event.latencyMs() <= settings.maxLatencyMs());
        }
    }

    @Test
    void rejectsMissingSettings() {
        assertThrows(NullPointerException.class, () -> new PaymentSimulator(new Random(1), null));
    }
}
