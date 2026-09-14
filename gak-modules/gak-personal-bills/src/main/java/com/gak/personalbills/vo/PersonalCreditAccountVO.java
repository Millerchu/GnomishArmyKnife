package com.gak.personalbills.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 个人信用授信账户信息。 */
public class PersonalCreditAccountVO {
    private Long id;
    private String accountName;
    private String institution;
    private BigDecimal creditLimit;
    private BigDecimal usedAmount;
    private Integer billingDay;
    private Integer repaymentDay;
    private String note;
    private BigDecimal availableAmount;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }

    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }

    public BigDecimal getUsedAmount() { return usedAmount; }
    public void setUsedAmount(BigDecimal usedAmount) { this.usedAmount = usedAmount; }

    public Integer getBillingDay() { return billingDay; }
    public void setBillingDay(Integer billingDay) { this.billingDay = billingDay; }

    public Integer getRepaymentDay() { return repaymentDay; }
    public void setRepaymentDay(Integer repaymentDay) { this.repaymentDay = repaymentDay; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public BigDecimal getAvailableAmount() { return availableAmount; }
    public void setAvailableAmount(BigDecimal availableAmount) { this.availableAmount = availableAmount; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
