package com.gak.travelrecord.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** SaveTravelRecordRequest：个人行程请求参数。 */

public class SaveTravelRecordRequest {
    private Long groupId;
    private Long previousRecordId;
    @NotBlank
    @Size(max = 96)
    private String transportType;
    @NotBlank
    @Size(max = 96)
    private String status;
    @NotBlank
    @Size(max = 96)
    private String departure;
    @NotBlank
    @Size(max = 96)
    private String arrival;
    @NotNull
    private LocalDate departureDate;
    private LocalTime departureTime;
    private LocalDate arrivalDate;
    private LocalTime arrivalTime;
    @NotBlank
    @Size(max = 96)
    private String departureZone;
    @NotBlank
    @Size(max = 96)
    private String arrivalZone;
    @Size(max = 96)
    private String serviceNumber;
    @Size(max = 96)
    private String carrier;
    @Size(max = 96)
    private String seatClass;
    @Size(max = 96)
    private String carriage;
    @Size(max = 96)
    private String seat;
    @Size(max = 96)
    private String purpose;
    @Size(max = 96)
    private String platform;
    @Size(max = 96)
    private String orderNumber;
    @Size(max = 1000)
    private String note;
    @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
    private BigDecimal paidAmount;
    @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
    private BigDecimal refundAmount;
    @NotBlank
    @Size(max = 3)
    private String currency;
    private LocalDate paymentDate;
    private LocalDate refundDate;
    @NotNull
    private Boolean useDepartureDate;
    @NotNull
    private Boolean syncBill;
    private Long existingBillId;
    private Integer version;
    @NotBlank
    @Size(max = 64)
    private String requestKey;
    private Boolean duplicateConfirmed;
    @Size(max = 9)
    private List<Long> attachmentIds;
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
    public Long getExistingBillId() { return existingBillId; }
    public void setExistingBillId(Long value) { this.existingBillId = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { this.version = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { this.requestKey = value; }
    public Boolean getDuplicateConfirmed() { return duplicateConfirmed; }
    public void setDuplicateConfirmed(Boolean value) { this.duplicateConfirmed = value; }
    public List<Long> getAttachmentIds() { return attachmentIds; }
    public void setAttachmentIds(List<Long> value) { this.attachmentIds = value; }
}
