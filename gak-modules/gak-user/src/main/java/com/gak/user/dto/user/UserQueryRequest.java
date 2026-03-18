package com.gak.user.dto.user;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 用户列表查询参数。
 */
public class UserQueryRequest {

    @Min(value = 1, message = "pageNo 必须大于等于 1")
    private Long pageNo = 1L;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Long pageSize = 10L;

    @Size(max = 100, message = "keyword 长度不能超过 100")
    private String keyword;

    @Size(max = 20, message = "status 长度不能超过 20")
    private String status;

    @Size(max = 20, message = "roleCode 长度不能超过 20")
    private String roleCode;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }
}
