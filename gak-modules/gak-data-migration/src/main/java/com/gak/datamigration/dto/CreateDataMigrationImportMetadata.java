package com.gak.datamigration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 导入任务元数据。
 */
public class CreateDataMigrationImportMetadata {

    @NotBlank(message = "importMode 不能为空")
    @Size(max = 20, message = "importMode 长度不能超过 20")
    private String importMode;

    private Boolean includeAttachments;

    private Boolean continueOnError;

    @Size(max = 255, message = "remark 长度不能超过 255")
    private String remark;

    public String getImportMode() {
        return importMode;
    }

    public void setImportMode(String importMode) {
        this.importMode = importMode;
    }

    public Boolean getIncludeAttachments() {
        return includeAttachments;
    }

    public void setIncludeAttachments(Boolean includeAttachments) {
        this.includeAttachments = includeAttachments;
    }

    public Boolean getContinueOnError() {
        return continueOnError;
    }

    public void setContinueOnError(Boolean continueOnError) {
        this.continueOnError = continueOnError;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
