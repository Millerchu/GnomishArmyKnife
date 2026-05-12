package com.gak.healthrecord.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 健康趋势查询参数。
 */
public class HealthTrendQueryRequest {

    private String metricKey;

    @Min(value = 1, message = "limit 不能小于 1")
    @Max(value = 50, message = "limit 不能大于 50")
    private Integer limit = 12;

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
