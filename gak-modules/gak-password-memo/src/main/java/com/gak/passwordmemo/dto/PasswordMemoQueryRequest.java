package com.gak.passwordmemo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 密码备忘录分页查询参数。
 */
public class PasswordMemoQueryRequest {

    @Min(value = 1, message = "pageNo 必须大于等于 1")
    private Long pageNo = 1L;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Long pageSize = 10L;

    @Size(max = 255, message = "keyword 长度不能超过 255")
    private String keyword;

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
}
