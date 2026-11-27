package com.gak.healthrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gak.healthrecord.persistence.HealthReportDetailItemsTypeHandler;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 体检报告明细集合实体。
 */
@TableName(value = "gak_health_report_detail", autoResultMap = true)
public class HealthReportDetail {

    @TableId(value = "report_id", type = IdType.INPUT)
    private Long reportId;
    private Long recordId;
    private Integer revision;

    @TableField(typeHandler = HealthReportDetailItemsTypeHandler.class)
    private List<HealthReportDetailItem> items;

    private LocalDateTime updatedAt;

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public List<HealthReportDetailItem> getItems() {
        return items;
    }

    public void setItems(List<HealthReportDetailItem> items) {
        this.items = items;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
