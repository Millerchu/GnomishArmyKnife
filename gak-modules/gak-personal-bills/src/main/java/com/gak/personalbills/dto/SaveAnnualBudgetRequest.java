package com.gak.personalbills.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 保存年度预算请求。
 */
public class SaveAnnualBudgetRequest {

    @NotNull(message = "year 不能为空")
    @Min(value = 2020, message = "year 不能早于 2020")
    @Max(value = 2099, message = "year 不能晚于 2099")
    private Integer year;

    @NotBlank(message = "categoryName 不能为空")
    @Size(max = 64, message = "categoryName 长度不能超过 64")
    private String categoryName;

    @NotNull(message = "annualLimit 不能为空")
    @DecimalMin(value = "0.01", message = "annualLimit 必须大于 0")
    private BigDecimal annualLimit;

    @NotNull(message = "alertThreshold 不能为空")
    @DecimalMin(value = "0.01", message = "alertThreshold 必须大于 0")
    @DecimalMax(value = "1.00", message = "alertThreshold 不能大于 1")
    private BigDecimal alertThreshold;

    @Size(max = 120, message = "note 长度不能超过 120")
    private String note;

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
