package com.gak.aisubscription.sync;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 官方组织 Costs API；固定域名且禁止重定向，密钥不会被转发到用户 URL。 */
@Component
public class OpenAiCostsClient {
    private static final String ENDPOINT = "https://api.openai.com/v1/organization/costs";
    private static final int MAX_PAGES = 8;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public OpenAiCostsClient(ObjectMapper json) { this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS); }
    public List<AiOfficialCost> fetch(String apiKey, LocalDate start, LocalDate end) {
        List<AiOfficialCost> costs = new ArrayList<>();
        Set<String> seenPages = new HashSet<>();
        String page = null;
        for (int count = 0; count < MAX_PAGES; count++) {
            String query = "?start_time=" + start.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
                    + "&end_time=" + end.atStartOfDay(ZoneOffset.UTC).toEpochSecond() + "&bucket_width=1d&limit=180";
            if (page != null) query += "&page=" + URLEncoder.encode(page, StandardCharsets.UTF_8);
            JsonNode response = request(apiKey, query);
            costs.addAll(parse(response, start, end));
            if (!response.path("has_more").asBoolean()) return costs;
            page = response.path("next_page").asText("");
            if (page.isBlank() || !seenPages.add(page)) throw new AiSyncFailure("INVALID_PROVIDER_PAGE", false, 0);
        }
        throw new AiSyncFailure("PROVIDER_PAGE_LIMIT", false, 0);
    }
    private JsonNode request(String key, String query) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + query))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + key).GET().build();
            HttpResponse<byte[]> response = http.send(request, ignored -> new BoundedCostResponse());
            int status = response.statusCode();
            if (status == 401 || status == 403) throw new AiSyncFailure("AUTH_REQUIRED", false, 0);
            if (status == 429) throw new AiSyncFailure("RATE_LIMITED", true,
                    retryAfter(response.headers().firstValue("Retry-After").orElse("60"), Instant.now()));
            if (status >= 500) throw new AiSyncFailure("PROVIDER_UNAVAILABLE", true, 60);
            if (status != 200) throw new AiSyncFailure("PROVIDER_REQUEST_REJECTED", false, 0);
            return json.readTree(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiSyncFailure("INTERRUPTED", true, 60);
        } catch (java.io.IOException exception) {
            if (exception.getCause() instanceof AiSyncFailure failure) {
                throw failure;
            }
            throw new AiSyncFailure("PROVIDER_TIMEOUT_OR_INVALID_RESPONSE", true, 60);
        }
    }
    /** 合并同一桶内的币种，稳定快照键为账号+UTC日+币种。未知结构必须失败，不能伪造零费用。 */
    List<AiOfficialCost> parse(JsonNode response, LocalDate start, LocalDate end) {
        if (response == null || !response.path("data").isArray()) throw new AiSyncFailure("INVALID_PROVIDER_DATA", false, 0);
        List<AiOfficialCost> result = new ArrayList<>();
        for (JsonNode bucket : response.path("data")) {
            long from = bucket.path("start_time").asLong(-1), to = bucket.path("end_time").asLong(-1);
            LocalDate date = Instant.ofEpochSecond(from).atZone(ZoneOffset.UTC).toLocalDate();
            if (from < 0 || to - from != 86400 || from % 86400 != 0 || date.isBefore(start)
                    || !date.isBefore(end) || !bucket.path("results").isArray()) {
                throw new AiSyncFailure("INVALID_PROVIDER_BUCKET", false, 0);
            }
            Map<String, BigDecimal> amounts = new HashMap<>();
            for (JsonNode entry : bucket.path("results")) {
                JsonNode amount = entry.path("amount");
                String currency = amount.path("currency").asText("").toUpperCase(Locale.ROOT);
                if (!currency.matches("[A-Z]{3}") || !amount.path("value").isNumber()
                        || amount.path("value").decimalValue().signum() < 0) {
                    throw new AiSyncFailure("INVALID_PROVIDER_AMOUNT", false, 0);
                }
                amounts.merge(currency, amount.path("value").decimalValue(), BigDecimal::add);
            }
            amounts.forEach((currency, amount) -> result.add(new AiOfficialCost(date, date.plusDays(1), currency, amount)));
        }
        return result;
    }
    static long retryAfter(String header, Instant now) {
        long seconds;
        try { seconds = Long.parseLong(header); }
        catch (RuntimeException ignored) {
            try { seconds = Duration.between(now, ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds(); }
            catch (RuntimeException invalid) { seconds = 60; }
        }
        return Math.max(1, Math.min(86400, seconds));
    }
}
