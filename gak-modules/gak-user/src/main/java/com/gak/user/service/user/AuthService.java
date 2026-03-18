package com.gak.user.service.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.user.domain.user.User;
import com.gak.user.dto.user.ChangePasswordRequest;
import com.gak.user.dto.user.LoginRequest;
import com.gak.user.dto.user.RegisterRequest;
import com.gak.user.enums.user.UserRoleCode;
import com.gak.user.enums.user.UserStatus;
import com.gak.user.mapper.user.UserMapper;
import com.gak.user.service.user.UserValidationSupport.StatusEnabledPair;
import com.gak.user.vo.user.UserLoginVO;
import com.gak.user.vo.user.UserProfileVO;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 认证服务。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordCryptoService passwordCryptoService;
    private final TokenService tokenService;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       PasswordCryptoService passwordCryptoService,
                       TokenService tokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.passwordCryptoService = passwordCryptoService;
        this.tokenService = tokenService;
    }

    @Transactional
    public UserProfileVO register(RegisterRequest request) {
        String username = normalizeUsername(request.getUsername());
        ensureUsernameUnique(username, null);

        String password = UserValidationSupport.resolvePassword(
                request.getInitialPassword(),
                request.getPassword(),
                true
        );
        UserValidationSupport.validatePasswordLength(password);

        String roleCode = resolveRegisterRoleCode(request.getRoleCode());
        StatusEnabledPair statusEnabledPair = resolveRegisterStatus(request.getStatus(), request.getEnabled());

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
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);

        return toUserProfile(user);
    }

    @Transactional
    public UserLoginVO login(LoginRequest request, HttpSession session, String captchaSessionKey) {
        String captcha = (String) session.getAttribute(captchaSessionKey);
        session.removeAttribute(captchaSessionKey);
        if (!StringUtils.hasText(captcha) || !captcha.equalsIgnoreCase(request.getCaptcha())) {
            throw new BusinessException("CAPTCHA_INVALID", "验证码错误或已过期");
        }

        User user = getByUsername(normalizeUsername(request.getUsername()));
        if (user == null) {
            throw new BusinessException("AUTH_INVALID", "用户名或密码错误");
        }

        String plainPassword = passwordCryptoService.decrypt(request.getEncryptedPassword());
        if (!passwordEncoder.matches(plainPassword, user.getPasswordHash())) {
            throw new BusinessException("AUTH_INVALID", "用户名或密码错误");
        }
        if (!isEnabled(user)) {
            throw new BusinessException("USER_DISABLED", "用户已被禁用");
        }

        LocalDateTime now = LocalDateTime.now();
        User updatedUser = new User();
        updatedUser.setId(user.getId());
        updatedUser.setLastLoginTime(now);
        updatedUser.setUpdatedAt(now);
        userMapper.updateById(updatedUser);
        user.setLastLoginTime(now);
        user.setUpdatedAt(now);

        return new UserLoginVO(tokenService.issueToken(user.getId()), toUserProfile(user));
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = getByUsername(normalizeUsername(request.getUsername()));
        if (user == null) {
            throw new BusinessException("AUTH_INVALID", "用户名或原密码错误");
        }

        String oldPlainPassword = passwordCryptoService.decrypt(request.getOldEncryptedPassword());
        String newPlainPassword = passwordCryptoService.decrypt(request.getNewEncryptedPassword());
        if (oldPlainPassword.equals(newPlainPassword)) {
            throw new BusinessException("PASSWORD_UNCHANGED", "新密码不能与原密码相同");
        }
        if (!passwordEncoder.matches(oldPlainPassword, user.getPasswordHash())) {
            throw new BusinessException("AUTH_INVALID", "用户名或原密码错误");
        }
        UserValidationSupport.validatePasswordLength(newPlainPassword);

        User updatedUser = new User();
        updatedUser.setId(user.getId());
        updatedUser.setPasswordHash(passwordEncoder.encode(newPlainPassword));
        updatedUser.setForceChangePassword(false);
        updatedUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(updatedUser);
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

    private User getByUsername(String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        return userMapper.selectOne(wrapper);
    }

    private String normalizeUsername(String username) {
        return UserValidationSupport.trimToNull(username);
    }

    private String resolveDisplayName(String displayName, String username) {
        String trimmed = UserValidationSupport.trimToNull(displayName);
        return StringUtils.hasText(trimmed) ? trimmed : username;
    }

    private String resolveRegisterRoleCode(String roleCode) {
        String normalized = UserValidationSupport.normalizeRoleCode(roleCode, UserRoleCode.USER.name());
        if (!UserRoleCode.USER.name().equals(normalized)) {
            throw new BusinessException("ROLE_CODE_INVALID", "注册用户角色只能为 USER");
        }
        return normalized;
    }

    private StatusEnabledPair resolveRegisterStatus(String status, Boolean enabled) {
        StatusEnabledPair pair = UserValidationSupport.normalizeStatusEnabled(status, enabled, UserStatus.ENABLED);
        if (!UserStatus.ENABLED.name().equals(pair.status()) || !pair.enabled()) {
            throw new BusinessException("USER_STATUS_INVALID", "注册用户状态只能为 ENABLED");
        }
        return pair;
    }

    private boolean isEnabled(User user) {
        String status = StringUtils.hasText(user.getStatus()) ? user.getStatus() : UserStatus.ENABLED.name();
        boolean enabled = user.getEnabled() != null ? user.getEnabled() : UserStatus.fromCode(status).isEnabled();
        return UserStatus.ENABLED.name().equalsIgnoreCase(status) && enabled;
    }

    private UserProfileVO toUserProfile(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setRoleCode(StringUtils.hasText(user.getRoleCode()) ? user.getRoleCode() : UserRoleCode.USER.name());
        String status = StringUtils.hasText(user.getStatus()) ? user.getStatus() : UserStatus.ENABLED.name();
        vo.setStatus(status);
        vo.setEnabled(user.getEnabled() != null ? user.getEnabled() : UserStatus.fromCode(status).isEnabled());
        vo.setForceChangePassword(Boolean.TRUE.equals(user.getForceChangePassword()));
        vo.setLastLoginTime(user.getLastLoginTime());
        return vo;
    }
}
