package com.gak.aisubscription.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import java.math.BigDecimal;

/** 智订持久化记录；用户归属仅由服务端写入。 */
@TableName("gak_ai_payment")
public class AiPayment {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long ownerUserId;
    private Long accountId;
    private Long subscriptionId;
    private String type;
    private BigDecimal amount;
    private String currency;
    private LocalDate paymentDate;
    private LocalDate coverageStart;
    private LocalDate coverageEnd;
    private Long originalPaymentId;
    private String requestKey;
    private String note;
    private LocalDateTime recordedAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public LocalDate getCoverageStart() { return coverageStart; }
    public void setCoverageStart(LocalDate coverageStart) { this.coverageStart = coverageStart; }
    public LocalDate getCoverageEnd() { return coverageEnd; }
    public void setCoverageEnd(LocalDate coverageEnd) { this.coverageEnd = coverageEnd; }
    public Long getOriginalPaymentId() { return originalPaymentId; }
    public void setOriginalPaymentId(Long originalPaymentId) { this.originalPaymentId = originalPaymentId; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
