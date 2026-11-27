package com.gak.datamigration.dto;

import com.gak.healthrecord.domain.HealthReportDetailItem;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 体检报告明细迁移条目载荷。
 */
public class HealthReportDetailMigrationItem {

    private Long reportId;
    private Long recordId;
    private List<HealthReportDetailItem> items;
    private LocalDateTime updatedAt;

    public HealthReportDetailMigrationItem() {
    }

    public HealthReportDetailMigrationItem(Long reportId,
                                           Long recordId,
                                           List<HealthReportDetailItem> items,
                                           LocalDateTime updatedAt) {
        this.reportId = reportId;
        this.recordId = recordId;
        this.items = items;
        this.updatedAt = updatedAt;
    }

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
