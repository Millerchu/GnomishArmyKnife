package com.gak.fuelstats.vo;

import java.math.BigDecimal;

/**
 * 年度用油支出项。
 */
public class FuelYearlyCostReportItemVO {

    private String label;
    private BigDecimal totalAmount;
    private BigDecimal totalFuelVolume;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getTotalFuelVolume() {
        return totalFuelVolume;
    }

    public void setTotalFuelVolume(BigDecimal totalFuelVolume) {
        this.totalFuelVolume = totalFuelVolume;
    }
}
