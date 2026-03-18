package com.gak.wowcharacter.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

/**
 * 新增/编辑 WoW 角色请求。
 */
public class SaveWowCharacterRequest {

    @NotBlank(message = "characterName 不能为空")
    @Size(max = 32, message = "characterName 长度不能超过 32")
    private String characterName;

    @NotBlank(message = "className 不能为空")
    @Size(max = 24, message = "className 长度不能超过 24")
    private String className;

    @Size(max = 24, message = "specName 长度不能超过 24")
    private String specName;

    @NotBlank(message = "raceName 不能为空")
    @Size(max = 24, message = "raceName 长度不能超过 24")
    private String raceName;

    @NotBlank(message = "realmName 不能为空")
    @Size(max = 32, message = "realmName 长度不能超过 32")
    private String realmName;

    @NotBlank(message = "faction 不能为空")
    @Size(max = 16, message = "faction 长度不能超过 16")
    private String faction;

    @NotNull(message = "level 不能为空")
    @Min(value = 1, message = "level 不能小于 1")
    @Max(value = 90, message = "level 不能大于 90")
    private Integer level;

    @NotNull(message = "itemLevel 不能为空")
    @Min(value = 0, message = "itemLevel 不能小于 0")
    private Integer itemLevel;

    @Min(value = 0, message = "mythicBestLevel 不能小于 0")
    private Integer mythicBestLevel;

    @Size(max = 32, message = "mythicDungeonName 长度不能超过 32")
    private String mythicDungeonName;

    @Min(value = 0, message = "mythicScore 不能小于 0")
    private Integer mythicScore;

    @Size(max = 32, message = "professionPrimary 长度不能超过 32")
    private String professionPrimary;

    @Size(max = 32, message = "professionSecondary 长度不能超过 32")
    private String professionSecondary;

    @Size(max = 255, message = "note 长度不能超过 255")
    private String note;

    @AssertTrue(message = "mythicBestLevel > 0 时，mythicDungeonName 必填；mythicDungeonName 非空时，mythicBestLevel 必须 > 0")
    public boolean isMythicDungeonPairValid() {
        int bestLevel = mythicBestLevel == null ? 0 : mythicBestLevel;
        boolean hasDungeonName = StringUtils.hasText(mythicDungeonName);
        if (bestLevel > 0) {
            return hasDungeonName;
        }
        return !hasDungeonName;
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

    public Integer getItemLevel() {
        return itemLevel;
    }

    public void setItemLevel(Integer itemLevel) {
        this.itemLevel = itemLevel;
    }

    public Integer getMythicBestLevel() {
        return mythicBestLevel;
    }

    public void setMythicBestLevel(Integer mythicBestLevel) {
        this.mythicBestLevel = mythicBestLevel;
    }

    public Integer getMythicScore() {
        return mythicScore;
    }

    public void setMythicScore(Integer mythicScore) {
        this.mythicScore = mythicScore;
    }

    public String getMythicDungeonName() {
        return mythicDungeonName;
    }

    public void setMythicDungeonName(String mythicDungeonName) {
        this.mythicDungeonName = mythicDungeonName;
    }

    public String getProfessionPrimary() {
        return professionPrimary;
    }

    public void setProfessionPrimary(String professionPrimary) {
        this.professionPrimary = professionPrimary;
    }

    public String getProfessionSecondary() {
        return professionSecondary;
    }

    public void setProfessionSecondary(String professionSecondary) {
        this.professionSecondary = professionSecondary;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
