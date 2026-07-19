package com.gak.permission.service;

import com.gak.attachment.constant.AttachmentConstants;
import com.gak.attachment.policy.AttachmentBusinessAccessPolicy;
import com.gak.permission.mapper.SystemAppMapper;
import com.gak.user.constant.UserSecurityConstants;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import org.springframework.stereotype.Component;

/**
 * 应用图标对登录用户可见，只有管理员可以管理。
 */
@Component
public class SystemAppAttachmentAccessPolicy implements AttachmentBusinessAccessPolicy {

    private final SystemAppMapper systemAppMapper;
    private final UserMapper userMapper;

    public SystemAppAttachmentAccessPolicy(SystemAppMapper systemAppMapper, UserMapper userMapper) {
        this.systemAppMapper = systemAppMapper;
        this.userMapper = userMapper;
    }

    @Override
    public String businessType() { return AttachmentConstants.BUSINESS_SYSTEM_APP; }

    @Override
    public boolean canView(Long currentUserId, Long businessId) {
        return userMapper.selectById(currentUserId) != null && systemAppMapper.selectById(businessId) != null;
    }

    @Override
    public boolean canManage(Long currentUserId, Long businessId) {
        User user = userMapper.selectById(currentUserId);
        return user != null && systemAppMapper.selectById(businessId) != null
                && UserSecurityConstants.ADMIN_ROLE_CODE.equalsIgnoreCase(user.getRoleCode());
    }
}
