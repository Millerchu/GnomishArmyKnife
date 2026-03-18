package com.gak.user.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重置密码请求。
 */
public class ResetPasswordRequest {

    @NotBlank(message = "newPassword 不能为空")
    @Size(min = 6, max = 100, message = "newPassword 长度必须在 6 到 100 之间")
    private String newPassword;

    private Boolean forceChange;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public Boolean getForceChange() {
        return forceChange;
    }

    public void setForceChange(Boolean forceChange) {
        this.forceChange = forceChange;
    }
}
