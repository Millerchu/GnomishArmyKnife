package com.gak.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.user.dto.AuthResponse;
import com.gak.user.dto.ChangePasswordRequest;
import com.gak.user.dto.LoginRequest;
import com.gak.user.dto.RegisterRequest;
import com.gak.user.dto.UserResponse;
import com.gak.user.entity.User;
import com.gak.user.mapper.UserMapper;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 用户注册与登录服务。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordCryptoService passwordCryptoService;

    /**
     * 构造方法注入依赖。
     *
     * @param userMapper 用户数据访问对象
     * @param passwordEncoder 密码编码器
     * @param passwordCryptoService 密码加解密服务
     */
    public UserService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       PasswordCryptoService passwordCryptoService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.passwordCryptoService = passwordCryptoService;
    }

    /**
     * 用户注册。
     *
     * @param request 注册请求
     * @return 用户基础信息
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", request.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "用户名已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);

        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName());
    }

    /**
     * 用户登录。
     *
     * @param request 登录请求
     * @param session 当前会话
     * @param captchaSessionKey 验证码 Session key
     * @return 登录结果
     */
    public AuthResponse login(LoginRequest request, HttpSession session, String captchaSessionKey) {
        String captcha = (String) session.getAttribute(captchaSessionKey);
        session.removeAttribute(captchaSessionKey);
        if (captcha == null || !captcha.equalsIgnoreCase(request.getCaptcha())) {
            throw new BusinessException("CAPTCHA_INVALID", "验证码错误或已过期");
        }

        String plainPassword = passwordCryptoService.decrypt(request.getEncryptedPassword());

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", request.getUsername());
        User user = userMapper.selectOne(wrapper);
        if (user == null || !passwordEncoder.matches(plainPassword, user.getPasswordHash())) {
            throw new BusinessException("AUTH_INVALID", "用户名或密码错误");
        }

        String token = UUID.randomUUID().toString();
        UserResponse response = new UserResponse(user.getId(), user.getUsername(), user.getDisplayName());
        return new AuthResponse(token, response);
    }

    /**
     * 用户修改密码。
     *
     * @param request 修改密码请求
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        String oldPlainPassword = passwordCryptoService.decrypt(request.getOldEncryptedPassword());
        String newPlainPassword = passwordCryptoService.decrypt(request.getNewEncryptedPassword());
        if (oldPlainPassword.equals(newPlainPassword)) {
            throw new BusinessException("PASSWORD_UNCHANGED", "新密码不能与原密码相同");
        }

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", request.getUsername());
        User user = userMapper.selectOne(wrapper);
        if (user == null || !passwordEncoder.matches(oldPlainPassword, user.getPasswordHash())) {
            throw new BusinessException("AUTH_INVALID", "用户名或原密码错误");
        }

        User updatedUser = new User();
        updatedUser.setId(user.getId());
        updatedUser.setPasswordHash(passwordEncoder.encode(newPlainPassword));
        updatedUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(updatedUser);
    }
}
