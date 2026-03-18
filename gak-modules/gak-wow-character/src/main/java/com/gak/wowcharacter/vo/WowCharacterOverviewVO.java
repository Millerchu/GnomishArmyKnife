package com.gak.wowcharacter.vo;

import java.util.List;

/**
 * WoW 角色概览。
 */
public class WowCharacterOverviewVO {

    private long totalCharacters;
    private long totalRealms;
    private int highestItemLevel;
    private int highestMythicScore;
    private double averageItemLevel;
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

    public int getHighestItemLevel() {
        return highestItemLevel;
    }

    public void setHighestItemLevel(int highestItemLevel) {
        this.highestItemLevel = highestItemLevel;
    }

    public int getHighestMythicScore() {
        return highestMythicScore;
    }

    public void setHighestMythicScore(int highestMythicScore) {
        this.highestMythicScore = highestMythicScore;
    }

    public double getAverageItemLevel() {
        return averageItemLevel;
    }

    public void setAverageItemLevel(double averageItemLevel) {
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
