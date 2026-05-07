package com.gak.fuelstats.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 最新油价视图对象。
 */
public class FuelLatestPricesVO {

    private LocalDateTime publishDate;
    private Map<String, BigDecimal> prices;

    public LocalDateTime getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(LocalDateTime publishDate) {
        this.publishDate = publishDate;
    }

    public Map<String, BigDecimal> getPrices() {
        return prices;
    }

    public void setPrices(Map<String, BigDecimal> prices) {
        this.prices = prices;
    }
}
