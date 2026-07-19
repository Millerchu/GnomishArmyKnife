package com.gak.passwordmemo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新备忘录密码请求。
 */
public class UpdateMemoPasswordRequest {

    @NotBlank(message = "newPassword 不能为空")
    @Size(max = 128, message = "newPassword 长度不能超过 128")
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
