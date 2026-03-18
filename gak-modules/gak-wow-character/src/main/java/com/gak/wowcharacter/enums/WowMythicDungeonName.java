package com.gak.wowcharacter.enums;

/**
 * 当前支持的大秘境副本名。
 */
public enum WowMythicDungeonName {

    MAGISTERS_TERRACE("魔导师平台"),
    MYZA_CAVE("迈萨拉洞窟"),
    NODE_XYNAS("节点希纳斯"),
    TOWER_OF_THE_WINDS("风行者之塔"),
    AZJ_KAHET_ACADEMY("艾杰斯亚学院"),
    SARON_MINE("萨隆矿坑"),
    SEAT_OF_THE_TRIUNE("执政团之座"),
    SKYREACH("通天峰");

    private final String label;

    WowMythicDungeonName(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        for (WowMythicDungeonName dungeonName : values()) {
            if (dungeonName.label.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
