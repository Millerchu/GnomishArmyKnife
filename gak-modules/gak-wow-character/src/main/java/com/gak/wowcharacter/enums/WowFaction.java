package com.gak.wowcharacter.enums;

/**
 * 阵营。
 */
public enum WowFaction {

    ALLIANCE("联盟"),
    HORDE("部落");

    private final String label;

    WowFaction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (WowFaction faction : values()) {
            if (faction.name().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    public static WowFaction from(String value) {
        return WowFaction.valueOf(value.trim().toUpperCase());
    }
}
