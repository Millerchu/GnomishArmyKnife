package com.gak.todolist.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 新增/编辑待办请求。
 */
public class SaveTodoItemRequest {

    @NotBlank(message = "title 不能为空")
    @Size(max = 100, message = "title 长度不能超过 100")
    private String title;

    @NotBlank(message = "listCode 不能为空")
    @Size(max = 20, message = "listCode 长度不能超过 20")
    private String listCode;

    @NotBlank(message = "importance 不能为空")
    @Size(max = 20, message = "importance 长度不能超过 20")
    private String importance;

    @NotBlank(message = "status 不能为空")
    @Size(max = 20, message = "status 长度不能超过 20")
    private String status;

    @NotNull(message = "important 不能为空")
    private Boolean important;

    private LocalDate dueDate;

    private LocalDateTime reminderAt;

    @Size(max = 2000, message = "note 长度不能超过 2000")
    private String note;

    @Valid
    private List<TodoItemStepRequest> steps;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getListCode() {
        return listCode;
    }

    public void setListCode(String listCode) {
        this.listCode = listCode;
    }

    public String getImportance() {
        return importance;
    }

    public void setImportance(String importance) {
        this.importance = importance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getImportant() {
        return important;
    }

    public void setImportant(Boolean important) {
        this.important = important;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDateTime getReminderAt() {
        return reminderAt;
    }

    public void setReminderAt(LocalDateTime reminderAt) {
        this.reminderAt = reminderAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<TodoItemStepRequest> getSteps() {
        return steps;
    }

    public void setSteps(List<TodoItemStepRequest> steps) {
        this.steps = steps;
    }
}
