package com.gak.wowcharacter.vo;

/**
 * WoW 角色简要信息。
 */
public class WowCharacterSimpleVO {

    private Long id;
    private String characterName;
    private String className;
    private String specName;
    private String specNameLabel;
    private String raceName;
    private String realmName;
    private String faction;
    private Integer level;
    private Integer itemLevel;
    private Integer mythicBestLevel;
    private String mythicDungeonName;
    private Integer mythicScore;
    private String professionPrimary;
    private String professionPrimaryLabel;
    private String professionSecondary;
    private String professionSecondaryLabel;

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
}
