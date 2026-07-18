package com.gak.fuelstats.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 新增/修改加油记录请求。
 */
public class SaveFuelRecordRequest {

    @NotBlank(message = "vehicleName 不能为空")
    @Size(max = 64, message = "vehicleName 长度不能超过 64")
    private String vehicleName;

    @NotNull(message = "fuelDate 不能为空")
    private LocalDate fuelDate;

    @NotNull(message = "odometerKm 不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "odometerKm 必须大于 0")
    @Digits(integer = 10, fraction = 1, message = "odometerKm 格式非法")
    private BigDecimal odometerKm;

    @NotNull(message = "fuelVolume 不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "fuelVolume 必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "fuelVolume 格式非法")
    private BigDecimal fuelVolume;

    @NotNull(message = "totalAmount 不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "totalAmount 必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "totalAmount 格式非法")
    private BigDecimal totalAmount;

    @NotNull(message = "discountedAmount 不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "discountedAmount 必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "discountedAmount 格式非法")
    private BigDecimal discountedAmount;

    @Digits(integer = 8, fraction = 3, message = "unitPrice 格式非法")
    private BigDecimal unitPrice;

    @NotBlank(message = "fuelType 不能为空")
    @Size(max = 16, message = "fuelType 长度不能超过 16")
    private String fuelType;

    @NotBlank(message = "fillType 不能为空")
    @Size(max = 16, message = "fillType 长度不能超过 16")
    private String fillType;

    @Size(max = 128, message = "stationName 长度不能超过 128")
    private String stationName;

    @Size(max = 500, message = "note 长度不能超过 500")
    private String note;

    @Size(max = 3, message = "加油凭证图片不能超过 3 张")
    private List<Long> attachmentIds;

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

    public List<Long> getAttachmentIds() { return attachmentIds; }

    public void setAttachmentIds(List<Long> attachmentIds) { this.attachmentIds = attachmentIds; }
}
