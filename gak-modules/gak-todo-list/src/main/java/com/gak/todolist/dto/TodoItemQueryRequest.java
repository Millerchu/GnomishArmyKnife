package com.gak.todolist.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 待办分页查询参数。
 */
public class TodoItemQueryRequest {

    @Min(value = 1, message = "pageNo 必须大于等于 1")
    private Long pageNo = 1L;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Long pageSize = 10L;

    @Size(max = 100, message = "keyword 长度不能超过 100")
    private String keyword;

    @Size(max = 20, message = "listCode 长度不能超过 20")
    private String listCode;

    @Size(max = 20, message = "status 长度不能超过 20")
    private String status;

    @Size(max = 20, message = "importance 长度不能超过 20")
    private String importance;

    @Size(max = 20, message = "viewCode 长度不能超过 20")
    private String viewCode;

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

    public String getListCode() {
        return listCode;
    }

    public void setListCode(String listCode) {
        this.listCode = listCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImportance() {
        return importance;
    }

    public void setImportance(String importance) {
        this.importance = importance;
    }

    public String getViewCode() {
        return viewCode;
    }

    public void setViewCode(String viewCode) {
        this.viewCode = viewCode;
    }
}
