package com.gak.user.service.user;

/**
 * 经过 NAS 接口验证的用户身份。
 *
 * @param userId NAS 用户 ID
 * @param username NAS 用户名
 * @param userType NAS 用户类型，仅用于审计，不参与 GAK 角色判定
 */
public record NasIdentity(String userId, String username, String userType) {
}
