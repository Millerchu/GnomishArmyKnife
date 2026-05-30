package com.gak.wowcharacter.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * WoW 角色列表项。
 */
public class WowCharacterListVO {

    private Long id;
    private String characterName;
    private String className;
    private String specName;
    private String specNameLabel;
    private String raceName;
    private String realmName;
    private String faction;
    private Integer level;
    private BigDecimal itemLevel;
    private Boolean isFeatured;
    private Integer mythicBestLevel;
    private String mythicDungeonName;
    private BigDecimal mythicScore;
    private Integer mythicCompletedDungeonCount;
    private String professionPrimary;
    private String professionPrimaryLabel;
    private String professionSecondary;
    private String professionSecondaryLabel;
    private String note;
    private List<WowCharacterMythicRunVO> mythicRuns;
    private List<WowCharacterWeeklyVaultVO> weeklyVaults;
    private List<WowCharacterKeybindingVO> keybindings;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCharacterName() {
        return characterName;
    }

    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getSpecName() {
        return specName;
    }

    public void setSpecName(String specName) {
        this.specName = specName;
    }

    public String getSpecNameLabel() {
        return specNameLabel;
    }

    public void setSpecNameLabel(String specNameLabel) {
        this.specNameLabel = specNameLabel;
    }

    public String getRaceName() {
        return raceName;
    }

    public void setRaceName(String raceName) {
        this.raceName = raceName;
    }

    public String getRealmName() {
        return realmName;
    }

    public void setRealmName(String realmName) {
        this.realmName = realmName;
    }

    public String getFaction() {
        return faction;
    }

    public void setFaction(String faction) {
        this.faction = faction;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public BigDecimal getItemLevel() {
        return itemLevel;
    }

    public void setItemLevel(BigDecimal itemLevel) {
        this.itemLevel = itemLevel;
    }

    public Boolean getIsFeatured() {
        return isFeatured;
    }

    public void setIsFeatured(Boolean isFeatured) {
        this.isFeatured = isFeatured;
    }

    public Integer getMythicBestLevel() {
        return mythicBestLevel;
    }

    public void setMythicBestLevel(Integer mythicBestLevel) {
        this.mythicBestLevel = mythicBestLevel;
    }

    public BigDecimal getMythicScore() {
        return mythicScore;
    }

    public void setMythicScore(BigDecimal mythicScore) {
        this.mythicScore = mythicScore;
    }

    public String getMythicDungeonName() {
        return mythicDungeonName;
    }

    public void setMythicDungeonName(String mythicDungeonName) {
        this.mythicDungeonName = mythicDungeonName;
    }

    public Integer getMythicCompletedDungeonCount() {
        return mythicCompletedDungeonCount;
    }

    public void setMythicCompletedDungeonCount(Integer mythicCompletedDungeonCount) {
        this.mythicCompletedDungeonCount = mythicCompletedDungeonCount;
    }

    public String getProfessionPrimary() {
        return professionPrimary;
    }

    public void setProfessionPrimary(String professionPrimary) {
        this.professionPrimary = professionPrimary;
    }

    public String getProfessionPrimaryLabel() {
        return professionPrimaryLabel;
    }

    public void setProfessionPrimaryLabel(String professionPrimaryLabel) {
        this.professionPrimaryLabel = professionPrimaryLabel;
    }

    public String getProfessionSecondary() {
        return professionSecondary;
    }

    public void setProfessionSecondary(String professionSecondary) {
        this.professionSecondary = professionSecondary;
    }

    public String getProfessionSecondaryLabel() {
        return professionSecondaryLabel;
    }

    public void setProfessionSecondaryLabel(String professionSecondaryLabel) {
        this.professionSecondaryLabel = professionSecondaryLabel;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<WowCharacterMythicRunVO> getMythicRuns() {
        return mythicRuns;
    }

    public void setMythicRuns(List<WowCharacterMythicRunVO> mythicRuns) {
        this.mythicRuns = mythicRuns;
    }

    public List<WowCharacterWeeklyVaultVO> getWeeklyVaults() {
        return weeklyVaults;
    }

    public void setWeeklyVaults(List<WowCharacterWeeklyVaultVO> weeklyVaults) {
        this.weeklyVaults = weeklyVaults;
    }

    public List<WowCharacterKeybindingVO> getKeybindings() {
        return keybindings;
    }

    public void setKeybindings(List<WowCharacterKeybindingVO> keybindings) {
        this.keybindings = keybindings;
    }
}
