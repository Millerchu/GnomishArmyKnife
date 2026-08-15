package com.gak.wowcharacter.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * WoW 赛季职业专精指南实体。
 */
@TableName("gak_wow_specialization_guide")
public class WowSpecializationGuide {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String seasonCode;
    private String classCode;
    private String className;
    private String specCode;
    private String specName;
    private String roleType;
    private Integer sortNo;
    private String mythicTalentBuildName;
    private String mythicTalentSummary;
    private String mythicTalentImportCode;
    private String raidTalentBuildName;
    private String raidTalentSummary;
    private String raidTalentImportCode;
    private String statPriority;
    private String rotationNotes;
    private String trinketRanking;
    private String sourceName;
    private String sourceUrl;
    private LocalDate sourceUpdatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSeasonCode() { return seasonCode; }
    public void setSeasonCode(String seasonCode) { this.seasonCode = seasonCode; }
    public String getClassCode() { return classCode; }
    public void setClassCode(String classCode) { this.classCode = classCode; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public String getSpecName() { return specName; }
    public void setSpecName(String specName) { this.specName = specName; }
    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getMythicTalentBuildName() { return mythicTalentBuildName; }
    public void setMythicTalentBuildName(String mythicTalentBuildName) { this.mythicTalentBuildName = mythicTalentBuildName; }
    public String getMythicTalentSummary() { return mythicTalentSummary; }
    public void setMythicTalentSummary(String mythicTalentSummary) { this.mythicTalentSummary = mythicTalentSummary; }
    public String getMythicTalentImportCode() { return mythicTalentImportCode; }
    public void setMythicTalentImportCode(String mythicTalentImportCode) { this.mythicTalentImportCode = mythicTalentImportCode; }
    public String getRaidTalentBuildName() { return raidTalentBuildName; }
    public void setRaidTalentBuildName(String raidTalentBuildName) { this.raidTalentBuildName = raidTalentBuildName; }
    public String getRaidTalentSummary() { return raidTalentSummary; }
    public void setRaidTalentSummary(String raidTalentSummary) { this.raidTalentSummary = raidTalentSummary; }
    public String getRaidTalentImportCode() { return raidTalentImportCode; }
    public void setRaidTalentImportCode(String raidTalentImportCode) { this.raidTalentImportCode = raidTalentImportCode; }
    public String getStatPriority() { return statPriority; }
    public void setStatPriority(String statPriority) { this.statPriority = statPriority; }
    public String getRotationNotes() { return rotationNotes; }
    public void setRotationNotes(String rotationNotes) { this.rotationNotes = rotationNotes; }
    public String getTrinketRanking() { return trinketRanking; }
    public void setTrinketRanking(String trinketRanking) { this.trinketRanking = trinketRanking; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public LocalDate getSourceUpdatedAt() { return sourceUpdatedAt; }
    public void setSourceUpdatedAt(LocalDate sourceUpdatedAt) { this.sourceUpdatedAt = sourceUpdatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
