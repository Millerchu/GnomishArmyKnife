package com.gak.todolist.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

/**
 * 更新状态请求。
 */
public class UpdateTodoStatusRequest {

    @NotBlank(message = "status 不能为空")
    @Size(max = 20, message = "status 长度不能超过 20")
    private String status;

    private Boolean completed;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }
}
