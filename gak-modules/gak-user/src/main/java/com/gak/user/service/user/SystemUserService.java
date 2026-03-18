package com.gak.user.service.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.user.domain.user.User;
import com.gak.user.dto.user.CreateUserRequest;
import com.gak.user.dto.user.ResetPasswordRequest;
import com.gak.user.dto.user.UpdateUserRequest;
import com.gak.user.dto.user.UpdateUserStatusRequest;
import com.gak.user.dto.user.UserQueryRequest;
import com.gak.user.enums.user.UserRoleCode;
import com.gak.user.enums.user.UserStatus;
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

    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public SystemUserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
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
            String status = UserValidationSupport.normalizeStatusEnabled(request.getStatus(), null, UserStatus.ENABLED).status();
            wrapper.eq("status", status);
        }
        if (StringUtils.hasText(request.getRoleCode())) {
            wrapper.eq("role_code", UserValidationSupport.normalizeRoleCode(request.getRoleCode(), UserRoleCode.USER.name()));
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
        StatusEnabledPair statusEnabledPair = UserValidationSupport.normalizeStatusEnabled(
                request.getStatus(),
                request.getEnabled(),
                UserStatus.ENABLED
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
        user.setRoleCode(UserValidationSupport.normalizeRoleCode(request.getRoleCode(), UserRoleCode.USER.name()));
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
        StatusEnabledPair statusEnabledPair = UserValidationSupport.normalizeStatusEnabled(
                request.getStatus(),
                request.getEnabled(),
                resolveCurrentStatus(current)
        );

        current.setUsername(username);
        current.setDisplayName(resolveDisplayName(request.getDisplayName(), username));
        current.setPhone(UserValidationSupport.trimToNull(request.getPhone()));
        current.setEmail(UserValidationSupport.trimToNull(request.getEmail()));
        current.setRoleCode(UserValidationSupport.normalizeRoleCode(request.getRoleCode(), resolveCurrentRoleCode(current)));
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
        StatusEnabledPair statusEnabledPair = UserValidationSupport.normalizeStatusEnabled(
                request.getStatus(),
                request.getEnabled(),
                resolveCurrentStatus(current)
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

    private UserStatus resolveCurrentStatus(User user) {
        String status = StringUtils.hasText(user.getStatus()) ? user.getStatus() : UserStatus.ENABLED.name();
        return UserStatus.fromCode(status);
    }

    private String resolveCurrentRoleCode(User user) {
        return StringUtils.hasText(user.getRoleCode()) ? user.getRoleCode() : UserRoleCode.USER.name();
    }

    private UserProfileVO toUserProfile(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setRoleCode(resolveCurrentRoleCode(user));
        String status = StringUtils.hasText(user.getStatus()) ? user.getStatus() : UserStatus.ENABLED.name();
        vo.setStatus(status);
        vo.setEnabled(user.getEnabled() != null ? user.getEnabled() : UserStatus.fromCode(status).isEnabled());
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
        String status = StringUtils.hasText(user.getStatus()) ? user.getStatus() : UserStatus.ENABLED.name();
        vo.setStatus(status);
        vo.setEnabled(user.getEnabled() != null ? user.getEnabled() : UserStatus.fromCode(status).isEnabled());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setRemark(user.getRemark());
        return vo;
    }
}
