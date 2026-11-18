package com.gak.aisubscription.sync;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OpenAiCostsClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private final OpenAiCostsClient client = new OpenAiCostsClient(json);
    private final LocalDate day = LocalDate.of(2024, 11, 1);
    @Test void combinesCurrencyRowsWithoutTurningCostIntoTokens() throws Exception {
        var costs = client.parse(json.readTree("""
          {"data":[{"start_time":1730419200,"end_time":1730505600,"results":[
            {"amount":{"currency":"usd","value":0.06}},
            {"amount":{"currency":"usd","value":0.07}}
          ]}]}
          """), day, day.plusDays(1));
        assertEquals(1, costs.size());
        assertEquals(0, new BigDecimal("0.13").compareTo(costs.get(0).amount()));
        assertEquals("USD", costs.get(0).currency());
        assertEquals(day.plusDays(1), costs.get(0).endDate());
    }
    @Test void invalidAmountsAndPartialBucketsAreNotAssumedZero() throws Exception {
        assertThrows(AiSyncFailure.class, () -> client.parse(json.readTree("{}"), day, day.plusDays(1)));
        assertThrows(AiSyncFailure.class, () -> client.parse(json.readTree("""
          {"data":[{"start_time":1730419200,"end_time":1730505600,"results":[{"amount":{"currency":"usd"}}]}]}
          """), day, day.plusDays(1)));
        assertThrows(AiSyncFailure.class, () -> client.parse(json.readTree("""
          {"data":[{"start_time":1730419200,"end_time":1730419300,"results":[]}]}
          """), day, day.plusDays(1)));
    }
    @Test void retryAfterSupportsSecondsAndHttpDateAndBounds() {
        Instant now = Instant.parse("2026-11-18T00:00:00Z");
        assertEquals(42, OpenAiCostsClient.retryAfter("42", now));
        assertEquals(60, OpenAiCostsClient.retryAfter("Wed, 18 Nov 2026 00:01:00 GMT", now));
        assertEquals(86400, OpenAiCostsClient.retryAfter("99999999", now));
        assertEquals(60, OpenAiCostsClient.retryAfter("invalid", now));
    }
}
