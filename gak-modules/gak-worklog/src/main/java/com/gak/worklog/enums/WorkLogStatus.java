package com.gak.worklog.enums;

/**
 * 工作内容完成状态。
 */
public enum WorkLogStatus {

    COMPLETED,
    UNFINISHED;

    /**
     * 将接口状态码规范为固定枚举值。
     *
     * @param code 状态码
     * @return 工作内容状态
     */
    public static WorkLogStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("工作内容状态不能为空");
        }
        for (WorkLogStatus status : values()) {
            if (status.name().equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("工作内容状态非法");
    }
}
