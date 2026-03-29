package com.gak.datamigration.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 迁移任务视图。
 */
public class DataMigrationTaskVO {

    private Long id;
    private String taskNo;
    private String taskType;
    private String status;
    private String packageName;
    private String fileName;
    private Long fileSize;
    private Integer systemResourceCount;
    private Integer businessAppCount;
    private Long recordCount;
    private Long attachmentCount;
    private String downloadUrl;
    private Boolean canDownload;
    private Boolean canDelete;
    private String message;
    private String remark;
    private String createdAt;
    private String finishedAt;
    private List<ItemVO> items = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
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

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Integer getSystemResourceCount() {
        return systemResourceCount;
    }

    public void setSystemResourceCount(Integer systemResourceCount) {
        this.systemResourceCount = systemResourceCount;
    }

    public Integer getBusinessAppCount() {
        return businessAppCount;
    }

    public void setBusinessAppCount(Integer businessAppCount) {
        this.businessAppCount = businessAppCount;
    }

    public Long getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Long recordCount) {
        this.recordCount = recordCount;
    }

    public Long getAttachmentCount() {
        return attachmentCount;
    }

    public void setAttachmentCount(Long attachmentCount) {
        this.attachmentCount = attachmentCount;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public Boolean getCanDownload() {
        return canDownload;
    }

    public void setCanDownload(Boolean canDownload) {
        this.canDownload = canDownload;
    }

    public Boolean getCanDelete() {
        return canDelete;
    }

    public void setCanDelete(Boolean canDelete) {
        this.canDelete = canDelete;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
    }

    public List<ItemVO> getItems() {
        return items;
    }

    public void setItems(List<ItemVO> items) {
        this.items = items;
    }

    /**
     * 任务明细视图。
     */
    public static class ItemVO {

        private String resourceCode;
        private String resourceName;
        private String resourceType;
        private String status;
        private Long recordCount;
        private Long attachmentCount;
        private String message;
        private String createdAt;
        private String finishedAt;

        public String getResourceCode() {
            return resourceCode;
        }

        public void setResourceCode(String resourceCode) {
            this.resourceCode = resourceCode;
        }

        public String getResourceName() {
            return resourceName;
        }

        public void setResourceName(String resourceName) {
            this.resourceName = resourceName;
        }

        public String getResourceType() {
            return resourceType;
        }

        public void setResourceType(String resourceType) {
            this.resourceType = resourceType;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getRecordCount() {
            return recordCount;
        }

        public void setRecordCount(Long recordCount) {
            this.recordCount = recordCount;
        }

        public Long getAttachmentCount() {
            return attachmentCount;
        }

        public void setAttachmentCount(Long attachmentCount) {
            this.attachmentCount = attachmentCount;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getFinishedAt() {
            return finishedAt;
        }

        public void setFinishedAt(String finishedAt) {
            this.finishedAt = finishedAt;
        }
    }
}
