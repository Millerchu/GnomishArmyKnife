package com.gak.travelrecord.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** TravelRecord：个人行程持久化对象。 */
@TableName("gak_travel_record")
public class TravelRecord {
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long detachedPaymentBillId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long detachedRefundBillId;
    public Long getDetachedPaymentBillId() { return detachedPaymentBillId; }
    public void setDetachedPaymentBillId(Long value) { detachedPaymentBillId = value; }
    public Long getDetachedRefundBillId() { return detachedRefundBillId; }
    public void setDetachedRefundBillId(Long value) { detachedRefundBillId = value; }
    private Boolean billingDetached = false;
    public Boolean getBillingDetached() { return billingDetached; }
    public void setBillingDetached(Boolean value) { billingDetached = value; }
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long ownerUserId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long groupId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long previousRecordId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String transportType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String departure;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String arrival;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate departureDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalTime departureTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate arrivalDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalTime arrivalTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String departureZone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String arrivalZone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String serviceNumber;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String carrier;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String seatClass;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String carriage;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String seat;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String purpose;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String platform;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNumber;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String note;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal paidAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal refundAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currency;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate paymentDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate refundDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean useDepartureDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean syncBill;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String requestKey;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer version;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long value) { this.ownerUserId = value; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long value) { this.groupId = value; }
    public Long getPreviousRecordId() { return previousRecordId; }
    public void setPreviousRecordId(Long value) { this.previousRecordId = value; }
    public String getTransportType() { return transportType; }
    public void setTransportType(String value) { this.transportType = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getDeparture() { return departure; }
    public void setDeparture(String value) { this.departure = value; }
    public String getArrival() { return arrival; }
    public void setArrival(String value) { this.arrival = value; }
    public LocalDate getDepartureDate() { return departureDate; }
    public void setDepartureDate(LocalDate value) { this.departureDate = value; }
    public LocalTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalTime value) { this.departureTime = value; }
    public LocalDate getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(LocalDate value) { this.arrivalDate = value; }
    public LocalTime getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(LocalTime value) { this.arrivalTime = value; }
    public String getDepartureZone() { return departureZone; }
    public void setDepartureZone(String value) { this.departureZone = value; }
    public String getArrivalZone() { return arrivalZone; }
    public void setArrivalZone(String value) { this.arrivalZone = value; }
    public String getServiceNumber() { return serviceNumber; }
    public void setServiceNumber(String value) { this.serviceNumber = value; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String value) { this.carrier = value; }
    public String getSeatClass() { return seatClass; }
    public void setSeatClass(String value) { this.seatClass = value; }
    public String getCarriage() { return carriage; }
    public void setCarriage(String value) { this.carriage = value; }
    public String getSeat() { return seat; }
    public void setSeat(String value) { this.seat = value; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String value) { this.purpose = value; }
    public String getPlatform() { return platform; }
    public void setPlatform(String value) { this.platform = value; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String value) { this.orderNumber = value; }
    public String getNote() { return note; }
    public void setNote(String value) { this.note = value; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal value) { this.paidAmount = value; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal value) { this.refundAmount = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate value) { this.paymentDate = value; }
    public LocalDate getRefundDate() { return refundDate; }
    public void setRefundDate(LocalDate value) { this.refundDate = value; }
    public Boolean getUseDepartureDate() { return useDepartureDate; }
    public void setUseDepartureDate(Boolean value) { this.useDepartureDate = value; }
    public Boolean getSyncBill() { return syncBill; }
    public void setSyncBill(Boolean value) { this.syncBill = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { this.requestKey = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { this.version = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
}
