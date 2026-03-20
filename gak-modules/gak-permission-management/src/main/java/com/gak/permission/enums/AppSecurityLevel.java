package com.gak.permission.enums;

/**
 * 应用密级。
 */
public enum AppSecurityLevel {

    PUBLIC,
    INTERNAL,
    CONFIDENTIAL;

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (AppSecurityLevel value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String code) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("非法应用密级");
        }
        return code.trim().toUpperCase();
    }
}
