package com.gak.healthrecord.vo;

import java.time.LocalDate;

/**
 * 健康概览视图。
 */
public class HealthSummaryVO {

    private LocalDate latestMeasureDate;
    private LocalDate lastExamDate;
    private LocalDate lastVisitDate;
    private Integer recordCount;
    private Integer reportCount;
    private Integer visitCount;

    public LocalDate getLatestMeasureDate() {
        return latestMeasureDate;
    }

    public void setLatestMeasureDate(LocalDate latestMeasureDate) {
        this.latestMeasureDate = latestMeasureDate;
    }

    public LocalDate getLastExamDate() {
        return lastExamDate;
    }

    public void setLastExamDate(LocalDate lastExamDate) {
        this.lastExamDate = lastExamDate;
    }

    public LocalDate getLastVisitDate() {
        return lastVisitDate;
    }

    public void setLastVisitDate(LocalDate lastVisitDate) {
        this.lastVisitDate = lastVisitDate;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    public Integer getReportCount() {
        return reportCount;
    }

    public void setReportCount(Integer reportCount) {
        this.reportCount = reportCount;
    }

    public Integer getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(Integer visitCount) {
        this.visitCount = visitCount;
    }
}
