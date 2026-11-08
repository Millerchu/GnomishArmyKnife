package com.gak.systemlog.enums;

/**
 * 异常采集来源。只有这三种会入库。
 */
public enum SystemExceptionSource {
    HTTP,
    ASYNC_TASK,
    SCHEDULER;

    public static boolean contains(String source) {
        if (source == null) {
            return false;
        }
        for (SystemExceptionSource value : values()) {
            if (value.name().equals(source)) {
                return true;
            }
        }
        return false;
    }
}
