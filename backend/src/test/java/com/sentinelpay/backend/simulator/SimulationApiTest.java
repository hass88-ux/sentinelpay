package com.sentinelpay.backend.simulator;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class SimulationApiTest {
    private static final String ENDPOINT = "/api/simulator/transactions";
    @Autowired
    private MockMvc mvc;

    @Test
    void defaultPreviewSerializesPaymentFields() throws Exception {
        var result = mvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.scenario").value("NORMAL"))
                .andExpect(jsonPath("$.count").value(10))
                .andExpect(jsonPath("$.events", hasSize(10)))
                .andExpect(jsonPath("$.events[*].currency", everyItem(is("USD"))))
                .andExpect(jsonPath("$.events[*].amount", everyItem(greaterThan(0.0))))
                .andReturn().getResponse().getContentAsString();
        var json = JsonPath.parse(result);
        assertNotNull(UUID.fromString(json.read("$.events[0].id", String.class)));
        assertNotNull(Instant.parse(json.read("$.events[0].timestamp", String.class)));
    }

    @ParameterizedTest
    @EnumSource(SimulationScenario.class)
    void supportsAllScenariosAndMaximumBatchSize(SimulationScenario scenario) throws Exception {
        mvc.perform(get(ENDPOINT).param("count", "100").param("scenario", scenario.name().toLowerCase(java.util.Locale.ROOT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value(scenario.name()))
                .andExpect(jsonPath("$.count").value(100))
                .andExpect(jsonPath("$.events", hasSize(100)))
                .andExpect(jsonPath("$.events[*].latencyMs", everyItem(greaterThanOrEqualTo(scenario.settings().minLatencyMs()))))
                .andExpect(jsonPath("$.events[*].latencyMs", everyItem(lessThanOrEqualTo(scenario.settings().maxLatencyMs()))));
    }

    @ParameterizedTest
    @CsvSource({"count,0", "count,-1", "count,101", "count,abc", "count,999999999999999999",
            "seed,abc", "seed,9223372036854775808", "scenario,unknown"})
    void invalidRequestsReturnUsefulProblems(String parameter, String value) throws Exception {
        mvc.perform(get(ENDPOINT).param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid simulation request"))
                .andExpect(jsonPath("$.detail", containsString(parameter)))
                .andExpect(jsonPath("$.events").doesNotExist());
    }

    @Test
    void sameSeedReplaysFieldsButNotIdentityAndRequestsStayIndependent() throws Exception {
        var first = JsonPath.parse(mvc.perform(get(ENDPOINT).param("count", "1").param("seed", "42"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(get(ENDPOINT).param("scenario", "OUTAGE")).andExpect(status().isOk());
        var second = JsonPath.parse(mvc.perform(get(ENDPOINT).param("count", "1").param("seed", "42"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (String field : new String[] {"amount", "status", "latencyMs", "currency"}) {
            assertEquals(first.read("$.events[0]." + field, Object.class), second.read("$.events[0]." + field, Object.class));
        }
        assertNotEquals(first.read("$.events[0].id", String.class), second.read("$.events[0].id", String.class));
    }
}
