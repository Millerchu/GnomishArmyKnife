package com.gak.todolist.enums;

/**
 * 列表视图编码。
 */
public enum TodoViewCode {

    ALL,
    TODAY,
    IMPORTANT,
    COMPLETED;

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (TodoViewCode code : values()) {
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
