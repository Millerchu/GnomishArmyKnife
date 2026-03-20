package com.gak.todolist.enums;

/**
 * 重要级别。
 */
public enum TodoImportance {

    LOW,
    MEDIUM,
    HIGH;

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (TodoImportance importance : values()) {
            if (importance.name().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String value) {
        return value.trim().toUpperCase();
    }
}
