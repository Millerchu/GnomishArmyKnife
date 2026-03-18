package com.gak.user.vo.user;

/**
 * 登录结果。
 */
public record UserLoginVO(String token, UserProfileVO user) {
}
