package com.gak.fuelstats.vo;

import com.gak.attachment.vo.AttachmentVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 加油记录视图对象。
 */
public class FuelRecordVO {

    private Long id;
    private String vehicleName;
    private LocalDate fuelDate;
    private LocalDateTime fuelTime;
    private BigDecimal odometerKm;
    private BigDecimal fuelVolume;
    private BigDecimal machineUnitPrice;
    private BigDecimal totalAmount;
    private BigDecimal discountedAmount;
    private BigDecimal discountAmount;
    private BigDecimal unitPrice;
    private String fuelType;
    private String fillType;
    private Boolean fuelWarningLight;
    private Boolean lastRecordKnown;
    private String stationName;
    private String note;
    private BigDecimal distanceKm;
    private BigDecimal fuelConsumption;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AttachmentVO> attachments;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVehicleName() {
        return vehicleName;
    }

    public void setVehicleName(String vehicleName) {
        this.vehicleName = vehicleName;
    }

    public LocalDate getFuelDate() {
        return fuelDate;
    }

    public void setFuelDate(LocalDate fuelDate) {
        this.fuelDate = fuelDate;
    }

    public LocalDateTime getFuelTime() {
        return fuelTime;
    }

    public void setFuelTime(LocalDateTime fuelTime) {
        this.fuelTime = fuelTime;
    }

    public BigDecimal getOdometerKm() {
        return odometerKm;
    }

    public void setOdometerKm(BigDecimal odometerKm) {
        this.odometerKm = odometerKm;
    }

    public BigDecimal getFuelVolume() {
        return fuelVolume;
    }

    public void setFuelVolume(BigDecimal fuelVolume) {
        this.fuelVolume = fuelVolume;
    }

    public BigDecimal getMachineUnitPrice() {
        return machineUnitPrice;
    }

    public void setMachineUnitPrice(BigDecimal machineUnitPrice) {
        this.machineUnitPrice = machineUnitPrice;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getDiscountedAmount() {
        return discountedAmount;
    }

    public void setDiscountedAmount(BigDecimal discountedAmount) {
        this.discountedAmount = discountedAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getFuelType() {
        return fuelType;
    }

    public void setFuelType(String fuelType) {
        this.fuelType = fuelType;
    }

    public String getFillType() {
        return fillType;
    }

    public void setFillType(String fillType) {
        this.fillType = fillType;
    }

    public Boolean getFuelWarningLight() {
        return fuelWarningLight;
    }

    public void setFuelWarningLight(Boolean fuelWarningLight) {
        this.fuelWarningLight = fuelWarningLight;
    }

    public Boolean getLastRecordKnown() {
        return lastRecordKnown;
    }

    public void setLastRecordKnown(Boolean lastRecordKnown) {
        this.lastRecordKnown = lastRecordKnown;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public BigDecimal getFuelConsumption() {
        return fuelConsumption;
    }

    public void setFuelConsumption(BigDecimal fuelConsumption) {
        this.fuelConsumption = fuelConsumption;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<AttachmentVO> getAttachments() { return attachments; }

    public void setAttachments(List<AttachmentVO> attachments) { this.attachments = attachments; }
}
