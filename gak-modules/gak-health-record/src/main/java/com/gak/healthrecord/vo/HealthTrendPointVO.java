package com.gak.healthrecord.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 健康趋势点视图。
 */
public class HealthTrendPointVO {

    private Long recordId;
    private LocalDate measureDate;
    private BigDecimal value;
    private BigDecimal secondaryValue;

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public LocalDate getMeasureDate() {
        return measureDate;
    }

    public void setMeasureDate(LocalDate measureDate) {
        this.measureDate = measureDate;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public BigDecimal getSecondaryValue() {
        return secondaryValue;
    }

    public void setSecondaryValue(BigDecimal secondaryValue) {
        this.secondaryValue = secondaryValue;
    }
}
