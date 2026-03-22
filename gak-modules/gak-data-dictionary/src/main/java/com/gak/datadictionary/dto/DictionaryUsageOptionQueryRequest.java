package com.gak.datadictionary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 按 usage 查询字典选项的请求参数。
 */
public class DictionaryUsageOptionQueryRequest {

    @NotBlank(message = "appCode 不能为空")
    @Size(max = 64, message = "appCode 长度不能超过 64")
    private String appCode;

    @NotBlank(message = "moduleCode 不能为空")
    @Size(max = 64, message = "moduleCode 长度不能超过 64")
    private String moduleCode;

    @NotBlank(message = "bizFieldCode 不能为空")
    @Size(max = 64, message = "bizFieldCode 长度不能超过 64")
    private String bizFieldCode;

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public String getBizFieldCode() {
        return bizFieldCode;
    }

    public void setBizFieldCode(String bizFieldCode) {
        this.bizFieldCode = bizFieldCode;
    }
}
