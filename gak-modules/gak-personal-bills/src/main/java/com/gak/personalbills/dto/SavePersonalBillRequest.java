package com.gak.personalbills.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保存个人账单请求。
 */
public class SavePersonalBillRequest {

    @NotBlank(message = "billType 不能为空")
    @Size(max = 20, message = "billType 长度不能超过 20")
    private String billType;

    @NotBlank(message = "categoryName 不能为空")
    @Size(max = 64, message = "categoryName 长度不能超过 64")
    private String categoryName;

    @NotNull(message = "amount 不能为空")
    @DecimalMin(value = "0.01", message = "amount 必须大于 0")
    private BigDecimal amount;

    @Size(max = 64, message = "accountName 长度不能超过 64")
    private String accountName;

    @Size(max = 64, message = "paymentMethod 长度不能超过 64")
    private String paymentMethod;

    @Size(max = 96, message = "merchantName 长度不能超过 96")
    private String merchantName;

    @NotNull(message = "billDate 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate billDate;

    @Size(max = 255, message = "note 长度不能超过 255")
    private String note;

    public String getBillType() {
        return billType;
    }

    public void setBillType(String billType) {
        this.billType = billType;
    }

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

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public LocalDate getBillDate() {
        return billDate;
    }

    public void setBillDate(LocalDate billDate) {
        this.billDate = billDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
