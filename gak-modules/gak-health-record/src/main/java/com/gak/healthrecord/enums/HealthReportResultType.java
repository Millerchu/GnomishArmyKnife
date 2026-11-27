package com.gak.healthrecord.enums;

/**
 * 体检报告明细结果类型枚举。
 */
public enum HealthReportResultType {
    NUMBER,
    QUALITATIVE,
    TEXT;

    public static HealthReportResultType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return HealthReportResultType.valueOf(code.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
