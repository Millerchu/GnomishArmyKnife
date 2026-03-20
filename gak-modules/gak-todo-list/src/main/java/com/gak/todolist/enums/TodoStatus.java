package com.gak.todolist.enums;

/**
 * 待办状态。
 */
public enum TodoStatus {

    TODO,
    IN_PROGRESS,
    COMPLETED;

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (TodoStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String value) {
        return value.trim().toUpperCase();
    }
}
