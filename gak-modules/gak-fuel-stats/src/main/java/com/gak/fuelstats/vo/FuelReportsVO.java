package com.gak.fuelstats.vo;

import java.util.List;

/**
 * 报表集合视图对象。
 */
public class FuelReportsVO {

    private List<FuelMonthlyReportItemVO> currentYearMonthlyFuel;
    private List<FuelYearlyCostReportItemVO> yearlyCostStats;

    public List<FuelMonthlyReportItemVO> getCurrentYearMonthlyFuel() {
        return currentYearMonthlyFuel;
    }

    public void setCurrentYearMonthlyFuel(List<FuelMonthlyReportItemVO> currentYearMonthlyFuel) {
        this.currentYearMonthlyFuel = currentYearMonthlyFuel;
    }

    public List<FuelYearlyCostReportItemVO> getYearlyCostStats() {
        return yearlyCostStats;
    }

    public void setYearlyCostStats(List<FuelYearlyCostReportItemVO> yearlyCostStats) {
        this.yearlyCostStats = yearlyCostStats;
    }
}
