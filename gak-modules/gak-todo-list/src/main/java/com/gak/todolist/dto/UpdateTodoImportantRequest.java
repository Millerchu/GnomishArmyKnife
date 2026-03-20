package com.gak.todolist.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 更新重要标记请求。
 */
public class UpdateTodoImportantRequest {

    @NotNull(message = "important 不能为空")
    private Boolean important;

    public Boolean getImportant() {
        return important;
    }

    public void setImportant(Boolean important) {
        this.important = important;
    }
}
