package com.gak.wowcharacter.vo;

import java.math.BigDecimal;

/**
 * WoW 角色单副本赛季记录视图。
 */
public class WowCharacterMythicRunVO {

    private String dungeonName;
    private Integer bestTimedLevel;
    private BigDecimal score;

    public String getDungeonName() {
        return dungeonName;
    }

    public void setDungeonName(String dungeonName) {
        this.dungeonName = dungeonName;
    }

    public Integer getBestTimedLevel() {
        return bestTimedLevel;
    }

    public void setBestTimedLevel(Integer bestTimedLevel) {
        this.bestTimedLevel = bestTimedLevel;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }
}
