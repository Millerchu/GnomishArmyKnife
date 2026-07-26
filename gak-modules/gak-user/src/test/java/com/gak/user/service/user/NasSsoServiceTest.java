package com.gak.user.service.user;

import com.gak.framework.exception.BusinessException;
import com.gak.user.config.NasSsoProperties;
import com.gak.user.vo.user.UserLoginVO;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NasSsoServiceTest {

    private static final String STATE = "a".repeat(43);
    private static final Duration SESSION_TTL = Duration.ofMinutes(15);

    @Mock
    private NasIdentityClient nasIdentityClient;

    @Mock
    private AuthService authService;

    private NasSsoProperties properties;
    private MutableClock clock;
    private NasSsoService nasSsoService;

    @BeforeEach
    void setUp() {
        properties = new NasSsoProperties();
        properties.setCodeTtl(Duration.ofSeconds(30));
        properties.setSessionTtl(SESSION_TTL);
        clock = new MutableClock(Instant.parse("2026-07-24T12:00:00Z"));
        nasSsoService = new NasSsoService(
                nasIdentityClient,
                authService,
                properties,
                new SecureRandom(),
                clock
        );
    }

    @Test
    void validNasIdentityShouldExchangeOnlyOnce() {
        NasIdentity identity = new NasIdentity("1000", "millerchu", "admin");
        UserLoginVO login = new UserLoginVO("gak-token", null);
        when(nasIdentityClient.verify("nas-token", "header")).thenReturn(identity);
        when(authService.loginFromNas("millerchu", SESSION_TTL)).thenReturn(login);

        String code = nasSsoService.createHandoff("nas-token", "header", STATE);

        assertEquals(login, nasSsoService.exchange(code, STATE));
        verify(authService).requireNasLoginUser("millerchu");
        verify(authService).loginFromNas("millerchu", SESSION_TTL);
        BusinessException replayException =
                assertThrows(BusinessException.class, () -> nasSsoService.exchange(code, STATE));
        assertEquals("NAS_SSO_CODE_INVALID", replayException.getCode());
    }

    @Test
    void stateMismatchShouldConsumeExchangeCode() {
        when(nasIdentityClient.verify("nas-token", "url"))
                .thenReturn(new NasIdentity("1000", "millerchu", "user"));
        String code = nasSsoService.createHandoff("nas-token", "url", STATE);
        String mismatchedState = "b".repeat(43);

        BusinessException stateException =
                assertThrows(BusinessException.class, () -> nasSsoService.exchange(code, mismatchedState));
        assertEquals("NAS_SSO_STATE_INVALID", stateException.getCode());
        assertThrows(BusinessException.class, () -> nasSsoService.exchange(code, STATE));
        verify(authService, never()).loginFromNas("millerchu", SESSION_TTL);
    }

    @Test
    void expiredExchangeCodeShouldFailClosed() {
        when(nasIdentityClient.verify("nas-token", "url"))
                .thenReturn(new NasIdentity("1000", "millerchu", "user"));
        String code = nasSsoService.createHandoff("nas-token", "url", STATE);
        clock.advance(Duration.ofSeconds(30));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> nasSsoService.exchange(code, STATE));

        assertEquals("NAS_SSO_CODE_INVALID", exception.getCode());
        verify(authService, never()).loginFromNas("millerchu", SESSION_TTL);
    }

    @Test
    void invalidTokenLocationShouldFailBeforeCallingNas() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> nasSsoService.createHandoff("nas-token", "cookie", STATE)
        );

        assertEquals("NAS_SSO_TOKEN_MODE_INVALID", exception.getCode());
        verify(nasIdentityClient, never()).verify("nas-token", "cookie");
    }

    @Test
    void rejectedNasTokenShouldNotReachGakUserLookup() {
        when(nasIdentityClient.verify("forged-token", "url"))
                .thenThrow(new BusinessException("NAS_SSO_TOKEN_INVALID", "NAS 登录状态无效"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> nasSsoService.createHandoff("forged-token", "url", STATE)
        );

        assertEquals("NAS_SSO_TOKEN_INVALID", exception.getCode());
        verifyNoInteractions(authService);
    }

    private static final class MutableClock extends Clock {

        private Instant currentTime;

        private MutableClock(Instant currentTime) {
            this.currentTime = currentTime;
        }

        private void advance(Duration duration) {
            currentTime = currentTime.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentTime;
        }
    }
}
