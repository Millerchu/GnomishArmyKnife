package com.gak.attachment.vo;

/**
 * 前端可见的附件摘要，不暴露任何物理存储路径。
 */
public class AttachmentVO {

    private Long id;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String usageType;
    private Integer sortNo;
    private Boolean image;
    private Boolean thumbnailAvailable;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getUsageType() { return usageType; }
    public void setUsageType(String usageType) { this.usageType = usageType; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Boolean getImage() { return image; }
    public void setImage(Boolean image) { this.image = image; }
    public Boolean getThumbnailAvailable() { return thumbnailAvailable; }
    public void setThumbnailAvailable(Boolean thumbnailAvailable) { this.thumbnailAvailable = thumbnailAvailable; }
}
