package com.gak.growthrecord.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 保存GrowthMeasurement的输入；用户与宝宝归属由接口路径和登录态确定。 */
public class SaveGrowthMeasurementRequest {
    @NotNull(message = "测量日期不能为空")
    @PastOrPresent(message = "测量日期不能晚于今天")
    private LocalDate measureDate;

    @DecimalMin(value = "0", inclusive = false, message = "测量数值必须大于0")
    @Digits(integer = 4, fraction = 2, message = "测量数值最多4位整数、2位小数")
    private BigDecimal heightCm;

    @DecimalMin(value = "0", inclusive = false, message = "测量数值必须大于0")
    @Digits(integer = 4, fraction = 2, message = "测量数值最多4位整数、2位小数")
    private BigDecimal weightKg;

    @DecimalMin(value = "0", inclusive = false, message = "测量数值必须大于0")
    @Digits(integer = 4, fraction = 2, message = "测量数值最多4位整数、2位小数")
    private BigDecimal headCm;

    @Size(max = 64, message = "记录人不能超过64字")
    private String recordedBy;

    @Size(max = 1000, message = "备注不能超过1000字")
    private String note;

    @AssertTrue(message = "身高、体重、头围至少填写一项")
    public boolean isAnyMetricFilled() {
        return heightCm != null || weightKg != null || headCm != null;
    }

    public LocalDate getMeasureDate() { return measureDate; }
    public void setMeasureDate(LocalDate measureDate) { this.measureDate = measureDate; }

    public BigDecimal getHeightCm() { return heightCm; }
    public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }

    public BigDecimal getHeadCm() { return headCm; }
    public void setHeadCm(BigDecimal headCm) { this.headCm = headCm; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}


