package com.gak.personalbills.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 个人账单每日收支趋势。
 */
public class PersonalBillDailyTrendVO {

    private LocalDate date;
    private BigDecimal expense;
    private BigDecimal income;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getExpense() {
        return expense;
    }

    public void setExpense(BigDecimal expense) {
        this.expense = expense;
    }

    public BigDecimal getIncome() {
        return income;
    }

    public void setIncome(BigDecimal income) {
        this.income = income;
    }
}
