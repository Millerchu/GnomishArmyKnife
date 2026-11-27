package com.gak.user.service.user;

import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TokenService 过期、滑动续期、容量淘汰、吊销及账号状态校验测试。
 */
class TokenServiceTest {

    @Test
    void shouldExpireAfterIdleTtlAndRespectAbsoluteTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-27T00:00:00Z"));
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectById(1L)).thenReturn(enabledUser(1L));

        TokenService tokenService = new TokenService(
                userMapper, clock, Duration.ofHours(5), Duration.ofHours(1), 10);
        String token = tokenService.issueToken(1L);

        clock.advance(Duration.ofMinutes(45));
        assertEquals(1L, tokenService.requireCurrentUserId(bearerRequest(token)));

        // 滑动续期后在新的空闲窗口内仍有效
        clock.advance(Duration.ofMinutes(45));
        assertEquals(1L, tokenService.requireCurrentUserId(bearerRequest(token)));

        // 超过空闲窗口应失效
        clock.advance(Duration.ofMinutes(61));
        assertUnauthorized(() -> tokenService.requireCurrentUserId(bearerRequest(token)));

        // 连续活跃也不可突破绝对过期上限
        String absoluteToken = tokenService.issueToken(1L);
        for (int i = 0; i < 6; i++) {
            clock.advance(Duration.ofMinutes(45));
            assertEquals(1L, tokenService.requireCurrentUserId(bearerRequest(absoluteToken)));
        }
        clock.advance(Duration.ofMinutes(31));
        assertUnauthorized(() -> tokenService.requireCurrentUserId(bearerRequest(absoluteToken)));
    }

    @Test
    void shouldRevokeSingleTokenAndAllUserTokens() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectById(1L)).thenReturn(enabledUser(1L));
        when(userMapper.selectById(2L)).thenReturn(enabledUser(2L));
        TokenService tokenService = new TokenService(
                userMapper, Clock.systemUTC(), Duration.ofHours(24), Duration.ofHours(2), 10);

        String token1 = tokenService.issueToken(1L);
        String token2 = tokenService.issueToken(1L);
        String otherToken = tokenService.issueToken(2L);

        tokenService.revokeToken(token1);
        assertUnauthorized(() -> tokenService.requireCurrentUserId(bearerRequest(token1)));
        assertEquals(1L, tokenService.requireCurrentUserId(bearerRequest(token2)));

        tokenService.revokeByUserId(1L);
        assertUnauthorized(() -> tokenService.requireCurrentUserId(bearerRequest(token2)));
        assertEquals(2L, tokenService.requireCurrentUserId(bearerRequest(otherToken)));
    }

    @Test
    void shouldRejectAndRevokeWhenUserDisabledOrMissing() {
        UserMapper userMapper = mock(UserMapper.class);
        User user = enabledUser(3L);
        when(userMapper.selectById(3L)).thenReturn(user);
        TokenService tokenService = new TokenService(
                userMapper, Clock.systemUTC(), Duration.ofHours(24), Duration.ofHours(2), 10);

        String token = tokenService.issueToken(3L);
        assertEquals(3L, tokenService.requireCurrentUserId(bearerRequest(token)));

        user.setEnabled(false);
        user.setStatus("DISABLED");
        assertUnauthorized(() -> tokenService.requireCurrentUserId(bearerRequest(token)));

        // 即使重新启用，原令牌也已被吊销
        user.setEnabled(true);
        user.setStatus("ENABLED");
        assertUnauthorized(() -> tokenService.requireCurrentUserId(bearerRequest(token)));
    }

    @Test
    void shouldEvictOldestAccessedTokenWhenExceedingCapacity() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-27T00:00:00Z"));
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectById(1L)).thenReturn(enabledUser(1L));
        when(userMapper.selectById(2L)).thenReturn(enabledUser(2L));
        when(userMapper.selectById(3L)).thenReturn(enabledUser(3L));

        TokenService tokenService = new TokenService(
                userMapper, clock, Duration.ofHours(24), Duration.ofHours(2), 2);
        String first = tokenService.issueToken(1L);
        clock.advance(Duration.ofSeconds(1));
        String second = tokenService.issueToken(2L);
        clock.advance(Duration.ofSeconds(1));
        assertEquals(1L, tokenService.requireCurrentUserId(bearerRequest(first)));
        clock.advance(Duration.ofSeconds(1));
        String third = tokenService.issueToken(3L);

        assertEquals(1L, tokenService.requireCurrentUserId(bearerRequest(first)));
        assertUnauthorized(() -> tokenService.requireCurrentUserId(bearerRequest(second)));
        assertEquals(3L, tokenService.requireCurrentUserId(bearerRequest(third)));
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private User enabledUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setEnabled(true);
        user.setStatus("ENABLED");
        user.setRoleCode("USER");
        return user;
    }

    private void assertUnauthorized(Runnable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant initial) {
            this.current = initial;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
