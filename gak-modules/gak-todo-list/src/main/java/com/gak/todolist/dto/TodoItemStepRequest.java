package com.gak.todolist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 待办子任务请求。
 */
public class TodoItemStepRequest {

    @NotBlank(message = "step.title 不能为空")
    @Size(max = 80, message = "step.title 长度不能超过 80")
    private String title;

    private Boolean done;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getDone() {
        return done;
    }

    public void setDone(Boolean done) {
        this.done = done;
    }
}
