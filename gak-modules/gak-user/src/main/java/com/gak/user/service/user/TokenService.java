package com.gak.user.service.user;

import com.gak.user.constant.UserSecurityConstants;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 进程内令牌服务，支持绝对/滑动过期、容量上限淘汰、按令牌/按用户吊销及账号启用态校验。
 */
@Service
public class TokenService {

    private static final String AUTHENTICATED_USER = TokenService.class.getName() + ".userId";
    private static final Duration PASSWORD_CHANGE_TTL = Duration.ofMinutes(10);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Duration DEFAULT_ABSOLUTE_TTL = Duration.ofHours(24);
    private static final Duration DEFAULT_IDLE_TTL = Duration.ofHours(2);
    private static final int DEFAULT_MAX_TOKENS = 10_000;

    private final Map<String, TokenSession> tokenStore = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> userTokens = new ConcurrentHashMap<>();
    private final Object sessionIndexLock = new Object();
    private final UserMapper userMapper;
    private final Clock clock;
    private final Duration absoluteTtl;
    private final Duration idleTtl;
    private final int maxTokens;

    /**
     * 供不依赖持久层的独立单元测试构造轻量级令牌服务。
     */
    public TokenService() {
        this(null, Clock.systemUTC(), DEFAULT_ABSOLUTE_TTL, DEFAULT_IDLE_TTL, DEFAULT_MAX_TOKENS);
    }

    @Autowired
    public TokenService(UserMapper userMapper,
                        @Value("${gak.auth.token.absolute-ttl:24h}") String absoluteTtl,
                        @Value("${gak.auth.token.idle-ttl:2h}") String idleTtl,
                        @Value("${gak.auth.token.max-size:10000}") int maxTokens) {
        this(userMapper, DurationStyle.detectAndParse(absoluteTtl), DurationStyle.detectAndParse(idleTtl), maxTokens);
    }

    public TokenService(UserMapper userMapper, Duration absoluteTtl, Duration idleTtl, int maxTokens) {
        this(userMapper, Clock.systemUTC(), absoluteTtl, idleTtl, maxTokens);
    }

    TokenService(UserMapper userMapper,
                 Clock clock,
                 Duration absoluteTtl,
                 Duration idleTtl,
                 int maxTokens) {
        if (absoluteTtl == null || absoluteTtl.isZero() || absoluteTtl.isNegative()) {
            throw new IllegalArgumentException("absoluteTtl 必须大于 0");
        }
        if (idleTtl == null || idleTtl.isZero() || idleTtl.isNegative()) {
            throw new IllegalArgumentException("idleTtl 必须大于 0");
        }
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens 必须大于等于 1");
        }
        this.userMapper = userMapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.absoluteTtl = absoluteTtl;
        this.idleTtl = idleTtl;
        this.maxTokens = maxTokens;
    }

    public String issueToken(Long userId) {
        return issueToken(userId, false);
    }

    public String issueToken(Long userId, boolean passwordChangeOnly) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        Instant now = clock.instant();
        String token = UUID.randomUUID().toString();
        Duration ttl = passwordChangeOnly && PASSWORD_CHANGE_TTL.compareTo(absoluteTtl) < 0
                ? PASSWORD_CHANGE_TTL : absoluteTtl;
        TokenSession session = new TokenSession(userId, now, now.plus(ttl), now, passwordChangeOnly);
        synchronized (sessionIndexLock) {
            evictIfNeededLocked(now);
            tokenStore.put(token, session);
            userTokens.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(token);
        }
        return token;
    }

    public Long requireCurrentUserId(HttpServletRequest request) {
        if (request != null && request.getAttribute(AUTHENTICATED_USER) instanceof Long userId) {
            return userId;
        }
        return requireSession(request, false);
    }

    public Long authenticateRequest(HttpServletRequest request) {
        Long userId = requireSession(request, false);
        request.setAttribute(AUTHENTICATED_USER, userId);
        return userId;
    }

    public Long requirePasswordChangeUserId(HttpServletRequest request) {
        return requireSession(request, true);
    }

    private Long requireSession(HttpServletRequest request, boolean passwordChangeAllowed) {
        String token = extractBearerToken(request);
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        TokenSession session = tokenStore.get(token);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        Instant now = clock.instant();
        if (session.isExpired(now, idleTtl)) {
            removeSession(token, session);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        boolean changeRequired = session.passwordChangeOnly();
        if (userMapper != null) {
            User user = userMapper.selectById(session.userId());
            if (!isAccountEnabled(user)) {
                revokeByUserId(session.userId());
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户已失效，请重新登录");
            }
            changeRequired |= Boolean.TRUE.equals(user.getForceChangePassword());
        }
        if (changeRequired && !passwordChangeAllowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先修改密码");
        }
        if (tokenStore.get(token) != session) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        session.touch(now);
        return session.userId();
    }

    public void revokeRequestToken(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (StringUtils.hasText(token)) {
            revokeToken(token);
        }
    }

    public void revokeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        String normalized = token.trim();
        synchronized (sessionIndexLock) {
            TokenSession removed = tokenStore.remove(normalized);
            if (removed != null) {
                detachUserTokenLocked(removed.userId(), normalized);
            }
        }
    }

    public void revokeByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        synchronized (sessionIndexLock) {
            Set<String> tokens = userTokens.remove(userId);
            if (tokens != null) {
                for (String token : tokens) {
                    tokenStore.remove(token);
                }
            }
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    private boolean isAccountEnabled(User user) {
        if (user == null) {
            return false;
        }
        return Boolean.TRUE.equals(user.getEnabled())
                && StringUtils.hasText(user.getStatus())
                && UserSecurityConstants.ENABLED_STATUS.equalsIgnoreCase(user.getStatus());
    }

    private void removeSession(String token, TokenSession expected) {
        synchronized (sessionIndexLock) {
            if (tokenStore.remove(token, expected)) {
                detachUserTokenLocked(expected.userId(), token);
            }
        }
    }

    private void detachUserTokenLocked(Long userId, String token) {
        Set<String> tokens = userTokens.get(userId);
        if (tokens != null) {
            tokens.remove(token);
            if (tokens.isEmpty()) {
                userTokens.remove(userId, tokens);
            }
        }
    }

    private void evictIfNeededLocked(Instant now) {
        if (tokenStore.size() < maxTokens) {
            return;
        }
        List<Map.Entry<String, TokenSession>> expired = new ArrayList<>();
        for (Map.Entry<String, TokenSession> entry : tokenStore.entrySet()) {
            if (entry.getValue().isExpired(now, idleTtl)) {
                expired.add(entry);
            }
        }
        for (Map.Entry<String, TokenSession> entry : expired) {
            tokenStore.remove(entry.getKey());
            detachUserTokenLocked(entry.getValue().userId(), entry.getKey());
        }
        if (tokenStore.size() < maxTokens) {
            return;
        }
        int overflow = tokenStore.size() - maxTokens + 1;
        List<Map.Entry<String, TokenSession>> candidates = new ArrayList<>(tokenStore.entrySet());
        candidates.sort(Comparator.comparing(entry -> entry.getValue().lastAccessedAt()));
        for (int i = 0; i < overflow && i < candidates.size(); i++) {
            Map.Entry<String, TokenSession> victim = candidates.get(i);
            tokenStore.remove(victim.getKey());
            detachUserTokenLocked(victim.getValue().userId(), victim.getKey());
        }
    }
}
