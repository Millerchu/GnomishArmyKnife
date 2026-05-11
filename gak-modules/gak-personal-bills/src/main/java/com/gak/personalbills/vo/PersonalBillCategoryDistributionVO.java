package com.gak.personalbills.vo;

import java.math.BigDecimal;

/**
 * 分类分布视图。
 */
public class PersonalBillCategoryDistributionVO {

    private String categoryName;
    private BigDecimal amount;
    private BigDecimal ratio;

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getRatio() {
        return ratio;
    }

    public void setRatio(BigDecimal ratio) {
        this.ratio = ratio;
    }
}
