package com.gak.datamigration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * 创建导出任务请求。
 */
public class CreateDataMigrationExportRequest {

    @NotBlank(message = "scopeMode 不能为空")
    @Size(max = 20, message = "scopeMode 长度不能超过 20")
    private String scopeMode;

    @NotBlank(message = "packageName 不能为空")
    @Size(max = 128, message = "packageName 长度不能超过 128")
    private String packageName;

    private Boolean includeAttachments;

    private List<String> systemResourceCodes = new ArrayList<>();

    private List<String> businessAppCodes = new ArrayList<>();

    @Size(max = 255, message = "remark 长度不能超过 255")
    private String remark;

    public String getScopeMode() {
        return scopeMode;
    }

    public void setScopeMode(String scopeMode) {
        this.scopeMode = scopeMode;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public Boolean getIncludeAttachments() {
        return includeAttachments;
    }

    public void setIncludeAttachments(Boolean includeAttachments) {
        this.includeAttachments = includeAttachments;
    }

    public List<String> getSystemResourceCodes() {
        return systemResourceCodes;
    }

    public void setSystemResourceCodes(List<String> systemResourceCodes) {
        this.systemResourceCodes = systemResourceCodes;
    }

    public List<String> getBusinessAppCodes() {
        return businessAppCodes;
    }

    public void setBusinessAppCodes(List<String> businessAppCodes) {
        this.businessAppCodes = businessAppCodes;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
