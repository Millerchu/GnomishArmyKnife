package com.gak.fuelstats.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 油耗统计概览。
 */
public class FuelSummaryVO {

    private BigDecimal totalAmount;
    private BigDecimal totalDiscountAmount;
    private BigDecimal totalFuelVolume;
    private BigDecimal averageUnitPrice;
    private BigDecimal averageConsumption;
    private BigDecimal currentMonthAmount;
    private List<FuelVehicleStatVO> vehicleStats;
    private List<FuelRecordVO> recentRecords;

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getTotalDiscountAmount() {
        return totalDiscountAmount;
    }

    public void setTotalDiscountAmount(BigDecimal totalDiscountAmount) {
        this.totalDiscountAmount = totalDiscountAmount;
    }

    public BigDecimal getTotalFuelVolume() {
        return totalFuelVolume;
    }

    public void setTotalFuelVolume(BigDecimal totalFuelVolume) {
        this.totalFuelVolume = totalFuelVolume;
    }

    public BigDecimal getAverageUnitPrice() {
        return averageUnitPrice;
    }

    public void setAverageUnitPrice(BigDecimal averageUnitPrice) {
        this.averageUnitPrice = averageUnitPrice;
    }

    public BigDecimal getAverageConsumption() {
        return averageConsumption;
    }

    public void setAverageConsumption(BigDecimal averageConsumption) {
        this.averageConsumption = averageConsumption;
    }

    public BigDecimal getCurrentMonthAmount() {
        return currentMonthAmount;
    }

    public void setCurrentMonthAmount(BigDecimal currentMonthAmount) {
        this.currentMonthAmount = currentMonthAmount;
    }

    public List<FuelVehicleStatVO> getVehicleStats() {
        return vehicleStats;
    }

    public void setVehicleStats(List<FuelVehicleStatVO> vehicleStats) {
        this.vehicleStats = vehicleStats;
    }

    public List<FuelRecordVO> getRecentRecords() {
        return recentRecords;
    }

    public void setRecentRecords(List<FuelRecordVO> recentRecords) {
        this.recentRecords = recentRecords;
    }
}
