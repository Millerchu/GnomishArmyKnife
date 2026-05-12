package com.gak.healthrecord.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.util.StringUtils;

/**
 * 保存医院就诊请求。
 */
public class SaveHealthVisitRequest {

    @NotNull(message = "visitDate 不能为空")
    private LocalDate visitDate;

    @NotBlank(message = "hospitalName 不能为空")
    @Size(max = 64, message = "hospitalName 长度不能超过 64")
    private String hospitalName;

    @Size(max = 64, message = "departmentName 长度不能超过 64")
    private String departmentName;

    @Size(max = 64, message = "doctorName 长度不能超过 64")
    private String doctorName;

    @Size(max = 24, message = "visitType 长度不能超过 24")
    private String visitType;

    @Size(max = 240, message = "chiefComplaint 长度不能超过 240")
    private String chiefComplaint;

    @Size(max = 500, message = "diagnosisSummary 长度不能超过 500")
    private String diagnosisSummary;

    @Size(max = 500, message = "treatmentPlan 长度不能超过 500")
    private String treatmentPlan;

    @Size(max = 500, message = "doctorAdvice 长度不能超过 500")
    private String doctorAdvice;

    @Size(max = 255, message = "caseRecordFileName 长度不能超过 255")
    private String caseRecordFileName;

    @Size(max = 255, message = "caseRecordUrl 长度不能超过 255")
    private String caseRecordUrl;

    @Size(max = 255, message = "note 长度不能超过 255")
    private String note;

    @AssertTrue(message = "主诉、诊断、处置方案、医生建议至少填写一项")
    public boolean isVisitContentValid() {
        return StringUtils.hasText(chiefComplaint)
                || StringUtils.hasText(diagnosisSummary)
                || StringUtils.hasText(treatmentPlan)
                || StringUtils.hasText(doctorAdvice);
    }

    @AssertTrue(message = "caseRecordFileName 和 caseRecordUrl 必须同时为空或同时有值")
    public boolean isCaseRecordFilePairValid() {
        boolean hasFileName = StringUtils.hasText(caseRecordFileName);
        boolean hasUrl = StringUtils.hasText(caseRecordUrl);
        return hasFileName == hasUrl;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(LocalDate visitDate) {
        this.visitDate = visitDate;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public String getVisitType() {
        return visitType;
    }

    public void setVisitType(String visitType) {
        this.visitType = visitType;
    }

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public void setChiefComplaint(String chiefComplaint) {
        this.chiefComplaint = chiefComplaint;
    }

    public String getDiagnosisSummary() {
        return diagnosisSummary;
    }

    public void setDiagnosisSummary(String diagnosisSummary) {
        this.diagnosisSummary = diagnosisSummary;
    }

    public String getTreatmentPlan() {
        return treatmentPlan;
    }

    public void setTreatmentPlan(String treatmentPlan) {
        this.treatmentPlan = treatmentPlan;
    }

    public String getDoctorAdvice() {
        return doctorAdvice;
    }

    public void setDoctorAdvice(String doctorAdvice) {
        this.doctorAdvice = doctorAdvice;
    }

    public String getCaseRecordFileName() {
        return caseRecordFileName;
    }

    public void setCaseRecordFileName(String caseRecordFileName) {
        this.caseRecordFileName = caseRecordFileName;
    }

    public String getCaseRecordUrl() {
        return caseRecordUrl;
    }

    public void setCaseRecordUrl(String caseRecordUrl) {
        this.caseRecordUrl = caseRecordUrl;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
