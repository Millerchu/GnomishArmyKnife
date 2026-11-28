package com.gak.healthrecord.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保存健康指标记录请求。
 */
public class SaveHealthRecordRequest {

    @NotNull(message = "measureDate 不能为空")
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
    private BigDecimal waistCm;
    private Integer steps;
    private Integer activeEnergyKcal;
    private Integer exerciseMinutes;
    private BigDecimal distanceKm;
    private BigDecimal sleepHours;
    private BigDecimal bloodOxygen;
    private Integer hrvMs;
    private BigDecimal vo2Max;

    @Size(max = 24, message = "sourceType 长度不能超过 24")
    private String sourceType;

    @Size(max = 255, message = "note 长度不能超过 255")
    private String note;

    @AssertTrue(message = "至少填写一项健康指标")
    public boolean isAnyMetricFilled() {
        return heightCm != null
                || weightKg != null
                || bodyFatRate != null
                || systolicPressure != null
                || diastolicPressure != null
                || totalCholesterol != null
                || triglycerides != null
                || hdlCholesterol != null
                || ldlCholesterol != null
                || fastingGlucose != null
                || heartRate != null
                || uricAcid != null
                || alanineAminotransferase != null
                || aspartateAminotransferase != null
                || gammaGlutamylTransferase != null
                || waistCm != null
                || steps != null
                || activeEnergyKcal != null
                || exerciseMinutes != null
                || distanceKm != null
                || sleepHours != null
                || bloodOxygen != null
                || hrvMs != null
                || vo2Max != null;
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

    public BigDecimal getWaistCm() {
        return waistCm;
    }

    public void setWaistCm(BigDecimal waistCm) {
        this.waistCm = waistCm;
    }

    public Integer getSteps() {
        return steps;
    }

    public void setSteps(Integer steps) {
        this.steps = steps;
    }

    public Integer getActiveEnergyKcal() {
        return activeEnergyKcal;
    }

    public void setActiveEnergyKcal(Integer activeEnergyKcal) {
        this.activeEnergyKcal = activeEnergyKcal;
    }

    public Integer getExerciseMinutes() {
        return exerciseMinutes;
    }

    public void setExerciseMinutes(Integer exerciseMinutes) {
        this.exerciseMinutes = exerciseMinutes;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public BigDecimal getSleepHours() {
        return sleepHours;
    }

    public void setSleepHours(BigDecimal sleepHours) {
        this.sleepHours = sleepHours;
    }

    public BigDecimal getBloodOxygen() {
        return bloodOxygen;
    }

    public void setBloodOxygen(BigDecimal bloodOxygen) {
        this.bloodOxygen = bloodOxygen;
    }

    public Integer getHrvMs() {
        return hrvMs;
    }

    public void setHrvMs(Integer hrvMs) {
        this.hrvMs = hrvMs;
    }

    public BigDecimal getVo2Max() {
        return vo2Max;
    }

    public void setVo2Max(BigDecimal vo2Max) {
        this.vo2Max = vo2Max;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
