package com.gak.healthrecord.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 健康指标记录视图。
 */
public class HealthRecordVO {

    private Long id;
    private LocalDate measureDate;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal bodyFatRate;
    private Integer systolicPressure;
    private Integer diastolicPressure;
    private BigDecimal totalCholesterol;
    private BigDecimal triglycerides;
    private BigDecimal hdlCholesterol;
    private BigDecimal ldlCholesterol;
    private BigDecimal fastingGlucose;
    private Integer heartRate;
    private Integer uricAcid;
    private Integer alanineAminotransferase;
    private Integer aspartateAminotransferase;
    private Integer gammaGlutamylTransferase;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getMeasureDate() {
        return measureDate;
    }

    public void setMeasureDate(LocalDate measureDate) {
        this.measureDate = measureDate;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public BigDecimal getBodyFatRate() {
        return bodyFatRate;
    }

    public void setBodyFatRate(BigDecimal bodyFatRate) {
        this.bodyFatRate = bodyFatRate;
    }

    public Integer getSystolicPressure() {
        return systolicPressure;
    }

    public void setSystolicPressure(Integer systolicPressure) {
        this.systolicPressure = systolicPressure;
    }

    public Integer getDiastolicPressure() {
        return diastolicPressure;
    }

    public void setDiastolicPressure(Integer diastolicPressure) {
        this.diastolicPressure = diastolicPressure;
    }

    public BigDecimal getTotalCholesterol() {
        return totalCholesterol;
    }

    public void setTotalCholesterol(BigDecimal totalCholesterol) {
        this.totalCholesterol = totalCholesterol;
    }

    public BigDecimal getTriglycerides() {
        return triglycerides;
    }

    public void setTriglycerides(BigDecimal triglycerides) {
        this.triglycerides = triglycerides;
    }

    public BigDecimal getHdlCholesterol() {
        return hdlCholesterol;
    }

    public void setHdlCholesterol(BigDecimal hdlCholesterol) {
        this.hdlCholesterol = hdlCholesterol;
    }

    public BigDecimal getLdlCholesterol() {
        return ldlCholesterol;
    }

    public void setLdlCholesterol(BigDecimal ldlCholesterol) {
        this.ldlCholesterol = ldlCholesterol;
    }

    public BigDecimal getFastingGlucose() {
        return fastingGlucose;
    }

    public void setFastingGlucose(BigDecimal fastingGlucose) {
        this.fastingGlucose = fastingGlucose;
    }

    public Integer getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(Integer heartRate) {
        this.heartRate = heartRate;
    }

    public Integer getUricAcid() {
        return uricAcid;
    }

    public void setUricAcid(Integer uricAcid) {
        this.uricAcid = uricAcid;
    }

    public Integer getAlanineAminotransferase() {
        return alanineAminotransferase;
    }

    public void setAlanineAminotransferase(Integer alanineAminotransferase) {
        this.alanineAminotransferase = alanineAminotransferase;
    }

    public Integer getAspartateAminotransferase() {
        return aspartateAminotransferase;
    }

    public void setAspartateAminotransferase(Integer aspartateAminotransferase) {
        this.aspartateAminotransferase = aspartateAminotransferase;
    }

    public Integer getGammaGlutamylTransferase() {
        return gammaGlutamylTransferase;
    }

    public void setGammaGlutamylTransferase(Integer gammaGlutamylTransferase) {
        this.gammaGlutamylTransferase = gammaGlutamylTransferase;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
}
