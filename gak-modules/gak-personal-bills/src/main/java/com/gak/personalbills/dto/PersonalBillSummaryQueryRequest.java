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

    @Min(value = 2020, message = "year 不能早于 2020")
    @Max(value = 2099, message = "year 不能晚于 2099")
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
}
