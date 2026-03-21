package com.gak.permission.enums;

/**
 * 应用图标存储类型。
 */
public enum AppIconStorageType {

    FILE_SERVER,
    DB,
    OSS,
    MINIO;

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (AppIconStorageType value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String code) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("非法图标存储类型");
        }
        return code.trim().toUpperCase();
    }
}
