package com.gak.user.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新用户状态请求。
 */
public class UpdateUserStatusRequest {

    @NotBlank(message = "status 不能为空")
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
