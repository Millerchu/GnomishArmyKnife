package com.gak.requirementboard.vo;

/**
 * 看板状态计数。
 */
public class RequirementStatusCountVO {

    private String status;
    private long count;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
