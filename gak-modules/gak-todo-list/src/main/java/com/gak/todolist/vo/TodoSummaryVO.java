package com.gak.todolist.vo;

/**
 * 概要统计 VO。
 */
public class TodoSummaryVO {

    private long total;
    private long today;
    private long important;
    private long completed;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getToday() {
        return today;
    }

    public void setToday(long today) {
        this.today = today;
    }

    public long getImportant() {
        return important;
    }

    public void setImportant(long important) {
        this.important = important;
    }

    public long getCompleted() {
        return completed;
    }

    public void setCompleted(long completed) {
        this.completed = completed;
    }
}
