package com.gak.permission.enums;

import com.gak.framework.exception.BusinessException;
import org.springframework.util.StringUtils;

/**
 * 应用启停状态。
 */
public enum SystemAppStatus {

    ENABLED(true),
    DISABLED(false);

    private final boolean enabled;

    SystemAppStatus(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static boolean isValid(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        for (SystemAppStatus value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    public static SystemAppStatus fromCode(String code) {
        if (!isValid(code)) {
            throw new BusinessException("APP_STATUS_INVALID", "status 非法");
        }
        return valueOf(code.trim().toUpperCase());
    }

    public static StatusEnabledPair normalize(String status, Boolean enabled, boolean defaultEnabled) {
        SystemAppStatus resolvedStatus = StringUtils.hasText(status)
                ? fromCode(status)
                : defaultEnabled ? ENABLED : DISABLED;
        boolean resolvedEnabled = enabled != null ? enabled : resolvedStatus.isEnabled();
        if (resolvedEnabled != resolvedStatus.isEnabled()) {
            throw new BusinessException("APP_STATUS_MISMATCH", "status 与 enabled 语义不一致");
        }
        return new StatusEnabledPair(resolvedStatus.name(), resolvedEnabled);
    }

    public record StatusEnabledPair(String status, boolean enabled) {
    }
}
