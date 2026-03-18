package com.gak.wowcharacter.enums;

/**
 * 正式服职业。
 */
public enum WowClassName {

    DEATH_KNIGHT("死亡骑士"),
    DEMON_HUNTER("恶魔猎手"),
    DRUID("德鲁伊"),
    EVOKER("唤魔师"),
    HUNTER("猎人"),
    MAGE("法师"),
    MONK("武僧"),
    PALADIN("圣骑士"),
    PRIEST("牧师"),
    ROGUE("潜行者"),
    SHAMAN("萨满"),
    WARLOCK("术士"),
    WARRIOR("战士");

    private final String label;

    WowClassName(String label) {
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
        for (WowClassName className : values()) {
            if (className.label.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
