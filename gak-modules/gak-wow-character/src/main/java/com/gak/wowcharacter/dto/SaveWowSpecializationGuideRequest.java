package com.gak.wowcharacter.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * WoW 赛季职业专精指南维护请求。
 */
public class SaveWowSpecializationGuideRequest {

    @NotBlank(message = "mythicTalentBuildName 不能为空")
    @Size(max = 128, message = "mythicTalentBuildName 长度不能超过 128")
    private String mythicTalentBuildName;

    @Size(max = 4000, message = "mythicTalentSummary 长度不能超过 4000")
    private String mythicTalentSummary;

    @Size(max = 8000, message = "mythicTalentImportCode 长度不能超过 8000")
    private String mythicTalentImportCode;

    @NotBlank(message = "raidTalentBuildName 不能为空")
    @Size(max = 128, message = "raidTalentBuildName 长度不能超过 128")
    private String raidTalentBuildName;

    @Size(max = 4000, message = "raidTalentSummary 长度不能超过 4000")
    private String raidTalentSummary;

    @Size(max = 8000, message = "raidTalentImportCode 长度不能超过 8000")
    private String raidTalentImportCode;

    @NotBlank(message = "statPriority 不能为空")
    @Size(max = 512, message = "statPriority 长度不能超过 512")
    private String statPriority;

    @Size(max = 8000, message = "rotationNotes 长度不能超过 8000")
    private String rotationNotes;

    @Size(max = 8000, message = "trinketRanking 长度不能超过 8000")
    private String trinketRanking;

    @NotBlank(message = "sourceName 不能为空")
    @Size(max = 128, message = "sourceName 长度不能超过 128")
    private String sourceName;

    @NotBlank(message = "sourceUrl 不能为空")
    @Size(max = 1024, message = "sourceUrl 长度不能超过 1024")
    @Pattern(regexp = "^https?://.+", message = "sourceUrl 必须是 HTTP 或 HTTPS 地址")
    private String sourceUrl;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate sourceUpdatedAt;

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
}
