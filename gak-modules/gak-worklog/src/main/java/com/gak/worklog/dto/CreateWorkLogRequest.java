package com.gak.worklog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 新增工作日志请求。
 */
public class CreateWorkLogRequest {

    /**
     * 兼容旧前端提交字段，实际归属以后端登录态为准。
     */
    private Long userId;

    @NotNull
    private LocalDate logDate;

    @NotEmpty
    private List<@Size(max = 32) String> typeCodes;

    @Size(max = 64)
    private String location;

    @NotBlank
    @Size(max = 128)
    private String projectCode;

    /**
     * 兼容旧客户端的换行文本字段。
     */
    @Size(max = 4000)
    private String workItem;

    /**
     * 兼容旧客户端的日志级汇总状态。
     */
    @Size(max = 16)
    private String status;

    /**
     * 新客户端按工作内容条目提交独立状态。
     */
    @Size(max = 100)
    private List<@Valid WorkLogItemRequest> workItems;

    @Size(max = 255)
    private String zentaoNo;

    @NotNull
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    @Digits(integer = 1, fraction = 1)
    private BigDecimal personDay;

    @DecimalMin(value = "0.0")
    private BigDecimal overtimeHours;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime offWorkTime;

    @Size(max = 32)
    private String businessTripAllowanceScene;

    private Boolean businessTripReimbursed;

    @Size(max = 500)
    private String remark;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getWorkItem() {
        return workItem;
    }

    public void setWorkItem(String workItem) {
        this.workItem = workItem;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<WorkLogItemRequest> getWorkItems() {
        return workItems;
    }

    public void setWorkItems(List<WorkLogItemRequest> workItems) {
        this.workItems = workItems;
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
