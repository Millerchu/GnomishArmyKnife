package com.gak.personalbills.vo;

import java.math.BigDecimal;

/**
 * 个人账单月度环比数据。
 */
public class PersonalBillMonthComparisonVO {

    private BigDecimal previousMonthExpense;
    private BigDecimal previousMonthIncome;
    private BigDecimal previousMonthBalance;
    private BigDecimal expenseDifference;
    private BigDecimal incomeDifference;
    private BigDecimal balanceDifference;
    private BigDecimal expenseChangeRate;
    private BigDecimal incomeChangeRate;
    private BigDecimal balanceChangeRate;

    public BigDecimal getPreviousMonthExpense() { return previousMonthExpense; }
    public void setPreviousMonthExpense(BigDecimal value) { this.previousMonthExpense = value; }
    public BigDecimal getPreviousMonthIncome() { return previousMonthIncome; }
    public void setPreviousMonthIncome(BigDecimal value) { this.previousMonthIncome = value; }
    public BigDecimal getPreviousMonthBalance() { return previousMonthBalance; }
    public void setPreviousMonthBalance(BigDecimal value) { this.previousMonthBalance = value; }
    public BigDecimal getExpenseDifference() { return expenseDifference; }
    public void setExpenseDifference(BigDecimal value) { this.expenseDifference = value; }
    public BigDecimal getIncomeDifference() { return incomeDifference; }
    public void setIncomeDifference(BigDecimal value) { this.incomeDifference = value; }
    public BigDecimal getBalanceDifference() { return balanceDifference; }
    public void setBalanceDifference(BigDecimal value) { this.balanceDifference = value; }
    public BigDecimal getExpenseChangeRate() { return expenseChangeRate; }
    public void setExpenseChangeRate(BigDecimal value) { this.expenseChangeRate = value; }
    public BigDecimal getIncomeChangeRate() { return incomeChangeRate; }
    public void setIncomeChangeRate(BigDecimal value) { this.incomeChangeRate = value; }
    public BigDecimal getBalanceChangeRate() { return balanceChangeRate; }
    public void setBalanceChangeRate(BigDecimal value) { this.balanceChangeRate = value; }
}
