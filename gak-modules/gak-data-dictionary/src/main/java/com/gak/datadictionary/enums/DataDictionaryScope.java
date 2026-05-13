package com.gak.datadictionary.enums;

/**
 * 数据字典作用范围。
 */
public enum DataDictionaryScope {

    PUBLIC,
    PERSONAL;

    public static DataDictionaryScope fromCode(String code) {
        for (DataDictionaryScope item : values()) {
            if (item.name().equalsIgnoreCase(code)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unsupported data dictionary scope: " + code);
    }
}
