package com.gak.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改密码请求体。
 */
public class ChangePasswordRequest {

    /**
     * 用户名。
     */
    @NotBlank
    private String username;

    /**
     * 前端加密后的原密码（Base64 编码）。
     */
    @NotBlank
    private String oldEncryptedPassword;

    /**
     * 前端加密后的新密码（Base64 编码）。
     */
    @NotBlank
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
