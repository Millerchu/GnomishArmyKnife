package com.gak.datadictionary.enums;

import org.springframework.util.StringUtils;

/**
 * 数据字典通用启停状态。
 */
public enum DataDictionaryStatus {

    ENABLED(true),
    DISABLED(false);

    private final boolean enabled;

    DataDictionaryStatus(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static boolean isValid(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        for (DataDictionaryStatus value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    public static DataDictionaryStatus fromCode(String code) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("非法状态");
        }
        return valueOf(code.trim().toUpperCase());
    }
}
