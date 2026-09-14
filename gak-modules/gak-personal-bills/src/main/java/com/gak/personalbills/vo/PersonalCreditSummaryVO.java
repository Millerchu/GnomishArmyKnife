package com.gak.personalbills.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 个人信用授信账户信息。 */
public class PersonalCreditSummaryVO {
    private java.util.List<PersonalCreditAccountVO> accounts;
    private BigDecimal totalLimit;
    private BigDecimal totalUsed;
    private BigDecimal totalAvailable;
    private BigDecimal usageRate;

    public java.util.List<PersonalCreditAccountVO> getAccounts() { return accounts; }
    public void setAccounts(java.util.List<PersonalCreditAccountVO> accounts) { this.accounts = accounts; }

    public BigDecimal getTotalLimit() { return totalLimit; }
    public void setTotalLimit(BigDecimal totalLimit) { this.totalLimit = totalLimit; }

    public BigDecimal getTotalUsed() { return totalUsed; }
    public void setTotalUsed(BigDecimal totalUsed) { this.totalUsed = totalUsed; }

    public BigDecimal getTotalAvailable() { return totalAvailable; }
    public void setTotalAvailable(BigDecimal totalAvailable) { this.totalAvailable = totalAvailable; }

    public BigDecimal getUsageRate() { return usageRate; }
    public void setUsageRate(BigDecimal usageRate) { this.usageRate = usageRate; }
}
