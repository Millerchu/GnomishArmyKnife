package com.gak.user.service.user;

import com.gak.framework.exception.BusinessException;
import com.gak.user.enums.user.UserRoleCode;
import com.gak.user.enums.user.UserStatus;
import org.springframework.util.StringUtils;

/**
 * 用户字段校验与归一化。
 */
final class UserValidationSupport {

    private UserValidationSupport() {
    }

    static String normalizeRoleCode(String roleCode, String defaultRoleCode) {
        String resolved = StringUtils.hasText(roleCode) ? roleCode.trim().toUpperCase() : defaultRoleCode;
        if (!UserRoleCode.isValid(resolved)) {
            throw new BusinessException("ROLE_CODE_INVALID", "roleCode 非法");
        }
        return resolved;
    }

    static StatusEnabledPair normalizeStatusEnabled(String status, Boolean enabled, UserStatus defaultStatus) {
        String resolvedStatusCode = StringUtils.hasText(status) ? status.trim().toUpperCase() : defaultStatus.name();
        if (!UserStatus.isValid(resolvedStatusCode)) {
            throw new BusinessException("USER_STATUS_INVALID", "status 非法");
        }

        UserStatus resolvedStatus = UserStatus.fromCode(resolvedStatusCode);
        boolean resolvedEnabled = enabled != null ? enabled : resolvedStatus.isEnabled();
        if (resolvedEnabled != resolvedStatus.isEnabled()) {
            throw new BusinessException("USER_STATUS_MISMATCH", "status 与 enabled 语义不一致");
        }
        return new StatusEnabledPair(resolvedStatus.name(), resolvedEnabled);
    }

    static String resolvePassword(String initialPassword, String password, boolean required) {
        boolean hasInitialPassword = StringUtils.hasText(initialPassword);
        boolean hasPassword = StringUtils.hasText(password);
        if (hasInitialPassword && hasPassword && !initialPassword.equals(password)) {
            throw new BusinessException("PASSWORD_MISMATCH", "password 与 initialPassword 不一致");
        }
        String resolvedPassword = hasInitialPassword ? initialPassword : password;
        if (required && !StringUtils.hasText(resolvedPassword)) {
            throw new BusinessException("PASSWORD_REQUIRED", "密码不能为空");
        }
        return resolvedPassword;
    }

    static void validatePasswordLength(String password) {
        if (!StringUtils.hasText(password) || password.length() < 6) {
            throw new BusinessException("PASSWORD_TOO_SHORT", "密码长度不能少于 6 位");
        }
    }

    static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    record StatusEnabledPair(String status, boolean enabled) {
    }
}
