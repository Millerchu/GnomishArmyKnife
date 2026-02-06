package com.gak.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.user.dto.AuthResponse;
import com.gak.user.dto.LoginRequest;
import com.gak.user.dto.RegisterRequest;
import com.gak.user.dto.UserResponse;
import com.gak.user.entity.User;
import com.gak.user.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 用户注册与登录服务。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 构造方法注入依赖。
     *
     * @param userMapper 用户数据访问对象
     * @param passwordEncoder 密码编码器
     */
    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
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
     * @return 登录结果
     */
    public AuthResponse login(LoginRequest request) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", request.getUsername());
        User user = userMapper.selectOne(wrapper);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "用户名或密码错误");
        }

        String token = UUID.randomUUID().toString();
        UserResponse response = new UserResponse(user.getId(), user.getUsername(), user.getDisplayName());
        return new AuthResponse(token, response);
    }
}