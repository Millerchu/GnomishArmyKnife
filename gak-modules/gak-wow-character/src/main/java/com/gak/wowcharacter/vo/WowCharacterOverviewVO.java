package com.gak.wowcharacter.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * WoW 角色概览。
 */
public class WowCharacterOverviewVO {

    private long totalCharacters;
    private long totalRealms;
    private BigDecimal highestItemLevel;
    private BigDecimal highestMythicScore;
    private BigDecimal averageItemLevel;
    private List<WowCharacterSimpleVO> featuredCharacters;
    private List<FactionStatVO> factionStats;
    private List<ClassStatVO> classStats;
    private List<RealmStatVO> realmStats;

    public long getTotalCharacters() {
        return totalCharacters;
    }

    public void setTotalCharacters(long totalCharacters) {
        this.totalCharacters = totalCharacters;
    }

    public long getTotalRealms() {
        return totalRealms;
    }

    public void setTotalRealms(long totalRealms) {
        this.totalRealms = totalRealms;
    }

    public BigDecimal getHighestItemLevel() {
        return highestItemLevel;
    }

    public void setHighestItemLevel(BigDecimal highestItemLevel) {
        this.highestItemLevel = highestItemLevel;
    }

    public BigDecimal getHighestMythicScore() {
        return highestMythicScore;
    }

    public void setHighestMythicScore(BigDecimal highestMythicScore) {
        this.highestMythicScore = highestMythicScore;
    }

    public BigDecimal getAverageItemLevel() {
        return averageItemLevel;
    }

    public void setAverageItemLevel(BigDecimal averageItemLevel) {
        this.averageItemLevel = averageItemLevel;
    }

    public List<WowCharacterSimpleVO> getFeaturedCharacters() {
        return featuredCharacters;
    }

    public void setFeaturedCharacters(List<WowCharacterSimpleVO> featuredCharacters) {
        this.featuredCharacters = featuredCharacters;
    }

    public List<FactionStatVO> getFactionStats() {
        return factionStats;
    }

    public void setFactionStats(List<FactionStatVO> factionStats) {
        this.factionStats = factionStats;
    }

    public List<ClassStatVO> getClassStats() {
        return classStats;
    }

    public void setClassStats(List<ClassStatVO> classStats) {
        this.classStats = classStats;
    }

    public List<RealmStatVO> getRealmStats() {
        return realmStats;
    }

    public void setRealmStats(List<RealmStatVO> realmStats) {
        this.realmStats = realmStats;
    }
}
