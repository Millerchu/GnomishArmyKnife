package com.gak.fuelstats.vo;

import java.math.BigDecimal;

/**
 * 月度用油走势项。
 */
public class FuelMonthlyReportItemVO {

    private String label;
    private BigDecimal fuelVolume;
    private BigDecimal totalAmount;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getFuelVolume() {
        return fuelVolume;
    }

    public void setFuelVolume(BigDecimal fuelVolume) {
        this.fuelVolume = fuelVolume;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
