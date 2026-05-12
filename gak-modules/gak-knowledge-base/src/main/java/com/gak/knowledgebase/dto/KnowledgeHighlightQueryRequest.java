package com.gak.knowledgebase.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 经验推荐查询参数。
 */
public class KnowledgeHighlightQueryRequest {

    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 12, message = "size 不能大于 12")
    private Integer size = 3;

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
