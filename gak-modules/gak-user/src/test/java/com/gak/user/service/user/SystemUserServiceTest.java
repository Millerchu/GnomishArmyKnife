package com.gak.user.service.user;

import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.user.domain.user.User;
import com.gak.user.dto.user.CreateUserRequest;
import com.gak.user.dto.user.ResetPasswordRequest;
import com.gak.user.dto.user.UpdateUserRequest;
import com.gak.user.dto.user.UpdateUserStatusRequest;
import com.gak.user.dto.user.UserQueryRequest;
import com.gak.user.mapper.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemUserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DataDictionaryUsageSupport dataDictionaryUsageSupport;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private SystemUserService systemUserService;

    @Test
    void deleteShouldRejectAdminUser() {
        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "ops", "ADMIN", true, "ENABLED"));
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "admin", "ADMIN", true, "ENABLED"));

        BusinessException exception = assertThrows(BusinessException.class, () -> systemUserService.delete(1L, 2L));
        assertEquals("ADMIN_DELETE_FORBIDDEN", exception.getCode());
        verify(tokenService, never()).revokeByUserId(any());
    }

    @Test
    void deleteShouldRejectCurrentUser() {
        when(userMapper.selectById(5L)).thenReturn(buildUser(5L, "zhangsan", "ADMIN", true, "ENABLED"));

        BusinessException exception = assertThrows(BusinessException.class, () -> systemUserService.delete(5L, 5L));
        assertEquals("SELF_DELETE_FORBIDDEN", exception.getCode());
    }

    @Test
    void allManagementMethodsShouldRejectNonAdminWithForbidden() {
        when(userMapper.selectById(10L)).thenReturn(buildUser(10L, "normal", "USER", true, "ENABLED"));

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> systemUserService.page(10L, new UserQueryRequest())).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> systemUserService.create(10L, new CreateUserRequest())).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> systemUserService.update(10L, 1L, new UpdateUserRequest())).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> systemUserService.delete(1L, 10L)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> systemUserService.updateStatus(10L, 1L, new UpdateUserStatusRequest())).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> systemUserService.resetPassword(10L, 1L, new ResetPasswordRequest())).getStatusCode());
    }

    @Test
    void disabledAdminOperatorShouldBeRejectedWithUnauthorized() {
        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "ops", "ADMIN", false, "DISABLED"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> systemUserService.page(2L, new UserQueryRequest()));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void updateShouldForbidDowngradingOrDisablingDefaultAdminAndSelf() {
        when(userMapper.selectById(2L)).thenReturn(buildUser(2L, "ops", "ADMIN", true, "ENABLED"));
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "admin", "ADMIN", true, "ENABLED"));
        when(dataDictionaryUsageSupport.normalizeValueByUsage("APP_USER_MANAGEMENT", "SYSTEM_USER", "roleCode", "USER", false))
                .thenReturn("USER");
        when(dataDictionaryUsageSupport.normalizeValueByUsage("APP_USER_MANAGEMENT", "SYSTEM_USER", "status", "ENABLED", false))
                .thenReturn("ENABLED");

        UpdateUserRequest downgradeAdmin = new UpdateUserRequest();
        downgradeAdmin.setUsername("admin");
        downgradeAdmin.setRoleCode("USER");
        downgradeAdmin.setStatus("ENABLED");
        downgradeAdmin.setEnabled(true);

        BusinessException downgradeEx = assertThrows(BusinessException.class,
                () -> systemUserService.update(2L, 1L, downgradeAdmin));
        assertEquals("ADMIN_ROLE_CHANGE_FORBIDDEN", downgradeEx.getCode());

        UpdateUserRequest disableSelf = new UpdateUserRequest();
        disableSelf.setUsername("ops");
        disableSelf.setRoleCode("ADMIN");
        disableSelf.setStatus("DISABLED");
        disableSelf.setEnabled(false);
        when(dataDictionaryUsageSupport.normalizeValueByUsage("APP_USER_MANAGEMENT", "SYSTEM_USER", "roleCode", "ADMIN", false))
                .thenReturn("ADMIN");
        when(dataDictionaryUsageSupport.normalizeValueByUsage("APP_USER_MANAGEMENT", "SYSTEM_USER", "status", "DISABLED", false))
                .thenReturn("DISABLED");

        BusinessException selfEx = assertThrows(BusinessException.class,
                () -> systemUserService.update(2L, 2L, disableSelf));
        assertEquals("SELF_DISABLE_FORBIDDEN", selfEx.getCode());
    }

    @Test
    void disablingDeletingAndResettingPasswordShouldRevokeTargetTokens() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L, "admin", "ADMIN", true, "ENABLED"));
        when(userMapper.selectById(9L)).thenReturn(buildUser(9L, "target", "USER", true, "ENABLED"));
        when(dataDictionaryUsageSupport.normalizeValueByUsage("APP_USER_MANAGEMENT", "SYSTEM_USER", "status", "DISABLED", false))
                .thenReturn("DISABLED");
        when(passwordEncoder.encode("NewPass123")).thenReturn("ENCODED");

        UpdateUserStatusRequest disableRequest = new UpdateUserStatusRequest();
        disableRequest.setStatus("DISABLED");
        disableRequest.setEnabled(false);
        systemUserService.updateStatus(1L, 9L, disableRequest);
        verify(tokenService).revokeByUserId(9L);

        ResetPasswordRequest resetRequest = new ResetPasswordRequest();
        resetRequest.setNewPassword("NewPass123");
        resetRequest.setForceChange(true);
        systemUserService.resetPassword(1L, 9L, resetRequest);
        verify(tokenService, org.mockito.Mockito.times(2)).revokeByUserId(9L);

        systemUserService.delete(9L, 1L);
        verify(tokenService, org.mockito.Mockito.times(3)).revokeByUserId(9L);
    }

    private User buildUser(Long id, String username, String roleCode, boolean enabled, String status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRoleCode(roleCode);
        user.setEnabled(enabled);
        user.setStatus(status);
        return user;
    }
}
