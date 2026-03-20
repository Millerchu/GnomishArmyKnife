package com.gak.todolist.vo;

import java.util.List;

/**
 * 待办列表页数据。
 */
public class TodoItemPageVO {

    private List<TodoItemListVO> list;
    private long total;
    private TodoSummaryVO summary;
    private List<TodoItemSimpleVO> upcoming;
    private List<ListStatVO> listStats;

    public List<TodoItemListVO> getList() {
        return list;
    }

    public void setList(List<TodoItemListVO> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public TodoSummaryVO getSummary() {
        return summary;
    }

    public void setSummary(TodoSummaryVO summary) {
        this.summary = summary;
    }

    public List<TodoItemSimpleVO> getUpcoming() {
        return upcoming;
    }

    public void setUpcoming(List<TodoItemSimpleVO> upcoming) {
        this.upcoming = upcoming;
    }

    public List<ListStatVO> getListStats() {
        return listStats;
    }

    public void setListStats(List<ListStatVO> listStats) {
        this.listStats = listStats;
    }
}
