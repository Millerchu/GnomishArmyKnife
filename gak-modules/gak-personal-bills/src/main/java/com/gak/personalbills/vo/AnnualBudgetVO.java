package com.gak.personalbills.vo;

import java.math.BigDecimal;

/**
 * 年度预算视图。
 */
public class AnnualBudgetVO {

    private Long id;
    private Integer year;
    private String categoryName;
    private BigDecimal annualLimit;
    private BigDecimal alertThreshold;
    private String note;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public BigDecimal getAnnualLimit() {
        return annualLimit;
    }

    public void setAnnualLimit(BigDecimal annualLimit) {
        this.annualLimit = annualLimit;
    }

    public BigDecimal getAlertThreshold() {
        return alertThreshold;
    }

    public void setAlertThreshold(BigDecimal alertThreshold) {
        this.alertThreshold = alertThreshold;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
