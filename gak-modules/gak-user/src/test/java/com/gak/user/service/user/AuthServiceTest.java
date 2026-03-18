package com.gak.user.service.user;

import com.gak.framework.exception.BusinessException;
import com.gak.user.domain.user.User;
import com.gak.user.dto.user.RegisterRequest;
import com.gak.user.mapper.user.UserMapper;
import com.gak.user.vo.user.UserProfileVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordCryptoService passwordCryptoService;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerShouldUseInitialPasswordAndPersistDefaultFields() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("zhangsan");
        request.setPassword("Abc123!");
        request.setInitialPassword("Abc123!");
        request.setDisplayName("张三");
        request.setPhone("13800001234");
        request.setEmail("zhangsan@example.com");

        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("Abc123!")).thenReturn("ENCODED_PASSWORD");
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        UserProfileVO result = authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User savedUser = captor.getValue();
        assertEquals("zhangsan", savedUser.getUsername());
        assertEquals("ENCODED_PASSWORD", savedUser.getPasswordHash());
        assertEquals("USER", savedUser.getRoleCode());
        assertEquals("ENABLED", savedUser.getStatus());
        assertTrue(savedUser.getEnabled());
        assertFalse(savedUser.getForceChangePassword());
        assertEquals(1L, result.getId());
        assertEquals("zhangsan", result.getUsername());
    }

    @Test
    void registerShouldRejectMismatchedPasswordFields() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("zhangsan");
        request.setPassword("oldPassword");
        request.setInitialPassword("newPassword");

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals("PASSWORD_MISMATCH", exception.getCode());
    }
}
