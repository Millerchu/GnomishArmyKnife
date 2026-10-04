package com.gak.growthrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.gak.growthrecord.enums.VaccinationStatus;

/** GrowthVaccination 数据库映射。可选字段更新时允许显式清空。 */
@TableName("gak_growth_vaccination")
public class GrowthVaccination {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long ownerUserId;
    private Long childId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String vaccineName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer dose;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate vaccinationDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private VaccinationStatus status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String institution;
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

    public String getVaccineName() { return vaccineName; }
    public void setVaccineName(String vaccineName) { this.vaccineName = vaccineName; }

    public Integer getDose() { return dose; }
    public void setDose(Integer dose) { this.dose = dose; }

    public LocalDate getVaccinationDate() { return vaccinationDate; }
    public void setVaccinationDate(LocalDate vaccinationDate) { this.vaccinationDate = vaccinationDate; }

    public VaccinationStatus getStatus() { return status; }
    public void setStatus(VaccinationStatus status) { this.status = status; }

    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}


