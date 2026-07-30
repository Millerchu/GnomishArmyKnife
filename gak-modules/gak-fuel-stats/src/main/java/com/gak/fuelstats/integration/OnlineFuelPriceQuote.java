package com.gak.fuelstats.integration;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 第三方油价服务返回的省级参考价。
 */
public record OnlineFuelPriceQuote(
        String region,
        BigDecimal price92,
        BigDecimal price95,
        BigDecimal price98,
        BigDecimal priceDiesel,
        LocalDateTime fetchedAt
) {
}
