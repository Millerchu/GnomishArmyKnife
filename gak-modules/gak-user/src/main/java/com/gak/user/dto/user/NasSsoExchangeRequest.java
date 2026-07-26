package com.gak.user.dto.user;

import jakarta.validation.constraints.NotBlank;

/**
 * NAS 单点登录交换请求。
 */
public class NasSsoExchangeRequest {

    @NotBlank(message = "code 不能为空")
    private String code;

    @NotBlank(message = "state 不能为空")
    private String state;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
