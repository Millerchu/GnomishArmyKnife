package com.gak.datadictionary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 数据字典分页查询参数。
 */
public class DataDictionaryQueryRequest {

    @Min(value = 1, message = "pageNo 必须大于等于 1")
    private long pageNo = 1L;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    @Max(value = 100, message = "pageSize 不能大于 100")
    private long pageSize = 10L;

    @Size(max = 64, message = "keyword 长度不能超过 64")
    private String keyword;

    @Size(max = 20, message = "status 长度不能超过 20")
    private String status;

    @Size(max = 64, message = "referenceApp 长度不能超过 64")
    private String referenceApp;

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

    public String getReferenceApp() {
        return referenceApp;
    }

    public void setReferenceApp(String referenceApp) {
        this.referenceApp = referenceApp;
    }
}
