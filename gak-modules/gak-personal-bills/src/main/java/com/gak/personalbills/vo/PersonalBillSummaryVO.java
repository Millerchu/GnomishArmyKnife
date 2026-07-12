package com.gak.personalbills.vo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 个人账单概览视图。
 */
public class PersonalBillSummaryVO {

    private BigDecimal currentMonthExpense;
    private BigDecimal currentMonthIncome;
    private BigDecimal currentMonthBalance;
    private BigDecimal currentYearExpense;
    private BigDecimal annualBudgetAmount;
    private BigDecimal annualBudgetUsed;
    private BigDecimal annualBudgetRemaining;
    private BigDecimal annualBudgetUsageRate;
    private List<PersonalBillCategoryDistributionVO> categoryDistribution = new ArrayList<>();
    private List<PersonalBillVO> recentBills = new ArrayList<>();
    private List<PersonalBudgetProgressVO> budgetProgressList = new ArrayList<>();
    private PersonalBillMonthComparisonVO monthComparison;
    private List<PersonalBillDailyTrendVO> dailyTrend = new ArrayList<>();

    public BigDecimal getCurrentMonthExpense() {
        return currentMonthExpense;
    }

    public void setCurrentMonthExpense(BigDecimal currentMonthExpense) {
        this.currentMonthExpense = currentMonthExpense;
    }

    public BigDecimal getCurrentMonthIncome() {
        return currentMonthIncome;
    }

    public void setCurrentMonthIncome(BigDecimal currentMonthIncome) {
        this.currentMonthIncome = currentMonthIncome;
    }

    public BigDecimal getCurrentMonthBalance() {
        return currentMonthBalance;
    }

    public void setCurrentMonthBalance(BigDecimal currentMonthBalance) {
        this.currentMonthBalance = currentMonthBalance;
    }

    public BigDecimal getCurrentYearExpense() {
        return currentYearExpense;
    }

    public void setCurrentYearExpense(BigDecimal currentYearExpense) {
        this.currentYearExpense = currentYearExpense;
    }

    public BigDecimal getAnnualBudgetAmount() {
        return annualBudgetAmount;
    }

    public void setAnnualBudgetAmount(BigDecimal annualBudgetAmount) {
        this.annualBudgetAmount = annualBudgetAmount;
    }

    public BigDecimal getAnnualBudgetUsed() {
        return annualBudgetUsed;
    }

    public void setAnnualBudgetUsed(BigDecimal annualBudgetUsed) {
        this.annualBudgetUsed = annualBudgetUsed;
    }

    public BigDecimal getAnnualBudgetRemaining() {
        return annualBudgetRemaining;
    }

    public void setAnnualBudgetRemaining(BigDecimal annualBudgetRemaining) {
        this.annualBudgetRemaining = annualBudgetRemaining;
    }

    public BigDecimal getAnnualBudgetUsageRate() {
        return annualBudgetUsageRate;
    }

    public void setAnnualBudgetUsageRate(BigDecimal annualBudgetUsageRate) {
        this.annualBudgetUsageRate = annualBudgetUsageRate;
    }

    public List<PersonalBillCategoryDistributionVO> getCategoryDistribution() {
        return categoryDistribution;
    }

    public void setCategoryDistribution(List<PersonalBillCategoryDistributionVO> categoryDistribution) {
        this.categoryDistribution = categoryDistribution;
    }

    public List<PersonalBillVO> getRecentBills() {
        return recentBills;
    }

    public void setRecentBills(List<PersonalBillVO> recentBills) {
        this.recentBills = recentBills;
    }

    public List<PersonalBudgetProgressVO> getBudgetProgressList() {
        return budgetProgressList;
    }

    public void setBudgetProgressList(List<PersonalBudgetProgressVO> budgetProgressList) {
        this.budgetProgressList = budgetProgressList;
    }

    public PersonalBillMonthComparisonVO getMonthComparison() {
        return monthComparison;
    }

    public void setMonthComparison(PersonalBillMonthComparisonVO monthComparison) {
        this.monthComparison = monthComparison;
    }

    public List<PersonalBillDailyTrendVO> getDailyTrend() {
        return dailyTrend;
    }

    public void setDailyTrend(List<PersonalBillDailyTrendVO> dailyTrend) {
        this.dailyTrend = dailyTrend;
    }
}
