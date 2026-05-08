package com.gak.fuelstats.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 最新油价视图对象。
 */
public class FuelLatestPricesVO {

    private LocalDateTime publishDate;
    private LocalDateTime nextAdjustTime;
    private String adjustWindow;
    private String priceChangeHint;
    private String remark;
    private Map<String, BigDecimal> prices;

    public LocalDateTime getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(LocalDateTime publishDate) {
        this.publishDate = publishDate;
    }

    public LocalDateTime getNextAdjustTime() {
        return nextAdjustTime;
    }

    public void setNextAdjustTime(LocalDateTime nextAdjustTime) {
        this.nextAdjustTime = nextAdjustTime;
    }

    public String getAdjustWindow() {
        return adjustWindow;
    }

    public void setAdjustWindow(String adjustWindow) {
        this.adjustWindow = adjustWindow;
    }

    public String getPriceChangeHint() {
        return priceChangeHint;
    }

    public void setPriceChangeHint(String priceChangeHint) {
        this.priceChangeHint = priceChangeHint;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Map<String, BigDecimal> getPrices() {
        return prices;
    }

    public void setPrices(Map<String, BigDecimal> prices) {
        this.prices = prices;
    }
}
