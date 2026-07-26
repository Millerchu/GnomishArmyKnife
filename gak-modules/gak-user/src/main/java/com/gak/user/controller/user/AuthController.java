package com.gak.user.controller.user;

import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.ApiResponse;
import com.gak.user.dto.user.ChangePasswordRequest;
import com.gak.user.dto.user.LoginRequest;
import com.gak.user.dto.user.NasSsoExchangeRequest;
import com.gak.user.dto.user.RegisterRequest;
import com.gak.user.service.user.AuthService;
import com.gak.user.service.user.NasSsoService;
import com.gak.user.service.user.PasswordCryptoService;
import com.gak.user.service.user.TokenService;
import com.gak.user.vo.user.CaptchaVO;
import com.gak.user.vo.user.PublicKeyVO;
import com.gak.user.vo.user.UserLoginVO;
import com.gak.user.vo.user.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 认证控制器。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String LOGIN_CAPTCHA_SESSION_KEY = "LOGIN_CAPTCHA";
    private static final String NAS_SSO_CALLBACK_PATH = "/gak/nas-sso-callback";
    private static final String SYSTEM_LOGIN_PATH = "/gak/syslogin";
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int CAPTCHA_LENGTH = 4;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<String> SAFE_NAS_SSO_FAILURE_CODES = Set.of(
            "NAS_SSO_DISABLED",
            "NAS_SSO_UNAVAILABLE",
            "NAS_SSO_TOKEN_INVALID",
            "NAS_SSO_USER_INVALID",
            "NAS_SSO_USER_NOT_FOUND",
            "USER_DISABLED",
            "NAS_SSO_STATE_INVALID"
    );

    private final AuthService authService;
    private final PasswordCryptoService passwordCryptoService;
    private final NasSsoService nasSsoService;
    private final TokenService tokenService;

    public AuthController(AuthService authService,
                          PasswordCryptoService passwordCryptoService,
                          NasSsoService nasSsoService,
                          TokenService tokenService) {
        this.authService = authService;
        this.passwordCryptoService = passwordCryptoService;
        this.nasSsoService = nasSsoService;
        this.tokenService = tokenService;
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

    @PostMapping("/nas-sso/handoff")
    public ResponseEntity<Void> nasSsoHandoff(@RequestParam(required = false) String nasToken,
                                               @RequestParam(required = false) String tokenWhere,
                                               @RequestParam(required = false) String state) {
        try {
            String code = nasSsoService.createHandoff(nasToken, tokenWhere, state);
            URI callback = buildRelativeUri(NAS_SSO_CALLBACK_PATH, "code", code, "state", state);
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(callback).build();
        } catch (BusinessException exception) {
            URI fallback = buildRelativeUri(
                    SYSTEM_LOGIN_PATH,
                    "reason",
                    resolveNasSsoFailureReason(exception)
            );
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(fallback).build();
        }
    }

    @PostMapping("/nas-sso/exchange")
    public ApiResponse<UserLoginVO> nasSsoExchange(@Valid @RequestBody NasSsoExchangeRequest request) {
        return ApiResponse.success(nasSsoService.exchange(request.getCode(), request.getState()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        tokenService.revoke(request);
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

    private URI buildRelativeUri(String path, String... queryPairs) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath(path);
        for (int index = 0; index < queryPairs.length; index += 2) {
            builder.queryParam(queryPairs[index], queryPairs[index + 1]);
        }
        return builder.build().encode().toUri();
    }

    private String resolveNasSsoFailureReason(BusinessException exception) {
        String code = exception.getCode();
        return SAFE_NAS_SSO_FAILURE_CODES.contains(code)
                ? code.toLowerCase(Locale.ROOT)
                : "nas_sso_failed";
    }
}
