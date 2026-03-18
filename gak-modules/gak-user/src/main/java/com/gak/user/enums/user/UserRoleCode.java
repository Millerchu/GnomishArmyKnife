package com.gak.user.enums.user;

/**
 * 用户角色编码。
 */
public enum UserRoleCode {

    ADMIN,
    DEV,
    USER;

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (UserRoleCode value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String code) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("非法角色编码");
        }
        return code.trim().toUpperCase();
    }
}
