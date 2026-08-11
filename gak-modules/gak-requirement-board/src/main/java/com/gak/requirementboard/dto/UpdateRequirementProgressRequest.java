package com.gak.requirementboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 更新需求进度请求。
 */
public class UpdateRequirementProgressRequest {

    @NotBlank(message = "反馈状态不能为空")
    @Size(max = 32, message = "反馈状态长度不能超过 32")
    private String status;

    @Size(max = 300, message = "处理说明长度不能超过 300")
    private String remark;

    @NotNull(message = "version 不能为空")
    @Positive(message = "version 必须大于 0")
    private Long version;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
