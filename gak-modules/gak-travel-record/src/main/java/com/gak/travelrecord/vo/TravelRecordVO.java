package com.gak.travelrecord.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.gak.attachment.vo.AttachmentVO;
import com.gak.personalbills.vo.PersonalBillVO;
/** TravelRecordVO：个人行程视图数据。 */

public class TravelRecordVO {
    private Long id;
    private Long groupId;
    private Long previousRecordId;
    private String transportType;
    private String status;
    private String departure;
    private String arrival;
    private LocalDate departureDate;
    private LocalTime departureTime;
    private LocalDate arrivalDate;
    private LocalTime arrivalTime;
    private String departureZone;
    private String arrivalZone;
    private String serviceNumber;
    private String carrier;
    private String seatClass;
    private String carriage;
    private String seat;
    private String purpose;
    private String platform;
    private String orderNumber;
    private String note;
    private BigDecimal paidAmount;
    private BigDecimal refundAmount;
    private String currency;
    private LocalDate paymentDate;
    private LocalDate refundDate;
    private Boolean useDepartureDate;
    private Boolean syncBill;
    private Integer version;
    private String requestKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AttachmentVO> attachments;
    private List<PersonalBillVO> bills;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
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
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { this.version = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { this.requestKey = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
    public List<AttachmentVO> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentVO> value) { this.attachments = value; }
    public List<PersonalBillVO> getBills() { return bills; }
    public void setBills(List<PersonalBillVO> value) { this.bills = value; }
}
