package com.gak.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录请求体。
 */
public class LoginRequest {

    /**
     * 用户名。
     */
    @NotBlank
    private String username;

    /**
     * 前端加密后的密码（Base64 编码）。
     */
    @NotBlank
    private String encryptedPassword;

    /**
     * 登录验证码。
     */
    @NotBlank
    private String captcha;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public void setEncryptedPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    public String getCaptcha() {
        return captcha;
    }

    public void setCaptcha(String captcha) {
        this.captcha = captcha;
    }
}
