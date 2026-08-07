package com.gak.worklog.dto;

import java.time.LocalDate;

/**
 * 可在新增日志时复用的未完成工作内容。
 */
public class UnfinishedWorkItemResponse {

    private Long id;
    private Long workLogId;
    private LocalDate logDate;
    private String projectCode;
    private String workItem;
    private String status;
    private String zentaoNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkLogId() {
        return workLogId;
    }

    public void setWorkLogId(Long workLogId) {
        this.workLogId = workLogId;
    }

    public LocalDate getLogDate() {
        return logDate;
    }

    public void setLogDate(LocalDate logDate) {
        this.logDate = logDate;
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

    public String getZentaoNo() {
        return zentaoNo;
    }

    public void setZentaoNo(String zentaoNo) {
        this.zentaoNo = zentaoNo;
    }
}
