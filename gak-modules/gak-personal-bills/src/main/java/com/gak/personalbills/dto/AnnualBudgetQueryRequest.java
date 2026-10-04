package com.gak.personalbills.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 年度预算查询参数。
 */
public class AnnualBudgetQueryRequest {

    @Min(value = 1, message = "year 必须大于零")
    @Max(value = 9999, message = "year 不能晚于 9999")
    private Integer year;

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }
}
