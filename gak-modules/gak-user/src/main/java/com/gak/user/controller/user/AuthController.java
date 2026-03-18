package com.gak.user.controller.user;

import com.gak.framework.response.ApiResponse;
import com.gak.user.dto.user.ChangePasswordRequest;
import com.gak.user.dto.user.LoginRequest;
import com.gak.user.dto.user.RegisterRequest;
import com.gak.user.service.user.AuthService;
import com.gak.user.service.user.PasswordCryptoService;
import com.gak.user.vo.user.CaptchaVO;
import com.gak.user.vo.user.PublicKeyVO;
import com.gak.user.vo.user.UserLoginVO;
import com.gak.user.vo.user.UserProfileVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String LOGIN_CAPTCHA_SESSION_KEY = "LOGIN_CAPTCHA";
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int CAPTCHA_LENGTH = 4;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthService authService;
    private final PasswordCryptoService passwordCryptoService;

    public AuthController(AuthService authService, PasswordCryptoService passwordCryptoService) {
        this.authService = authService;
        this.passwordCryptoService = passwordCryptoService;
    }

    @GetMapping("/captcha")
    public ApiResponse<CaptchaVO> captcha(HttpSession session) {
        String captcha = generateCaptcha();
        session.setAttribute(LOGIN_CAPTCHA_SESSION_KEY, captcha);
        return ApiResponse.success(new CaptchaVO(captcha));
    }

    @GetMapping("/password-public-key")
    public ApiResponse<PublicKeyVO> passwordPublicKey() {
        return ApiResponse.success(new PublicKeyVO(passwordCryptoService.getPublicKey()));
    }

    @PostMapping("/register")
    public ApiResponse<UserProfileVO> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<UserLoginVO> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        return ApiResponse.success(authService.login(request, session, LOGIN_CAPTCHA_SESSION_KEY));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.success();
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
