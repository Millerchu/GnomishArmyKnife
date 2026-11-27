package com.gak.datamigration.dto;

import com.gak.datamigration.service.AttachmentMigrationSupport.TransferItem;
import com.gak.healthrecord.domain.HealthRecord;
import com.gak.healthrecord.domain.HealthReport;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.personalbills.domain.PersonalBill;
import java.util.List;

/**
 * 健康记录迁移载荷，保持旧版 JSON 字段名并扩展可选 reportDetails。
 */
public class HealthRecordMigrationPayload {

    private List<HealthRecord> records;
    private List<HealthVisit> visits;
    private List<PersonalBill> bills;
    private List<HealthReport> reports;
    private List<TransferItem> visitAttachments;
    private List<TransferItem> reportAttachments;
    private List<HealthReportDetailMigrationItem> reportDetails;

    public HealthRecordMigrationPayload() {
    }

    public HealthRecordMigrationPayload(List<HealthRecord> records,
                                        List<HealthVisit> visits,
                                        List<HealthReport> reports,
                                        List<TransferItem> visitAttachments,
                                        List<TransferItem> reportAttachments,
                                        List<PersonalBill> bills,
                                        List<HealthReportDetailMigrationItem> reportDetails) {
        this.records = records;
        this.visits = visits;
        this.reports = reports;
        this.visitAttachments = visitAttachments;
        this.reportAttachments = reportAttachments;
        this.bills = bills;
        this.reportDetails = reportDetails;
    }

    public List<HealthRecord> getRecords() {
        return records;
    }

    public void setRecords(List<HealthRecord> records) {
        this.records = records;
    }

    public List<HealthVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<HealthVisit> visits) {
        this.visits = visits;
    }

    public List<PersonalBill> getBills() {
        return bills;
    }

    public void setBills(List<PersonalBill> bills) {
        this.bills = bills;
    }

    public List<HealthReport> getReports() {
        return reports;
    }

    public void setReports(List<HealthReport> reports) {
        this.reports = reports;
    }

    public List<TransferItem> getVisitAttachments() {
        return visitAttachments;
    }

    public void setVisitAttachments(List<TransferItem> visitAttachments) {
        this.visitAttachments = visitAttachments;
    }

    public List<TransferItem> getReportAttachments() {
        return reportAttachments;
    }

    public void setReportAttachments(List<TransferItem> reportAttachments) {
        this.reportAttachments = reportAttachments;
    }

    public List<HealthReportDetailMigrationItem> getReportDetails() {
        return reportDetails;
    }

    public void setReportDetails(List<HealthReportDetailMigrationItem> reportDetails) {
        this.reportDetails = reportDetails;
    }
}
