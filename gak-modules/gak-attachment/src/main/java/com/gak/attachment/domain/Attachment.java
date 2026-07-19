package com.gak.attachment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 统一附件元数据实体，物理文件始终由存储适配器管理。
 */
@TableName("gak_attachment")
public class Attachment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long ownerUserId;
    private String businessType;
    private Long businessId;
    private String usageType;
    private String originalFileName;
    private String contentType;
    private String fileExtension;
    private Long fileSize;
    private String sha256;
    private String storageProvider;
    private String objectKey;
    private String thumbnailKey;
    private String status;
    private Integer sortNo;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime boundAt;
    private LocalDateTime deletedAt;
    private LocalDateTime purgedAt;
    private String legacySourceKey;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getUsageType() { return usageType; }
    public void setUsageType(String usageType) { this.usageType = usageType; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getFileExtension() { return fileExtension; }
    public void setFileExtension(String fileExtension) { this.fileExtension = fileExtension; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getStorageProvider() { return storageProvider; }
    public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getThumbnailKey() { return thumbnailKey; }
    public void setThumbnailKey(String thumbnailKey) { this.thumbnailKey = thumbnailKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getBoundAt() { return boundAt; }
    public void setBoundAt(LocalDateTime boundAt) { this.boundAt = boundAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getPurgedAt() { return purgedAt; }
    public void setPurgedAt(LocalDateTime purgedAt) { this.purgedAt = purgedAt; }
    public String getLegacySourceKey() { return legacySourceKey; }
    public void setLegacySourceKey(String legacySourceKey) { this.legacySourceKey = legacySourceKey; }
}
