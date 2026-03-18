package com.gak.wowcharacter.dto;

import jakarta.validation.constraints.Size;

/**
 * WoW 角色概览查询参数。
 */
public class WowCharacterOverviewQueryRequest {

    @Size(max = 64, message = "keyword 长度不能超过 64")
    private String keyword;

    @Size(max = 16, message = "faction 长度不能超过 16")
    private String faction;

    @Size(max = 24, message = "className 长度不能超过 24")
    private String className;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getFaction() {
        return faction;
    }

    public void setFaction(String faction) {
        this.faction = faction;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }
}
