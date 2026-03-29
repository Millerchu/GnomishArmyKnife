package com.gak.datamigration.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 迁移任务分页查询参数。
 */
public class DataMigrationTaskQueryRequest {

    @Min(value = 1, message = "pageNo 必须大于等于 1")
    private long pageNo = 1L;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    @Max(value = 100, message = "pageSize 不能大于 100")
    private long pageSize = 12L;

    @Size(max = 20, message = "taskType 长度不能超过 20")
    private String taskType;

    @Size(max = 20, message = "status 长度不能超过 20")
    private String status;

    public long getPageNo() {
        return pageNo;
    }

    public void setPageNo(long pageNo) {
        this.pageNo = pageNo;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
