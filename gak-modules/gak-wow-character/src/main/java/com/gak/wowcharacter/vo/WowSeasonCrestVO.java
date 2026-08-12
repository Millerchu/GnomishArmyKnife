package com.gak.wowcharacter.vo;

/**
 * WoW 当前赛季纹章上限视图。
 */
public class WowSeasonCrestVO {
    private String name;
    private Integer weeklyCap;

    public WowSeasonCrestVO() {
    }

    public WowSeasonCrestVO(String name, Integer weeklyCap) {
        this.name = name;
        this.weeklyCap = weeklyCap;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getWeeklyCap() { return weeklyCap; }
    public void setWeeklyCap(Integer weeklyCap) { this.weeklyCap = weeklyCap; }
}
