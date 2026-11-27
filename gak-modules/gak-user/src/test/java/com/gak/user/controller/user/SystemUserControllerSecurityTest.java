package com.gak.user.controller.user;

import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.framework.exception.GlobalExceptionHandler;
import com.gak.framework.exception.SystemExceptionCapture;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.user.service.user.SystemUserService;
import com.gak.user.service.user.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台用户管理控制器权限回归测试。
 */
class SystemUserControllerSecurityTest {

    private UserMapper userMapper;
    private TokenService tokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        DataDictionaryUsageSupport dictionarySupport = mock(DataDictionaryUsageSupport.class);
        tokenService = new TokenService(userMapper, Duration.ofHours(24), Duration.ofHours(2), 100);
        SystemUserService systemUserService = new SystemUserService(userMapper, passwordEncoder, dictionarySupport, tokenService);
        SystemUserController controller = new SystemUserController(systemUserService, tokenService);
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler(
                new StaticListableBeanFactory().getBeanProvider(SystemExceptionCapture.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    void regularUserShouldReceive403AcrossAllSystemUserEndpoints() throws Exception {
        User regularUser = new User();
        regularUser.setId(100L);
        regularUser.setUsername("user1");
        regularUser.setRoleCode("USER");
        regularUser.setEnabled(true);
        regularUser.setStatus("ENABLED");
        when(userMapper.selectById(100L)).thenReturn(regularUser);

        String authHeader = "Bearer " + tokenService.issueToken(100L);

        mockMvc.perform(get("/system/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/system/users").header("Authorization", authHeader))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/system/users")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"evil\",\"initialPassword\":\"Abc12345\",\"roleCode\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/system/users/1")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user1\",\"roleCode\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/system/users/1").header("Authorization", authHeader))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/system/users/1/status")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"enabled\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/system/users/1/reset-password")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Abc12345\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledAdminTokenShouldBeRejectedAndRevoked() throws Exception {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRoleCode("ADMIN");
        admin.setEnabled(true);
        admin.setStatus("ENABLED");
        when(userMapper.selectById(1L)).thenReturn(admin);

        String authHeader = "Bearer " + tokenService.issueToken(1L);
        mockMvc.perform(get("/system/users").header("Authorization", authHeader))
                .andExpect(status().isOk());

        admin.setEnabled(false);
        admin.setStatus("DISABLED");
        mockMvc.perform(get("/system/users").header("Authorization", authHeader))
                .andExpect(status().isUnauthorized());

        admin.setEnabled(true);
        admin.setStatus("ENABLED");
        mockMvc.perform(get("/system/users").header("Authorization", authHeader))
                .andExpect(status().isUnauthorized());
    }
}
