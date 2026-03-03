package com.gak.user.controller;

import com.gak.user.dto.AuthResponse;
import com.gak.user.dto.CaptchaResponse;
import com.gak.user.dto.LoginRequest;
import com.gak.user.dto.PublicKeyResponse;
import com.gak.user.dto.RegisterRequest;
import com.gak.user.dto.UserResponse;
import com.gak.user.service.PasswordCryptoService;
import com.gak.user.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    private static final String LOGIN_CAPTCHA_SESSION_KEY = "LOGIN_CAPTCHA";
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int CAPTCHA_LENGTH = 4;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserService userService;
    private final PasswordCryptoService passwordCryptoService;

    /**
     * 构造方法注入用户服务。
     *
     * @param userService 用户服务
     * @param passwordCryptoService 密码加解密服务
     */
    public AuthController(UserService userService, PasswordCryptoService passwordCryptoService) {
        this.userService = userService;
        this.passwordCryptoService = passwordCryptoService;
    }

    /**
     * 生成登录验证码，并缓存在 session 中。
     *
     * @param session 当前会话
     * @return 4 位字母数字验证码
     */
    @GetMapping("/captcha")
    public CaptchaResponse captcha(HttpSession session) {
        String captcha = generateCaptcha();
        session.setAttribute(LOGIN_CAPTCHA_SESSION_KEY, captcha);
        return new CaptchaResponse(captcha);
    }

    /**
     * 获取登录密码加密公钥。
     *
     * @return Base64 编码的 RSA 公钥
     */
    @GetMapping("/password-public-key")
    public PublicKeyResponse passwordPublicKey() {
        return new PublicKeyResponse(passwordCryptoService.getPublicKey());
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
     * @param session 当前会话
     * @return 登录结果（包含伪 token）
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        return userService.login(request, session, LOGIN_CAPTCHA_SESSION_KEY);
    }

    private String generateCaptcha() {
        StringBuilder sb = new StringBuilder(CAPTCHA_LENGTH);
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            int index = SECURE_RANDOM.nextInt(CAPTCHA_CHARS.length());
            sb.append(CAPTCHA_CHARS.charAt(index));
        }
        return sb.toString();
    }
}
