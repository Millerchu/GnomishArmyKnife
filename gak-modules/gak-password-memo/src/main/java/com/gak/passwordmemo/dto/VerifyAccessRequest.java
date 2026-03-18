package com.gak.passwordmemo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 查看明文密码校验请求。
 */
public class VerifyAccessRequest {

    @NotBlank(message = "loginPassword 不能为空")
    @Size(max = 100, message = "loginPassword 长度不能超过 100")
    private String loginPassword;

    public String getLoginPassword() {
        return loginPassword;
    }

    public void setLoginPassword(String loginPassword) {
        this.loginPassword = loginPassword;
    }
}
