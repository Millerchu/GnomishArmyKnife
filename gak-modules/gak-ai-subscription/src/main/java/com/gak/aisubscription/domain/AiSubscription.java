package com.gak.aisubscription.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import java.math.BigDecimal;

/** 智订持久化记录；用户归属仅由服务端写入。 */
@TableName("gak_ai_subscription")
public class AiSubscription {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long ownerUserId;
    private Long accountId;
    private String planName;
    private LocalDate startDate;
    private LocalDate endDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate nextBillingDate;
    private Integer cycleMonths;
    private BigDecimal amount;
    private String currency;
    private Boolean autoRenew;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate trialEndDate;
    private Integer reminderDays;
    private Boolean terminated;
    private LocalDateTime recordedAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public LocalDate getNextBillingDate() { return nextBillingDate; }
    public void setNextBillingDate(LocalDate nextBillingDate) { this.nextBillingDate = nextBillingDate; }
    public Integer getCycleMonths() { return cycleMonths; }
    public void setCycleMonths(Integer cycleMonths) { this.cycleMonths = cycleMonths; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Boolean getAutoRenew() { return autoRenew; }
    public void setAutoRenew(Boolean autoRenew) { this.autoRenew = autoRenew; }
    public LocalDate getTrialEndDate() { return trialEndDate; }
    public void setTrialEndDate(LocalDate trialEndDate) { this.trialEndDate = trialEndDate; }
    public Integer getReminderDays() { return reminderDays; }
    public void setReminderDays(Integer reminderDays) { this.reminderDays = reminderDays; }
    public Boolean getTerminated() { return terminated; }
    public void setTerminated(Boolean terminated) { this.terminated = terminated; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
