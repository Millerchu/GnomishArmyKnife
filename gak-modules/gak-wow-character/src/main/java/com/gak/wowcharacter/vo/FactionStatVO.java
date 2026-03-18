package com.gak.wowcharacter.vo;

/**
 * 阵营统计。
 */
public class FactionStatVO {

    private String label;
    private long count;
    private double ratio;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public double getRatio() {
        return ratio;
    }

    public void setRatio(double ratio) {
        this.ratio = ratio;
    }
}
