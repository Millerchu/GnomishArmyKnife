package com.gak.permission.service;

import com.gak.framework.security.AppPermissionCheck;
import com.gak.permission.mapper.UserAppPermissionMapper;
import org.springframework.stereotype.Service;

/** 应用目录和业务入口使用相同的直接授权及管理员兜底语义。 */
@Service
public class AppPermissionCheckImpl implements AppPermissionCheck {
    private final UserAppPermissionMapper mapper;

    public AppPermissionCheckImpl(UserAppPermissionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean isAllowed(Long userId, String appCode) {
        return mapper.isAllowed(userId, appCode);
    }
}
