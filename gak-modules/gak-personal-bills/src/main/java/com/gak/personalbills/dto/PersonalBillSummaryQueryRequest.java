package com.gak.personalbills.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 个人账单概览查询参数。
 */
public class PersonalBillSummaryQueryRequest {

    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "month 格式必须为 yyyy-MM")
    private String month;

    @Min(value = 1, message = "year 必须大于零")
    @Max(value = 9999, message = "year 不能晚于 9999")
    private Integer year;

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }
    private String currency = "CNY";

    public String getCurrency() { return currency; }

    public void setCurrency(String currency) { this.currency = currency; }

}
