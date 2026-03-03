package com.gak.user.dto;

/**
 * 登录验证码返回体。
 */
public class CaptchaResponse {

    /**
     * 4 位字母数字验证码。
     */
    private final String captcha;

    public CaptchaResponse(String captcha) {
        this.captcha = captcha;
    }

    public String getCaptcha() {
        return captcha;
    }
}
