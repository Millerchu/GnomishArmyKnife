package com.gak.aisubscription.sync;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** xAI 团队费用使用管理密钥和美元聚合值，接口域名固定。 */
@Component
public class GrokCostsClient {
    private static final String ENDPOINT = "https://management-api.x.ai/v1/billing/teams/";
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public GrokCostsClient(ObjectMapper json) {
        this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    public List<AiOfficialCost> fetch(String apiKey, String teamId, LocalDate start, LocalDate end) {
        try {
            var analytics = Map.of(
                    "timeRange", Map.of("startTime", start + " 00:00:00", "endTime", end + " 00:00:00",
                            "timezone", "Etc/GMT"),
                    "timeUnit", "TIME_UNIT_DAY", "groupBy", List.of(), "filters", List.of(),
                    "values", List.of(Map.of("name", "usd", "aggregation", "AGGREGATION_SUM")));
            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + teamId + "/usage"))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("analyticsRequest", analytics))))
                    .build();
            HttpResponse<byte[]> response = http.send(request, ignored -> new BoundedCostResponse());
            int status = response.statusCode();
            if (status == 401 || status == 403) throw new AiSyncFailure("AUTH_REQUIRED", false, 0);
            if (status == 429) throw new AiSyncFailure("RATE_LIMITED", true,
                    OpenAiCostsClient.retryAfter(response.headers().firstValue("Retry-After").orElse("60"), Instant.now()));
            if (status >= 500) throw new AiSyncFailure("PROVIDER_UNAVAILABLE", true, 60);
            if (status != 200) throw new AiSyncFailure("PROVIDER_REQUEST_REJECTED", false, 0);
            return parse(json.readTree(response.body()), start, end);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiSyncFailure("INTERRUPTED", true, 60);
        } catch (IOException exception) {
            if (exception.getCause() instanceof AiSyncFailure failure) {
                throw failure;
            }
            throw new AiSyncFailure("PROVIDER_TIMEOUT_OR_INVALID_RESPONSE", true, 60);
        }
    }

    private OffsetDateTime parseTimestamp(String value) {
        try {
            return Instant.parse(value).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw new AiSyncFailure("INVALID_PROVIDER_BUCKET", false, 0);
        }
    }

    List<AiOfficialCost> parse(JsonNode response, LocalDate start, LocalDate end) {
        if (response == null || response.path("limitReached").asBoolean() || !response.path("timeSeries").isArray()) {
            throw new AiSyncFailure("INVALID_PROVIDER_DATA", false, 0);
        }
        Map<LocalDate, BigDecimal> buckets = new TreeMap<>();
        for (JsonNode series : response.path("timeSeries")) {
            if (!series.path("dataPoints").isArray()) throw new AiSyncFailure("INVALID_PROVIDER_DATA", false, 0);
            for (JsonNode point : series.path("dataPoints")) {
                var timestamp = parseTimestamp(point.path("timestamp").asText());
                LocalDate date = timestamp.toLocalDate();
                JsonNode amount = point.path("values").path(0);
                if (date.isBefore(start) || !date.isBefore(end) || timestamp.toLocalTime().toSecondOfDay() != 0
                        || timestamp.getNano() != 0 || !amount.isNumber() || amount.decimalValue().signum() < 0) {
                    throw new AiSyncFailure("INVALID_PROVIDER_BUCKET", false, 0);
                }
                buckets.merge(date, amount.decimalValue(), BigDecimal::add);
            }
        }
        return buckets.entrySet().stream().map(bucket ->
                new AiOfficialCost(bucket.getKey(), bucket.getKey().plusDays(1), "USD", bucket.getValue())).toList();
    }
}
