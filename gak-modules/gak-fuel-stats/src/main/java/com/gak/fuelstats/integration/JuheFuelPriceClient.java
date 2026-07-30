package com.gak.fuelstats.integration;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 聚合数据“今日国内油价”接口客户端。
 */
@Component
public class JuheFuelPriceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JuheFuelPriceClient.class);
    private static final int PRICE_SCALE = 2;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final String SUCCESS_CODE = "0";

    private final RestClient restClient;
    private final String apiKey;
    private final Duration cacheDuration;
    private volatile List<OnlineFuelPriceQuote> cachedQuotes = List.of();
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public JuheFuelPriceClient(
            RestClient.Builder restClientBuilder,
            @Value("${gak.fuel-price.juhe-api-url:https://apis.juhe.cn/gnyj/query}") String apiUrl,
            @Value("${gak.fuel-price.juhe-api-key:}") String apiKey,
            @Value("${gak.fuel-price.cache-minutes:30}") long cacheMinutes) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(apiUrl)
                .build();
        this.apiKey = apiKey;
        this.cacheDuration = Duration.ofMinutes(Math.max(1, cacheMinutes));
    }

    /**
     * 查询指定省级地区油价；未配置密钥或第三方异常时返回空结果，由业务层降级。
     */
    public Optional<OnlineFuelPriceQuote> findByRegion(String region) {
        if (!StringUtils.hasText(apiKey)) {
            return Optional.empty();
        }
        try {
            return loadQuotes().stream()
                    .filter(item -> item.region().equals(region))
                    .findFirst();
        } catch (RestClientException | IllegalStateException exception) {
            LOGGER.warn("查询聚合数据油价失败，region={}, reason={}", region, exception.getMessage());
            return Optional.empty();
        }
    }

    private List<OnlineFuelPriceQuote> loadQuotes() {
        Instant now = Instant.now();
        if (now.isBefore(cacheExpiresAt) && !cachedQuotes.isEmpty()) {
            return cachedQuotes;
        }
        synchronized (this) {
            now = Instant.now();
            if (now.isBefore(cacheExpiresAt) && !cachedQuotes.isEmpty()) {
                return cachedQuotes;
            }
            List<OnlineFuelPriceQuote> refreshedQuotes = requestQuotes();
            cachedQuotes = List.copyOf(refreshedQuotes);
            cacheExpiresAt = now.plus(cacheDuration);
            return cachedQuotes;
        }
    }

    private List<OnlineFuelPriceQuote> requestQuotes() {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !SUCCESS_CODE.equals(response.path("error_code").asText())) {
            String reason = response == null ? "响应为空" : response.path("reason").asText("未知错误");
            throw new IllegalStateException("聚合数据油价接口返回失败：" + reason);
        }

        JsonNode resultNode = response.path("result");
        if (!resultNode.isArray()) {
            throw new IllegalStateException("聚合数据油价接口缺少结果列表");
        }

        LocalDateTime fetchedAt = LocalDateTime.now();
        List<OnlineFuelPriceQuote> quotes = new ArrayList<>();
        for (JsonNode item : resultNode) {
            String region = item.path("city").asText("").trim();
            if (!StringUtils.hasText(region)) {
                continue;
            }
            quotes.add(new OnlineFuelPriceQuote(
                    region,
                    parsePrice(item.path("92h").asText()),
                    parsePrice(item.path("95h").asText()),
                    parsePrice(item.path("98h").asText()),
                    parsePrice(item.path("0h").asText()),
                    fetchedAt
            ));
        }
        if (quotes.isEmpty()) {
            throw new IllegalStateException("聚合数据油价接口未返回有效地区");
        }
        return quotes;
    }

    private BigDecimal parsePrice(String rawValue) {
        if (!StringUtils.hasText(rawValue) || "-".equals(rawValue.trim())) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(rawValue.trim());
            return price.compareTo(BigDecimal.ZERO) > 0
                    ? price.setScale(PRICE_SCALE, RoundingMode.HALF_UP)
                    : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
