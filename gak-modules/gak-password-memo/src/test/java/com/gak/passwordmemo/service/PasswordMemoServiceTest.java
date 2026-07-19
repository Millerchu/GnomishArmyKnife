package com.gak.passwordmemo.service;

import com.gak.framework.exception.BusinessException;
import com.gak.passwordmemo.domain.PasswordMemo;
import com.gak.passwordmemo.domain.PasswordMemoHistory;
import com.gak.passwordmemo.dto.SavePasswordMemoRequest;
import com.gak.passwordmemo.dto.UpdateMemoPasswordRequest;
import com.gak.passwordmemo.dto.VerifyAccessRequest;
import com.gak.passwordmemo.mapper.PasswordMemoMapper;
import com.gak.passwordmemo.mapper.PasswordMemoHistoryMapper;
import com.gak.passwordmemo.vo.PasswordMemoDetailVO;
import com.gak.passwordmemo.vo.VerifyAccessResponse;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordMemoServiceTest {

    @Mock
    private PasswordMemoMapper passwordMemoMapper;

    @Mock
    private PasswordMemoHistoryMapper passwordMemoHistoryMapper;

    @Mock
    private PasswordMemoCryptoService passwordMemoCryptoService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordMemoService passwordMemoService;

    @Test
    void createShouldEncryptPasswordAndBindCurrentOwner() {
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordMemoCryptoService.encrypt("MemoPassword123"))
                .thenReturn(new PasswordMemoCryptoService.EncryptedPayload("ciphertext", "nonce"));
        doAnswer(invocation -> {
            PasswordMemo memo = invocation.getArgument(0);
            memo.setId(11L);
            return 1;
        }).when(passwordMemoMapper).insert(any(PasswordMemo.class));

        SavePasswordMemoRequest request = new SavePasswordMemoRequest();
        request.setSiteName("GitHub");
        request.setSiteUrl("https://github.com");
        request.setUsername("octocat");
        request.setPassword("MemoPassword123");

        PasswordMemoDetailVO result = passwordMemoService.create(1L, request);

        ArgumentCaptor<PasswordMemo> captor = ArgumentCaptor.forClass(PasswordMemo.class);
        verify(passwordMemoMapper).insert(captor.capture());
        PasswordMemo saved = captor.getValue();
        assertEquals(1L, saved.getOwnerUserId());
        assertEquals("ciphertext", saved.getPasswordCiphertext());
        assertEquals("nonce", saved.getPasswordNonce());
        assertEquals(11L, result.getId());
        assertEquals("********", result.getMaskedPassword());
    }

    @Test
    void verifyAccessShouldValidateCurrentLoginPassword() {
        User user = new User();
        user.setId(1L);
        user.setPasswordHash("encoded");
        when(userMapper.selectById(1L)).thenReturn(user);

        PasswordMemo memo = new PasswordMemo();
        memo.setId(12L);
        memo.setOwnerUserId(1L);
        memo.setPasswordCiphertext("ciphertext");
        memo.setPasswordNonce("nonce");
        when(passwordMemoMapper.selectOne(any())).thenReturn(memo);
        when(passwordEncoder.matches("current-login-password", "encoded")).thenReturn(true);
        when(passwordMemoCryptoService.decrypt("ciphertext", "nonce")).thenReturn("third-party-password");

        VerifyAccessRequest request = new VerifyAccessRequest();
        request.setLoginPassword("current-login-password");
        VerifyAccessResponse response = passwordMemoService.verifyAccess(1L, 12L, request, "127.0.0.1");

        assertEquals("third-party-password", response.password());
    }

    @Test
    void verifyAccessShouldRejectWrongLoginPassword() {
        User user = new User();
        user.setId(1L);
        user.setPasswordHash("encoded");
        when(userMapper.selectById(1L)).thenReturn(user);

        PasswordMemo memo = new PasswordMemo();
        memo.setId(12L);
        memo.setOwnerUserId(1L);
        when(passwordMemoMapper.selectOne(any())).thenReturn(memo);
        when(passwordEncoder.matches("wrong-password", "encoded")).thenReturn(false);

        VerifyAccessRequest request = new VerifyAccessRequest();
        request.setLoginPassword("wrong-password");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> passwordMemoService.verifyAccess(1L, 12L, request, "127.0.0.1")
        );
        assertEquals("LOGIN_PASSWORD_INVALID", exception.getCode());
    }

    @Test
    void updatePasswordShouldArchiveCurrentPasswordBeforeReplacingIt() {
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);

        PasswordMemo memo = new PasswordMemo();
        memo.setId(12L);
        memo.setOwnerUserId(1L);
        memo.setPasswordCiphertext("old-ciphertext");
        memo.setPasswordNonce("old-nonce");
        memo.setPasswordStartedAt(java.time.LocalDateTime.of(2026, 1, 1, 8, 0));
        when(passwordMemoMapper.selectOne(any())).thenReturn(memo);
        when(passwordMemoCryptoService.decrypt("old-ciphertext", "old-nonce")).thenReturn("old-password");
        when(passwordMemoCryptoService.encrypt("new-password"))
                .thenReturn(new PasswordMemoCryptoService.EncryptedPayload("new-ciphertext", "new-nonce"));

        UpdateMemoPasswordRequest request = new UpdateMemoPasswordRequest();
        request.setNewPassword("new-password");
        passwordMemoService.updatePassword(1L, 12L, request);

        ArgumentCaptor<PasswordMemoHistory> historyCaptor = ArgumentCaptor.forClass(PasswordMemoHistory.class);
        verify(passwordMemoHistoryMapper).insert(historyCaptor.capture());
        PasswordMemoHistory history = historyCaptor.getValue();
        assertEquals("old-ciphertext", history.getPasswordCiphertext());
        assertEquals(memo.getPasswordStartedAt(), history.getUsageEndedAt());
        assertEquals("new-ciphertext", memo.getPasswordCiphertext());
        verify(passwordMemoMapper).updateById(memo);
    }
}
