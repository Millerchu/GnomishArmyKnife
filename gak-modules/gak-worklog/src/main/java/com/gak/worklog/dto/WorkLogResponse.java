package com.gak.worklog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 工作日志详情响应。
 */
public class WorkLogResponse {

    private Long id;
    private Long userId;
    private LocalDate logDate;
    private List<String> typeCodes;
    private String location;
    private String projectCode;
    private String workItem;
    /**
     * 兼容旧客户端的汇总状态，任一条目未完成时为未完成。
     */
    private String status;
    private List<WorkLogItemResponse> workItems;
    private String zentaoNo;
    private BigDecimal personDay;
    private BigDecimal overtimeHours;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime offWorkTime;
    private String businessTripAllowanceScene;
    private BigDecimal businessTripAllowanceAmount;
    private Boolean businessTripReimbursed;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public List<WorkLogItemResponse> getWorkItems() {
        return workItems;
    }

    public void setWorkItems(List<WorkLogItemResponse> workItems) {
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
