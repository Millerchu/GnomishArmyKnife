package com.gak.healthrecord.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 健康趋势点视图。
 */
public class HealthTrendPointVO {

    private LocalDate measureDate;
    private BigDecimal value;

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
}
