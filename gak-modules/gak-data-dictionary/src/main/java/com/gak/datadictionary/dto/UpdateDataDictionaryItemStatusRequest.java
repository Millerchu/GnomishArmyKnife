package com.gak.datadictionary.dto;

import jakarta.validation.constraints.Size;

/**
 * 数据字典项状态更新请求。
 */
public class UpdateDataDictionaryItemStatusRequest {

    @Size(max = 20, message = "status 长度不能超过 20")
    private String status;

    private Boolean enabled;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
