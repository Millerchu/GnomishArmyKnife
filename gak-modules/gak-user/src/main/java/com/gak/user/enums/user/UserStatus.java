package com.gak.user.enums.user;

/**
 * 用户状态。
 */
public enum UserStatus {

    ENABLED(true),
    DISABLED(false);

    private final boolean enabled;

    UserStatus(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (UserStatus value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    public static UserStatus fromCode(String code) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("非法状态编码");
        }
        return UserStatus.valueOf(code.trim().toUpperCase());
    }
}
