package com.gak.personalbills.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 个人账单分页查询参数。
 */
public class PersonalBillQueryRequest {

    @Min(value = 1, message = "pageNo 必须大于等于 1")
    private long pageNo = 1L;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    @Max(value = 100, message = "pageSize 不能大于 100")
    private long pageSize = 8L;

    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "month 格式必须为 yyyy-MM")
    private String month;

    @Size(max = 20, message = "billType 长度不能超过 20")
    private String billType;

    @Size(max = 64, message = "categoryName 长度不能超过 64")
    private String categoryName;

    @Size(max = 64, message = "keyword 长度不能超过 64")
    private String keyword;

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

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getBillType() {
        return billType;
    }

    public void setBillType(String billType) {
        this.billType = billType;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
