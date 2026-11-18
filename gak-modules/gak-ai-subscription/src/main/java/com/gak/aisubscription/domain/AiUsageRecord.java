package com.gak.aisubscription.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import java.math.BigDecimal;

/** 智订持久化记录；用户归属仅由服务端写入。 */
@TableName("gak_ai_usage_record")
public class AiUsageRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long ownerUserId;
    private Long accountId;
    private String kind;
    private String mode;
    private String periodKey;
    private String zoneId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String metric;
    private String unit;
    private String dimension;
    private BigDecimal quantity;
    private BigDecimal cost;
    private String currency;
    private String resolution;
    private String source;
    private String externalKey;
    private String status;
    private String reason;
    private LocalDateTime recordedAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getPeriodKey() { return periodKey; }
    public void setPeriodKey(String periodKey) { this.periodKey = periodKey; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getExternalKey() { return externalKey; }
    public void setExternalKey(String externalKey) { this.externalKey = externalKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
