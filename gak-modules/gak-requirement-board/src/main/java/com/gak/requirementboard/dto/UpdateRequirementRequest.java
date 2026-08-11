package com.gak.requirementboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 编辑需求内容请求。
 */
public class UpdateRequirementRequest {

    @NotBlank(message = "所属应用不能为空")
    @Size(max = 64, message = "所属应用编码长度不能超过 64")
    private String appCode;

    @NotBlank(message = "类型不能为空")
    @Size(max = 16, message = "类型长度不能超过 16")
    private String type;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过 100")
    private String title;

    @Size(max = 2000, message = "详细描述长度不能超过 2000")
    private String description;

    @NotBlank(message = "优先级不能为空")
    @Size(max = 16, message = "优先级长度不能超过 16")
    private String priority;

    @NotNull(message = "version 不能为空")
    @Positive(message = "version 必须大于 0")
    private Long version;

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
