package com.gak.user.controller.user;

import com.gak.framework.exception.BusinessException;
import com.gak.user.service.user.AuthService;
import com.gak.user.service.user.NasSsoService;
import com.gak.user.service.user.PasswordCryptoService;
import com.gak.user.service.user.TokenService;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * 认证控制器的 NAS SSO 相对跳转契约测试。
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String STATE = "a".repeat(43);

    @Mock
    private AuthService authService;

    @Mock
    private PasswordCryptoService passwordCryptoService;

    @Mock
    private NasSsoService nasSsoService;

    @Mock
    private TokenService tokenService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(
                authService,
                passwordCryptoService,
                nasSsoService,
                tokenService
        );
    }

    @Test
    void handoffShouldRedirectToRelativeGatewayCallback() {
        when(nasSsoService.createHandoff("nas-token", "header", STATE))
                .thenReturn("exchange-code");

        ResponseEntity<Void> response =
                authController.nasSsoHandoff("nas-token", "header", STATE);

        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals(
                URI.create("/gak/nas-sso-callback?code=exchange-code&state=" + STATE),
                response.getHeaders().getLocation()
        );
    }

    @Test
    void handoffFailureShouldUseSafeRelativeLoginReason() {
        when(nasSsoService.createHandoff("nas-token", "header", STATE))
                .thenThrow(new BusinessException("NAS_SSO_TOKEN_INVALID", "NAS token 无效"));

        ResponseEntity<Void> response =
                authController.nasSsoHandoff("nas-token", "header", STATE);

        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals(
                URI.create("/gak/syslogin?reason=nas_sso_token_invalid"),
                response.getHeaders().getLocation()
        );
    }
}
