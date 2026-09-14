package com.gak.personalbills.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;
/** 个人信用授信账户请求。 */
public class SaveCreditAccountRequest {
    @NotBlank @Size(max = 64)
    private String accountName;
    @NotBlank @Size(max = 64)
    private String institution;
    @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
    private BigDecimal creditLimit;
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
    private BigDecimal usedAmount;
    @Min(1) @Max(31)
    private Integer billingDay;
    @Min(1) @Max(31)
    private Integer repaymentDay;
    @Size(max = 160)
    private String note;

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
}
