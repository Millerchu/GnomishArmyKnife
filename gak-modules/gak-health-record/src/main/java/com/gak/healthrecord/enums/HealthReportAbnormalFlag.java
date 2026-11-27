package com.gak.healthrecord.enums;

/**
 * 体检报告明细异常标记枚举。
 */
public enum HealthReportAbnormalFlag {
    UNSPECIFIED,
    NORMAL,
    HIGH,
    LOW,
    ABNORMAL;

    public static HealthReportAbnormalFlag fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return HealthReportAbnormalFlag.valueOf(code.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
