package com.gak.worklog.entity;

/**
 * 工作日志类型编码。
 */
public enum WorkLogTypeCode {

    NORMAL("正常"),
    LEAVE("请假"),
    BUSINESS_TRIP("出差"),
    SICK_LEAVE("病假"),
    OTHER("其他");

    private final String label;

    WorkLogTypeCode(String label) {
        this.label = label;
    }

    /**
     * 获取中文展示名。
     *
     * @return 中文展示名
     */
    public String getLabel() {
        return label;
    }

    /**
     * 校验编码是否合法。
     *
     * @param code 编码
     * @return 是否合法
     */
    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (WorkLogTypeCode value : values()) {
            if (value.name().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
