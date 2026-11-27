package com.gak.healthrecord.enums;

/**
 * 体检报告明细分组枚举。
 */
public enum HealthReportItemGroup {
    GENERAL("一般检查"),
    LIPID("血脂"),
    LIVER("肝功能"),
    KIDNEY("肾功能"),
    CBC("血常规"),
    URINALYSIS("尿常规"),
    IMAGING("影像"),
    ECG("心电图"),
    SPECIALTY("专项检查"),
    OTHER("其他");

    private final String label;

    HealthReportItemGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static HealthReportItemGroup fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return HealthReportItemGroup.valueOf(code.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
