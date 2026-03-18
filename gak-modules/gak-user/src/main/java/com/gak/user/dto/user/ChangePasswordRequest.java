package com.gak.user.dto.user;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改密码请求。
 */
public class ChangePasswordRequest {

    @NotBlank(message = "username 不能为空")
    private String username;

    @NotBlank(message = "oldEncryptedPassword 不能为空")
    private String oldEncryptedPassword;

    @NotBlank(message = "newEncryptedPassword 不能为空")
    private String newEncryptedPassword;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getOldEncryptedPassword() {
        return oldEncryptedPassword;
    }

    public void setOldEncryptedPassword(String oldEncryptedPassword) {
        this.oldEncryptedPassword = oldEncryptedPassword;
    }

    public String getNewEncryptedPassword() {
        return newEncryptedPassword;
    }

    public void setNewEncryptedPassword(String newEncryptedPassword) {
        this.newEncryptedPassword = newEncryptedPassword;
    }
}
