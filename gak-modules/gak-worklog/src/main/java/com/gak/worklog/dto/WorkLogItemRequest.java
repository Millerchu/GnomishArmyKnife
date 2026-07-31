package com.gak.worklog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 工作日志内容条目入参。
 */
public class WorkLogItemRequest {

    @NotBlank
    @Size(max = 4000)
    private String content;

    @Size(max = 16)
    private String status;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
