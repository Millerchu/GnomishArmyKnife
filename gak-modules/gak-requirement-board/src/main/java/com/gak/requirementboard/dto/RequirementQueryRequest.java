package com.gak.requirementboard.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 需求看板分页查询参数。
 */
public class RequirementQueryRequest {

    @Min(value = 1, message = "pageNo 必须大于等于 1")
    private long pageNo = 1L;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    @Max(value = 100, message = "pageSize 不能大于 100")
    private long pageSize = 50L;

    @Size(max = 100, message = "keyword 长度不能超过 100")
    private String keyword;

    @Size(max = 32, message = "status 长度不能超过 32")
    private String status;

    @Size(max = 64, message = "appCode 长度不能超过 64")
    private String appCode;

    @Size(max = 16, message = "priority 长度不能超过 16")
    private String priority;

    @Size(max = 16, message = "type 长度不能超过 16")
    private String type;

    public long getPageNo() {
        return pageNo;
    }

    public void setPageNo(long pageNo) {
        this.pageNo = pageNo;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
