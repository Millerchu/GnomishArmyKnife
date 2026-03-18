package com.gak.wowcharacter.vo;

/**
 * 服务器统计。
 */
public class RealmStatVO {

    private String realmName;
    private long count;
    private int highestItemLevel;

    public String getRealmName() {
        return realmName;
    }

    public void setRealmName(String realmName) {
        this.realmName = realmName;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public int getHighestItemLevel() {
        return highestItemLevel;
    }

    public void setHighestItemLevel(int highestItemLevel) {
        this.highestItemLevel = highestItemLevel;
    }
}
