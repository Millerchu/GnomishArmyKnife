package com.gak.wowcharacter.vo;

/**
 * 职业统计。
 */
public class ClassStatVO {

    private String className;
    private long count;
    private double averageItemLevel;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public double getAverageItemLevel() {
        return averageItemLevel;
    }

    public void setAverageItemLevel(double averageItemLevel) {
        this.averageItemLevel = averageItemLevel;
    }
}
