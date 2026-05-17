package com.gak.knowledgebase.dto;

import jakarta.validation.constraints.Size;

/**
 * 经验审核请求。
 */
public class ReviewKnowledgeEntryRequest {

    @Size(max = 200, message = "reviewRemark 长度不能超过 200")
    private String reviewRemark;

    public String getReviewRemark() {
        return reviewRemark;
    }

    public void setReviewRemark(String reviewRemark) {
        this.reviewRemark = reviewRemark;
    }
}
