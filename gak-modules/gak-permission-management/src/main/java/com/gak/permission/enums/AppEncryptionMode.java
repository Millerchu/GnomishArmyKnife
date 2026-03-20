package com.gak.permission.enums;

/**
 * 应用加密模式。
 */
public enum AppEncryptionMode {

    NONE,
    FIELD,
    END_TO_END;

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (AppEncryptionMode value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String code) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("非法应用加密模式");
        }
        return code.trim().toUpperCase();
    }
}
