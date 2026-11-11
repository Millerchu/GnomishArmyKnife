package com.gak.passwordmemo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新备忘录密码请求。
 */
public class UpdateMemoPasswordRequest {

    @NotNull(message = "newPassword 字段不能缺失或为 null")
    @Size(max = 128, message = "newPassword 长度不能超过 128")
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
