package com.gak.requirementboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建共享需求请求。
 */
public class CreateRequirementRequest {

    @NotBlank(message = "所属应用不能为空")
    @Size(max = 64, message = "所属应用编码长度不能超过 64")
    private String appCode;

    @NotBlank(message = "需求标题不能为空")
    @Size(max = 100, message = "需求标题长度不能超过 100")
    private String title;

    @Size(max = 2000, message = "需求描述长度不能超过 2000")
    private String description;

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
