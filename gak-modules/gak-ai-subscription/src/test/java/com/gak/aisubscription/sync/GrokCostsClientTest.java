package com.gak.aisubscription.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrokCostsClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private final GrokCostsClient client = new GrokCostsClient(json);
    private final LocalDate day = LocalDate.of(2026, 11, 17);

    @Test
    void sumsUsdAtUtcDayWithoutUnitPriceConversion() throws Exception {
        var response = json.readTree("""
            {"limitReached":false,"timeSeries":[
              {"dataPoints":[{"timestamp":"2026-11-17T00:00:00Z","values":[0.0025]}]},
              {"dataPoints":[{"timestamp":"2026-11-17T00:00:00Z","values":[0.25]}]}
            ]}
            """);
        var costs = client.parse(response, day, day.plusDays(1));
        assertEquals(1, costs.size());
        assertEquals(new AiOfficialCost(day, day.plusDays(1), "USD", new BigDecimal("0.2525")), costs.getFirst());
    }

    @Test
    void rejectsPartialDataBeforeReplacingExistingBuckets() throws Exception {
        var response = json.readTree("{\"limitReached\":true,\"timeSeries\":[]}");
        assertThrows(AiSyncFailure.class, () -> client.parse(response, day, day.plusDays(1)));
    }
}
