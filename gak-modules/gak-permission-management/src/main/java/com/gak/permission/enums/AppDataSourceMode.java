package com.gak.permission.enums;

/**
 * 应用数据来源模式。
 */
public enum AppDataSourceMode {

    REAL,
    DEMO;

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (AppDataSourceMode value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String code) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("非法数据来源模式");
        }
        return code.trim().toUpperCase();
    }
}
