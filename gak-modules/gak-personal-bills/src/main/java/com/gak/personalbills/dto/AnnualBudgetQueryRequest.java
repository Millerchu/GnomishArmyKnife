package com.gak.personalbills.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 年度预算查询参数。
 */
public class AnnualBudgetQueryRequest {

    @Min(value = 2020, message = "year 不能早于 2020")
    @Max(value = 2099, message = "year 不能晚于 2099")
    private Integer year;

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }
}
