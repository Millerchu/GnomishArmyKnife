package com.gak.wowcharacter.vo;

import java.util.List;

/**
 * WoW 当前版本和赛季资讯视图。
 */
public class WowSeasonInfoVO {
    private String versionName;
    private String seasonCode;
    private String seasonName;
    private String headline;
    private String summary;
    private List<String> highlights;
    private List<String> dungeons;
    private List<WowSeasonCrestVO> crestLimits;
    private String worldBoss;
    private String holidayThisWeek;
    private String holidayNextWeek;
    private String catalystLimit;
    private String sparkLimit;
    private String specialEvent;
    private String specialEventDateRange;

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public String getSeasonCode() { return seasonCode; }
    public void setSeasonCode(String seasonCode) { this.seasonCode = seasonCode; }
    public String getSeasonName() { return seasonName; }
    public void setSeasonName(String seasonName) { this.seasonName = seasonName; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<String> getHighlights() { return highlights; }
    public void setHighlights(List<String> highlights) { this.highlights = highlights; }
    public List<String> getDungeons() { return dungeons; }
    public void setDungeons(List<String> dungeons) { this.dungeons = dungeons; }
    public List<WowSeasonCrestVO> getCrestLimits() { return crestLimits; }
    public void setCrestLimits(List<WowSeasonCrestVO> crestLimits) { this.crestLimits = crestLimits; }
    public String getWorldBoss() { return worldBoss; }
    public void setWorldBoss(String worldBoss) { this.worldBoss = worldBoss; }
    public String getHolidayThisWeek() { return holidayThisWeek; }
    public void setHolidayThisWeek(String holidayThisWeek) { this.holidayThisWeek = holidayThisWeek; }
    public String getHolidayNextWeek() { return holidayNextWeek; }
    public void setHolidayNextWeek(String holidayNextWeek) { this.holidayNextWeek = holidayNextWeek; }
    public String getCatalystLimit() { return catalystLimit; }
    public void setCatalystLimit(String catalystLimit) { this.catalystLimit = catalystLimit; }
    public String getSparkLimit() { return sparkLimit; }
    public void setSparkLimit(String sparkLimit) { this.sparkLimit = sparkLimit; }
    public String getSpecialEvent() { return specialEvent; }
    public void setSpecialEvent(String specialEvent) { this.specialEvent = specialEvent; }
    public String getSpecialEventDateRange() { return specialEventDateRange; }
    public void setSpecialEventDateRange(String specialEventDateRange) { this.specialEventDateRange = specialEventDateRange; }
}
