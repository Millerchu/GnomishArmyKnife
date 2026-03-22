package com.gak.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 应用新增/编辑请求。
 */
public class SaveSystemAppRequest {

    @NotBlank(message = "appCode 不能为空")
    @Size(max = 64, message = "appCode 长度不能超过 64")
    private String appCode;

    @Size(max = 64, message = "featureCode 长度不能超过 64")
    private String featureCode;

    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 长度不能超过 64")
    private String name;

    @Size(max = 128, message = "route 长度不能超过 128")
    private String route;

    @Size(max = 64, message = "category 长度不能超过 64")
    private String category;

    @NotBlank(message = "dataSourceMode 不能为空")
    @Size(max = 16, message = "dataSourceMode 长度不能超过 16")
    private String dataSourceMode;

    @NotBlank(message = "securityLevel 不能为空")
    @Size(max = 20, message = "securityLevel 长度不能超过 20")
    private String securityLevel;

    @NotBlank(message = "encryptionMode 不能为空")
    @Size(max = 20, message = "encryptionMode 长度不能超过 20")
    private String encryptionMode;

    @NotBlank(message = "iconType 不能为空")
    @Size(max = 16, message = "iconType 长度不能超过 16")
    private String iconType;

    @Size(max = 64, message = "iconPreset 长度不能超过 64")
    private String iconPreset;

    @Size(max = 32, message = "iconText 长度不能超过 32")
    private String iconText;

    @Size(max = 255, message = "iconUrl 长度不能超过 255")
    private String iconUrl;

    @Size(max = 32, message = "iconStorageType 长度不能超过 32")
    private String iconStorageType;

    @Size(max = 255, message = "iconFileName 长度不能超过 255")
    private String iconFileName;

    private Boolean enabled;

    private Integer sortNo;

    @Size(max = 255, message = "description 长度不能超过 255")
    private String description;

    @Size(max = 255, message = "remark 长度不能超过 255")
    private String remark;

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featureCode) {
        this.featureCode = featureCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDataSourceMode() {
        return dataSourceMode;
    }

    public void setDataSourceMode(String dataSourceMode) {
        this.dataSourceMode = dataSourceMode;
    }

    public String getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(String securityLevel) {
        this.securityLevel = securityLevel;
    }

    public String getEncryptionMode() {
        return encryptionMode;
    }

    public void setEncryptionMode(String encryptionMode) {
        this.encryptionMode = encryptionMode;
    }

    public String getIconType() {
        return iconType;
    }

    public void setIconType(String iconType) {
        this.iconType = iconType;
    }

    public String getIconText() {
        return iconText;
    }

    public void setIconText(String iconText) {
        this.iconText = iconText;
    }

    public String getIconPreset() {
        return iconPreset;
    }

    public void setIconPreset(String iconPreset) {
        this.iconPreset = iconPreset;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getIconStorageType() {
        return iconStorageType;
    }

    public void setIconStorageType(String iconStorageType) {
        this.iconStorageType = iconStorageType;
    }

    public String getIconFileName() {
        return iconFileName;
    }

    public void setIconFileName(String iconFileName) {
        this.iconFileName = iconFileName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
