package com.gak.datamigration.service;

import com.gak.user.constant.UserSecurityConstants;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 数据迁移管理员权限校验。
 */
@Service
public class DataMigrationAdminGuard {

    private final UserMapper userMapper;

    public DataMigrationAdminGuard(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User requireAdmin(Long currentUserId) {
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if (!UserSecurityConstants.ADMIN_ROLE_CODE.equalsIgnoreCase(resolveRoleCode(currentUser))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可执行数据迁移");
        }
        return currentUser;
    }

    public User requireExists(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private String resolveRoleCode(User user) {
        return StringUtils.hasText(user.getRoleCode()) ? user.getRoleCode() : UserSecurityConstants.DEFAULT_ROLE_CODE;
    }
}
