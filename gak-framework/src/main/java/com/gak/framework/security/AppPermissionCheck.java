package com.gak.framework.security;

/** 查询当前账号是否可以访问启用的应用。 */
public interface AppPermissionCheck {
    boolean isAllowed(Long userId, String appCode);
}
