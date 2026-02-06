package com.gak.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户注册请求体。
 */
public class RegisterRequest {

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

    /**
     * 展示名称。
     */
    @NotBlank
    private String displayName;

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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}