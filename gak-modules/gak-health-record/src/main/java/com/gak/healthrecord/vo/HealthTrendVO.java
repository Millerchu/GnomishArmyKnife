package com.gak.healthrecord.vo;

import java.util.List;

/**
 * 健康趋势视图。
 */
public class HealthTrendVO {

    private String metricKey;
    private List<HealthTrendPointVO> points;

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public List<HealthTrendPointVO> getPoints() {
        return points;
    }

    public void setPoints(List<HealthTrendPointVO> points) {
        this.points = points;
    }
}
