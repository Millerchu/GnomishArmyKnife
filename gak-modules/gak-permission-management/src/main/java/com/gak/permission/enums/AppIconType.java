package com.gak.permission.enums;

/**
 * 应用图标类型。
 */
public enum AppIconType {

    PRESET,
    UPLOAD,
    URL,
    TEXT,
    ;

    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return TEXT.name();
        }
        for (AppIconType value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return value.name();
            }
        }
        throw new IllegalArgumentException("非法应用图标类型");
    }
}
