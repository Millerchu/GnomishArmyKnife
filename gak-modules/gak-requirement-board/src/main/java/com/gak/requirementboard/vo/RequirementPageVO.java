package com.gak.requirementboard.vo;

import java.util.List;

/**
 * 需求看板分页结果。
 */
public class RequirementPageVO {

    private List<RequirementListVO> list;
    private long total;
    private List<RequirementStatusCountVO> statusCounts;

    public List<RequirementListVO> getList() {
        return list;
    }

    public void setList(List<RequirementListVO> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<RequirementStatusCountVO> getStatusCounts() {
        return statusCounts;
    }

    public void setStatusCounts(List<RequirementStatusCountVO> statusCounts) {
        this.statusCounts = statusCounts;
    }
}
