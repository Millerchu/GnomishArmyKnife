package com.gak.knowledgebase.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 经验列表查询参数。
 */
public class KnowledgeEntryQueryRequest {

    @Min(value = 1, message = "pageNo 不能小于 1")
    private Integer pageNo = 1;

    @Min(value = 1, message = "pageSize 不能小于 1")
    @Max(value = 100, message = "pageSize 不能大于 100")
    private Integer pageSize = 10;

    @Size(max = 32, message = "view 长度不能超过 32")
    private String view;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }
}
