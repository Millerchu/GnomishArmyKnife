package com.gak.user.service.user;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 简单 token 服务。
 */
@Service
public class TokenService {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final Map<String, AuthSession> tokenStore = new ConcurrentHashMap<>();
    private final Clock clock;

    public TokenService() {
        this(Clock.systemUTC());
    }

    TokenService(Clock clock) {
        this.clock = clock;
    }

    public String issueToken(Long userId) {
        return issueToken(userId, null, SessionType.PASSWORD);
    }

    public String issueNasSsoToken(Long userId, Duration ttl) {
        return issueToken(userId, ttl, SessionType.NAS_SSO);
    }

    private String issueToken(Long userId, Duration ttl, SessionType sessionType) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = ttl == null ? null : clock.instant().plus(ttl);
        tokenStore.put(token, new AuthSession(userId, sessionType, expiresAt));
        return token;
    }

    public Long requireCurrentUserId(HttpServletRequest request) {
        String token = extractToken(request);
        AuthSession session = tokenStore.get(token);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        if (session.isExpired(clock.instant())) {
            tokenStore.remove(token, session);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        return session.userId();
    }

    public void revoke(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return;
        }
        tokenStore.remove(authorization.substring(BEARER_PREFIX.length()).trim());
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        return token;
    }

    private enum SessionType {
        PASSWORD,
        NAS_SSO
    }

    private record AuthSession(Long userId, SessionType sessionType, Instant expiresAt) {

        private boolean isExpired(Instant now) {
            return expiresAt != null && !now.isBefore(expiresAt);
        }
    }
}
