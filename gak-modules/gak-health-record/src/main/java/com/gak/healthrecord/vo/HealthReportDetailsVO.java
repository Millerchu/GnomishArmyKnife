package com.gak.healthrecord.vo;

import java.util.List;

/**
 * 体检报告明细视图。
 */
public class HealthReportDetailsVO {

    private HealthReportVO report;
    private Integer revision;
    private Long linkedRecordId;
    private List<HealthReportDetailItemVO> items;

    public HealthReportVO getReport() {
        return report;
    }

    public void setReport(HealthReportVO report) {
        this.report = report;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public Long getLinkedRecordId() {
        return linkedRecordId;
    }

    public void setLinkedRecordId(Long linkedRecordId) {
        this.linkedRecordId = linkedRecordId;
    }

    public List<HealthReportDetailItemVO> getItems() {
        return items;
    }

    public void setItems(List<HealthReportDetailItemVO> items) {
        this.items = items;
    }
}
