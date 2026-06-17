package com.gak.worklog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 最近一周日志简述响应。
 */
public class WeeklyWorkLogBriefResponse {

    private Long id;
    private LocalDate logDate;
    private List<String> typeCodes;
    private String location;
    private String projectCode;
    private String brief;
    private BigDecimal personDay;
    private BigDecimal overtimeHours;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime offWorkTime;
    private String businessTripAllowanceScene;
    private BigDecimal businessTripAllowanceAmount;
    private Boolean businessTripReimbursed;
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getLogDate() {
        return logDate;
    }

    public void setLogDate(LocalDate logDate) {
        this.logDate = logDate;
    }

    public List<String> getTypeCodes() {
        return typeCodes;
    }

    public void setTypeCodes(List<String> typeCodes) {
        this.typeCodes = typeCodes;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getBrief() {
        return brief;
    }

    public void setBrief(String brief) {
        this.brief = brief;
    }

    public BigDecimal getPersonDay() {
        return personDay;
    }

    public void setPersonDay(BigDecimal personDay) {
        this.personDay = personDay;
    }

    public BigDecimal getOvertimeHours() {
        return overtimeHours;
    }

    public void setOvertimeHours(BigDecimal overtimeHours) {
        this.overtimeHours = overtimeHours;
    }

    public LocalTime getOffWorkTime() {
        return offWorkTime;
    }

    public void setOffWorkTime(LocalTime offWorkTime) {
        this.offWorkTime = offWorkTime;
    }

    public String getBusinessTripAllowanceScene() {
        return businessTripAllowanceScene;
    }

    public void setBusinessTripAllowanceScene(String businessTripAllowanceScene) {
        this.businessTripAllowanceScene = businessTripAllowanceScene;
    }

    public BigDecimal getBusinessTripAllowanceAmount() {
        return businessTripAllowanceAmount;
    }

    public void setBusinessTripAllowanceAmount(BigDecimal businessTripAllowanceAmount) {
        this.businessTripAllowanceAmount = businessTripAllowanceAmount;
    }

    public Boolean getBusinessTripReimbursed() {
        return businessTripReimbursed;
    }

    public void setBusinessTripReimbursed(Boolean businessTripReimbursed) {
        this.businessTripReimbursed = businessTripReimbursed;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
