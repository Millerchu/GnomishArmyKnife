package com.gak.wowcharacter.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/**
 * WoW 角色列表项。
 */
public class WowCharacterListVO {

    private Long id;
    private String characterName;
    private String className;
    private String specName;
    private String raceName;
    private String realmName;
    private String faction;
    private Integer level;
    private Integer itemLevel;
    private Integer mythicBestLevel;
    private String mythicDungeonName;
    private Integer mythicScore;
    private String professionPrimary;
    private String professionSecondary;
    private String note;

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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
