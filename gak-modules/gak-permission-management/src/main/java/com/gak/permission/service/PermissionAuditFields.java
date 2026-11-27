package com.gak.permission.service;

/** 保证审计字段符合 PostgreSQL VARCHAR 的字符数限制。 */
final class PermissionAuditFields {
    private PermissionAuditFields() {
    }

    static String ip(String value) {
        return limit(value, 64);
    }

    static String userAgent(String value) {
        return limit(value, 255);
    }

    private static String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.replace("\u0000", "").trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, maxLength));
    }
}
