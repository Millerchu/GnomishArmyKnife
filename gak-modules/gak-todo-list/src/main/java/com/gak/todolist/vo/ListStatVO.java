package com.gak.todolist.vo;

/**
 * 清单统计 VO。
 */
public class ListStatVO {

    private String listCode;
    private long count;

    public String getListCode() {
        return listCode;
    }

    public void setListCode(String listCode) {
        this.listCode = listCode;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
