package com.gak.user.controller;

import com.gak.user.dto.AuthResponse;
import com.gak.user.dto.LoginRequest;
import com.gak.user.dto.RegisterRequest;
import com.gak.user.dto.UserResponse;
import com.gak.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口控制器。
 * <p>
 * 提供用户注册与登录的对外 API。
 * </p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    /**
     * 构造方法注入用户服务。
     *
     * @param userService 用户服务
     */
    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户注册接口。
     *
     * @param request 注册请求体
     * @return 注册成功的用户信息
     */
    @PostMapping("/register")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    /**
     * 用户登录接口。
     *
     * @param request 登录请求体
     * @return 登录结果（包含伪 token）
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }
}