package com.gak.user.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 */
public class LoginRequest {

    @NotBlank(message = "username 不能为空")
    @Size(max = 64, message = "用户名过长")
    private String username;

    @NotBlank(message = "encryptedPassword 不能为空")
    private String encryptedPassword;

    @NotBlank(message = "captcha 不能为空")
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
