package com.gak.healthrecord.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 保存健康报告请求。
 */
public class SaveHealthReportRequest {

    private Long visitId;

    @NotNull(message = "examDate 不能为空")
    private LocalDate examDate;

    @Size(max = 64, message = "hospitalName 长度不能超过 64")
    private String hospitalName;

    @NotBlank(message = "reportTitle 不能为空")
    @Size(max = 64, message = "reportTitle 长度不能超过 64")
    private String reportTitle;

    @Size(max = 240, message = "summary 长度不能超过 240")
    private String summary;

    @Size(max = 240, message = "doctorAdvice 长度不能超过 240")
    private String doctorAdvice;

    @Size(max = 255, message = "reportFileName 长度不能超过 255")
    private String reportFileName;

    @Size(max = 255, message = "reportUrl 长度不能超过 255")
    private String reportUrl;

    private List<Long> attachmentIds;

    @AssertTrue(message = "reportFileName 和 reportUrl 必须同时为空或同时有值")
    public boolean isFilePairValid() {
        boolean hasFileName = reportFileName != null && !reportFileName.trim().isEmpty();
        boolean hasUrl = reportUrl != null && !reportUrl.trim().isEmpty();
        return hasFileName == hasUrl;
    }

    public Long getVisitId() {
        return visitId;
    }

    public void setVisitId(Long visitId) {
        this.visitId = visitId;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public String getReportTitle() {
        return reportTitle;
    }

    public void setReportTitle(String reportTitle) {
        this.reportTitle = reportTitle;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDoctorAdvice() {
        return doctorAdvice;
    }

    public void setDoctorAdvice(String doctorAdvice) {
        this.doctorAdvice = doctorAdvice;
    }

    public String getReportFileName() {
        return reportFileName;
    }

    public void setReportFileName(String reportFileName) {
        this.reportFileName = reportFileName;
    }

    public String getReportUrl() {
        return reportUrl;
    }

    public void setReportUrl(String reportUrl) {
        this.reportUrl = reportUrl;
    }

    public List<Long> getAttachmentIds() { return attachmentIds; }

    public void setAttachmentIds(List<Long> attachmentIds) { this.attachmentIds = attachmentIds; }
}
