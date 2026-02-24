package com.gak.worklog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 更新工作日志请求。
 */
public class UpdateWorkLogRequest {

    @NotNull
    private LocalDate logDate;

    @NotEmpty
    private List<@Size(max = 32) String> typeCodes;

    @Size(max = 64)
    private String location;

    @Size(max = 128)
    private String projectCode;

    @NotNull
    @Size(max = 4000)
    private String workItem;

    @Size(max = 255)
    private String zentaoNo;

    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal personDay;

    @DecimalMin(value = "0.0")
    private BigDecimal overtimeHours;

    @Size(max = 500)
    private String remark;

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

    public String getWorkItem() {
        return workItem;
    }

    public void setWorkItem(String workItem) {
        this.workItem = workItem;
    }

    public String getZentaoNo() {
        return zentaoNo;
    }

    public void setZentaoNo(String zentaoNo) {
        this.zentaoNo = zentaoNo;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
