package com.gak.user.service.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenServiceTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-07-24T12:00:00Z");

    @Test
    void nasSsoTokenShouldExpireAtConfiguredTime() {
        MutableClock clock = new MutableClock(INITIAL_TIME);
        TokenService tokenService = new TokenService(clock);
        String token = tokenService.issueNasSsoToken(1000L, Duration.ofMinutes(15));
        MockHttpServletRequest request = bearerRequest(token);

        assertEquals(1000L, tokenService.requireCurrentUserId(request));

        clock.advance(Duration.ofMinutes(15));
        assertThrows(ResponseStatusException.class, () -> tokenService.requireCurrentUserId(request));
    }

    @Test
    void revokedTokenShouldNoLongerAuthenticate() {
        TokenService tokenService = new TokenService(Clock.fixed(INITIAL_TIME, ZoneId.of("UTC")));
        String token = tokenService.issueToken(1001L);
        MockHttpServletRequest request = bearerRequest(token);

        tokenService.revoke(request);

        assertThrows(ResponseStatusException.class, () -> tokenService.requireCurrentUserId(request));
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
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
