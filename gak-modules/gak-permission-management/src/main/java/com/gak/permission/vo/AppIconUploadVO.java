package com.gak.permission.vo;

/**
 * 应用图标上传结果。
 */
public class AppIconUploadVO {

    private String iconUrl;
    private String iconStorageType;
    private String iconFileName;

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
}
