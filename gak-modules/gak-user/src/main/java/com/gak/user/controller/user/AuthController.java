package com.gak.user.controller.user;

import com.gak.framework.security.PublicEndpoint;
import com.gak.framework.response.ApiResponse;
import com.gak.user.dto.user.ChangePasswordRequest;
import com.gak.user.dto.user.LoginRequest;
import com.gak.user.dto.user.RegisterRequest;
import com.gak.user.service.user.AuthService;
import com.gak.user.service.user.LoginCaptchaService;
import com.gak.user.service.user.PasswordCryptoService;
import com.gak.user.service.user.TokenService;
import com.gak.user.vo.user.CaptchaVO;
import com.gak.user.vo.user.PublicKeyVO;
import com.gak.user.vo.user.UserLoginVO;
import com.gak.user.vo.user.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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

    private final AuthService authService;
    private final PasswordCryptoService passwordCryptoService;
    private final TokenService tokenService;

    private final LoginCaptchaService captchaService;

    public AuthController(AuthService authService,
                          PasswordCryptoService passwordCryptoService,
                          TokenService tokenService,
                          LoginCaptchaService captchaService) {
        this.authService = authService;
        this.passwordCryptoService = passwordCryptoService;
        this.tokenService = tokenService;
        this.captchaService = captchaService;
    }

    @PublicEndpoint
    @GetMapping("/captcha")
    public ResponseEntity<ApiResponse<CaptchaVO>> captcha(HttpSession session, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(new CaptchaVO(captchaService.issue(session, request.getRemoteAddr()))));
    }

    @PublicEndpoint
    @GetMapping("/password-public-key")
    public ApiResponse<PublicKeyVO> passwordPublicKey() {
        return ApiResponse.success(new PublicKeyVO(passwordCryptoService.getPublicKey()));
    }

    @PublicEndpoint
    @PostMapping("/register")
    public ApiResponse<UserProfileVO> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PublicEndpoint
    @PostMapping("/login")
    public ApiResponse<UserLoginVO> login(@Valid @RequestBody LoginRequest request, HttpSession session,
                                          HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.login(request, session, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        authService.changePassword(tokenService.requirePasswordChangeUserId(httpRequest), request);
        return ApiResponse.success();
    }

    @PublicEndpoint
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpServletRequest) {
        tokenService.revokeRequestToken(httpServletRequest);
        return ApiResponse.success();
    }
}
