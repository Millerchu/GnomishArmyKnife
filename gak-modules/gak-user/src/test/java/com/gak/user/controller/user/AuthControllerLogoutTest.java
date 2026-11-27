package com.gak.user.controller.user;

import com.gak.framework.exception.GlobalExceptionHandler;
import com.gak.framework.exception.SystemExceptionCapture;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.user.service.user.AuthService;
import com.gak.user.service.user.LoginCaptchaService;
import com.gak.user.service.user.PasswordCryptoService;
import com.gak.user.service.user.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登出接口吊销令牌测试。
 */
class AuthControllerLogoutTest {

    @Test
    void logoutShouldRevokeBearerToken() throws Exception {
        UserMapper userMapper = mock(UserMapper.class);
        User user = new User();
        user.setId(7L);
        user.setEnabled(true);
        user.setStatus("ENABLED");
        when(userMapper.selectById(7L)).thenReturn(user);

        TokenService tokenService = new TokenService(
                userMapper, Duration.ofHours(24), Duration.ofHours(2), 100);
        AuthController controller = new AuthController(
                mock(AuthService.class), mock(PasswordCryptoService.class), tokenService, new LoginCaptchaService());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(
                        new StaticListableBeanFactory().getBeanProvider(SystemExceptionCapture.class)))
                .build();

        String token = tokenService.issueToken(7L);
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        MockHttpServletRequest checkRequest = new MockHttpServletRequest();
        checkRequest.addHeader("Authorization", "Bearer " + token);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> tokenService.requireCurrentUserId(checkRequest));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());

        // 无 token 登出也应幂等返回成功
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk());
    }
}
