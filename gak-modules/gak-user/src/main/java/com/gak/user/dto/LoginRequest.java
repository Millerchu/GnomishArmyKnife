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
     * 明文密码。
     */
    @NotBlank
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}