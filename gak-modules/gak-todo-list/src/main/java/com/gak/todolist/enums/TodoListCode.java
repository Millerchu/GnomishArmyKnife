package com.gak.todolist.enums;

/**
 * 待办清单编码。
 */
public enum TodoListCode {

    MY_DAY,
    WORK,
    PERSONAL,
    LEARNING,
    SHOPPING;

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (TodoListCode code : values()) {
            if (code.name().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String value) {
        return value.trim().toUpperCase();
    }
}
