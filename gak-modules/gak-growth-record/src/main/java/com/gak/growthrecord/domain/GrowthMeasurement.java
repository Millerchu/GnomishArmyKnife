package com.gak.growthrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** GrowthMeasurement 数据库映射。可选字段更新时允许显式清空。 */
@TableName("gak_growth_measurement")
public class GrowthMeasurement {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long ownerUserId;
    private Long childId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate measureDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal heightCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal weightKg;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal headCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String recordedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }

    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}


