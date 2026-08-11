package com.gak.wowcharacter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * WoW 角色分页查询参数。
 */
public class WowCharacterQueryRequest {

    @Min(value = 1, message = "pageNo 必须大于等于 1")
    private Long pageNo = 1L;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Long pageSize = 10L;

    @Size(max = 64, message = "keyword 长度不能超过 64")
    private String keyword;

    @Size(max = 16, message = "faction 长度不能超过 16")
    private String faction;

    @Size(max = 24, message = "className 长度不能超过 24")
    private String className;

    @Size(max = 32, message = "realmName 长度不能超过 32")
    private String realmName;

    private String sortField;

    private String sortDirection;

    public Long getPageNo() {
        return pageNo;
    }

    public void setPageNo(Long pageNo) {
        this.pageNo = pageNo;
    }

    public Long getPageSize() {
        return pageSize;
    }

    public void setPageSize(Long pageSize) {
        this.pageSize = pageSize;
    }

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

    public String getRealmName() {
        return realmName;
    }

    public void setRealmName(String realmName) {
        this.realmName = realmName;
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }
}
