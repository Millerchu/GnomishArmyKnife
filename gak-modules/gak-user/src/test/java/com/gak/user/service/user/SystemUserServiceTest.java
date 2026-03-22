package com.gak.user.service.user;

import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemUserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DataDictionaryUsageSupport dataDictionaryUsageSupport;

    @InjectMocks
    private SystemUserService systemUserService;

    @Test
    void deleteShouldRejectAdminUser() {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        when(userMapper.selectById(1L)).thenReturn(admin);

        BusinessException exception = assertThrows(BusinessException.class, () -> systemUserService.delete(1L, 2L));
        assertEquals("ADMIN_DELETE_FORBIDDEN", exception.getCode());
    }

    @Test
    void deleteShouldRejectCurrentUser() {
        User current = new User();
        current.setId(5L);
        current.setUsername("zhangsan");
        when(userMapper.selectById(5L)).thenReturn(current);

        BusinessException exception = assertThrows(BusinessException.class, () -> systemUserService.delete(5L, 5L));
        assertEquals("SELF_DELETE_FORBIDDEN", exception.getCode());
    }
}
