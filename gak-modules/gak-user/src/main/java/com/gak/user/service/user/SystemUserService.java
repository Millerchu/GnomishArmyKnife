package com.gak.user.service.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.user.constant.UserSecurityConstants;
import com.gak.user.domain.user.User;
import com.gak.user.dto.user.CreateUserRequest;
import com.gak.user.dto.user.ResetPasswordRequest;
import com.gak.user.dto.user.UpdateUserRequest;
import com.gak.user.dto.user.UpdateUserStatusRequest;
import com.gak.user.dto.user.UserQueryRequest;
import com.gak.user.mapper.user.UserMapper;
import com.gak.user.service.user.UserValidationSupport.StatusEnabledPair;
import com.gak.user.vo.user.UserListItemVO;
import com.gak.user.vo.user.UserProfileVO;
import java.util.Collections;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 后台用户管理服务。
 */
@Service
public class SystemUserService {

    private static final String APP_CODE = "APP_USER_MANAGEMENT";
    private static final String MODULE_CODE = "SYSTEM_USER";
    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final DataDictionaryUsageSupport dataDictionaryUsageSupport;

    public SystemUserService(UserMapper userMapper,
                             PasswordEncoder passwordEncoder,
                             DataDictionaryUsageSupport dataDictionaryUsageSupport) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.dataDictionaryUsageSupport = dataDictionaryUsageSupport;
    }

    public PagedResult<UserListItemVO> page(UserQueryRequest request) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        String keyword = UserValidationSupport.trimToNull(request.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like("username", keyword)
                    .or()
                    .like("display_name", keyword)
                    .or()
                    .like("phone", keyword)
                    .or()
                    .like("email", keyword));
        }
        if (StringUtils.hasText(request.getStatus())) {
            String status = normalizeStatusValue(request.getStatus(), true, UserSecurityConstants.ENABLED_STATUS);
            wrapper.eq("status", status);
        }
        if (StringUtils.hasText(request.getRoleCode())) {
            wrapper.eq("role_code", normalizeRoleCode(request.getRoleCode(), false));
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");

        List<User> users = userMapper.selectList(wrapper);
        List<UserListItemVO> list = new ArrayList<>();
        long total = users.size();
        long fromIndex = Math.max((request.getPageNo() - 1) * request.getPageSize(), 0L);
        long toIndex = Math.min(fromIndex + request.getPageSize(), total);
        if (fromIndex >= total) {
            return new PagedResult<>(Collections.emptyList(), total);
        }
        for (User user : users.subList((int) fromIndex, (int) toIndex)) {
            list.add(toUserListItem(user));
        }
        return new PagedResult<>(list, total);
    }

    @Transactional
    public UserProfileVO create(CreateUserRequest request) {
        String username = normalizeUsername(request.getUsername());
        ensureUsernameUnique(username, null);
        String roleCode = normalizeRoleCode(request.getRoleCode(), true);
        String status = normalizeStatusValue(request.getStatus(), true, UserSecurityConstants.ENABLED_STATUS);
        StatusEnabledPair statusEnabledPair = UserValidationSupport.normalizeStatusEnabled(
                status,
                request.getEnabled(),
                isEnabledStatus(status)
        );
        String password = request.getInitialPassword().trim();
        UserValidationSupport.validatePasswordLength(password);

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(resolveDisplayName(request.getDisplayName(), username));
        user.setPhone(UserValidationSupport.trimToNull(request.getPhone()));
        user.setEmail(UserValidationSupport.trimToNull(request.getEmail()));
        user.setRoleCode(roleCode);
        user.setStatus(statusEnabledPair.status());
        user.setEnabled(statusEnabledPair.enabled());
        user.setForceChangePassword(false);
        user.setRemark(UserValidationSupport.trimToNull(request.getRemark()));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return toUserProfile(user);
    }

    @Transactional
    public UserProfileVO update(Long id, UpdateUserRequest request) {
        User current = getByIdOrThrow(id);
        String username = normalizeUsername(request.getUsername());
        ensureUsernameUnique(username, id);
        String roleCode = normalizeRoleCode(request.getRoleCode(), true, resolveCurrentRoleCode(current));
        String status = normalizeStatusValue(request.getStatus(), true, resolveCurrentStatus(current));
        StatusEnabledPair statusEnabledPair = UserValidationSupport.normalizeStatusEnabled(
                status,
                request.getEnabled(),
                isEnabledStatus(status)
        );

        current.setUsername(username);
        current.setDisplayName(resolveDisplayName(request.getDisplayName(), username));
        current.setPhone(UserValidationSupport.trimToNull(request.getPhone()));
        current.setEmail(UserValidationSupport.trimToNull(request.getEmail()));
        current.setRoleCode(roleCode);
        current.setStatus(statusEnabledPair.status());
        current.setEnabled(statusEnabledPair.enabled());
        current.setRemark(UserValidationSupport.trimToNull(request.getRemark()));
        current.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(current);
        return toUserProfile(current);
    }

    @Transactional
    public void delete(Long id, Long currentUserId) {
        User current = getByIdOrThrow(id);
        if (DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(current.getUsername())) {
            throw new BusinessException("ADMIN_DELETE_FORBIDDEN", "默认管理员账号不可删除");
        }
        if (current.getId().equals(currentUserId)) {
            throw new BusinessException("SELF_DELETE_FORBIDDEN", "当前登录用户不能删除自己");
        }
        userMapper.deleteById(id);
    }

    @Transactional
    public void updateStatus(Long id, UpdateUserStatusRequest request) {
        User current = getByIdOrThrow(id);
        String status = normalizeStatusValue(request.getStatus(), true, resolveCurrentStatus(current));
        StatusEnabledPair statusEnabledPair = UserValidationSupport.normalizeStatusEnabled(
                status,
                request.getEnabled(),
                isEnabledStatus(status)
        );

        User updatedUser = new User();
        updatedUser.setId(id);
        updatedUser.setStatus(statusEnabledPair.status());
        updatedUser.setEnabled(statusEnabledPair.enabled());
        updatedUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(updatedUser);
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        getByIdOrThrow(id);
        User updatedUser = new User();
        updatedUser.setId(id);
        updatedUser.setPasswordHash(passwordEncoder.encode(request.getNewPassword().trim()));
        updatedUser.setForceChangePassword(Boolean.TRUE.equals(request.getForceChange()));
        updatedUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(updatedUser);
    }

    private User getByIdOrThrow(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void ensureUsernameUnique(String username, Long excludeId) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        Long count = userMapper.selectCount(wrapper);
        if (count != null && count > 0L) {
            throw new BusinessException("USERNAME_EXISTS", "用户名已存在");
        }
    }

    private String normalizeUsername(String username) {
        return UserValidationSupport.trimToNull(username);
    }

    private String resolveDisplayName(String displayName, String username) {
        String trimmed = UserValidationSupport.trimToNull(displayName);
        return StringUtils.hasText(trimmed) ? trimmed : username;
    }

    private String resolveCurrentStatus(User user) {
        return StringUtils.hasText(user.getStatus()) ? user.getStatus() : UserSecurityConstants.ENABLED_STATUS;
    }

    private String resolveCurrentRoleCode(User user) {
        return StringUtils.hasText(user.getRoleCode()) ? user.getRoleCode() : UserSecurityConstants.DEFAULT_ROLE_CODE;
    }

    private UserProfileVO toUserProfile(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setRoleCode(resolveCurrentRoleCode(user));
        String status = resolveCurrentStatus(user);
        vo.setStatus(status);
        vo.setEnabled(user.getEnabled() != null ? user.getEnabled() : isEnabledStatus(status));
        vo.setForceChangePassword(Boolean.TRUE.equals(user.getForceChangePassword()));
        vo.setLastLoginTime(user.getLastLoginTime());
        return vo;
    }

    private UserListItemVO toUserListItem(User user) {
        UserListItemVO vo = new UserListItemVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setRoleCode(resolveCurrentRoleCode(user));
        String status = resolveCurrentStatus(user);
        vo.setStatus(status);
        vo.setEnabled(user.getEnabled() != null ? user.getEnabled() : isEnabledStatus(status));
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setRemark(user.getRemark());
        return vo;
    }

    private String normalizeRoleCode(String roleCode, boolean allowNull) {
        return normalizeRoleCode(roleCode, allowNull, UserSecurityConstants.DEFAULT_ROLE_CODE);
    }

    private String normalizeRoleCode(String roleCode, boolean allowNull, String defaultRoleCode) {
        String normalized = normalizeUserMetadataValue("roleCode", roleCode, !allowNull, "ROLE_CODE_INVALID", "roleCode 非法");
        if (normalized == null) {
            return defaultRoleCode;
        }
        return normalized;
    }

    private String normalizeStatusValue(String status, boolean allowNull, String defaultStatus) {
        String normalized = normalizeUserMetadataValue("status", status, !allowNull, "USER_STATUS_INVALID", "status 非法");
        if (normalized == null) {
            return defaultStatus;
        }
        return normalized;
    }

    private boolean isEnabledStatus(String status) {
        return UserSecurityConstants.ENABLED_STATUS.equalsIgnoreCase(status);
    }

    private String normalizeUserMetadataValue(String bizFieldCode,
                                              String value,
                                              boolean required,
                                              String errorCode,
                                              String message) {
        try {
            return dataDictionaryUsageSupport.normalizeValueByUsage(APP_CODE, MODULE_CODE, bizFieldCode, value, required);
        } catch (BusinessException exception) {
            throw new BusinessException(errorCode, message);
        }
    }
}
