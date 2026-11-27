package com.gak.user.service.user;

import java.time.Instant;

/** 登录验证码与其失效时间，仅保存在服务器会话中。 */
public record LoginCaptcha(String answer, Instant expiresAt) implements java.io.Serializable {
}
