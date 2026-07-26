package com.gak.user.service.user;

import com.gak.framework.exception.BusinessException;
import com.gak.user.config.NasSsoProperties;
import com.gak.user.vo.user.UserLoginVO;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * NAS 单点登录编排服务。
 */
@Service
public class NasSsoService {

    private static final Pattern STATE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{32,128}$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{32,128}$");
    private static final String TOKEN_WHERE_URL = "url";
    private static final String TOKEN_WHERE_HEADER = "header";
    private static final int EXCHANGE_CODE_BYTES = 32;
    private static final int NAS_TOKEN_MAX_LENGTH = 4096;

    private final NasIdentityClient nasIdentityClient;
    private final AuthService authService;
    private final NasSsoProperties properties;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final Map<String, HandoffRecord> handoffStore = new ConcurrentHashMap<>();

    @Autowired
    public NasSsoService(NasIdentityClient nasIdentityClient,
                         AuthService authService,
                         NasSsoProperties properties) {
        this(nasIdentityClient, authService, properties, new SecureRandom(), Clock.systemUTC());
    }

    NasSsoService(NasIdentityClient nasIdentityClient,
                  AuthService authService,
                  NasSsoProperties properties,
                  SecureRandom secureRandom,
                  Clock clock) {
        this.nasIdentityClient = nasIdentityClient;
        this.authService = authService;
        this.properties = properties;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    public String createHandoff(String nasToken, String tokenWhere, String state) {
        validateState(state);
        validateNasToken(nasToken);
        String normalizedTokenWhere = normalizeTokenWhere(tokenWhere);
        NasIdentity identity = nasIdentityClient.verify(nasToken, normalizedTokenWhere);
        authService.requireNasLoginUser(identity.username());

        cleanupExpiredHandoffs();
        String code = issueExchangeCode();
        Instant expiresAt = clock.instant().plus(properties.getCodeTtl());
        handoffStore.put(code, new HandoffRecord(identity.username(), state, expiresAt));
        return code;
    }

    public UserLoginVO exchange(String code, String state) {
        validateCode(code);
        validateState(state);
        HandoffRecord handoff = handoffStore.remove(code);
        if (handoff == null || !clock.instant().isBefore(handoff.expiresAt())) {
            throw new BusinessException("NAS_SSO_CODE_INVALID", "单点登录凭证无效或已过期");
        }
        if (!handoff.state().equals(state)) {
            throw new BusinessException("NAS_SSO_STATE_INVALID", "单点登录状态校验失败");
        }
        return authService.loginFromNas(handoff.username(), properties.getSessionTtl());
    }

    private String normalizeTokenWhere(String tokenWhere) {
        String normalized = StringUtils.hasText(tokenWhere)
                ? tokenWhere.trim().toLowerCase()
                : TOKEN_WHERE_URL;
        if (!TOKEN_WHERE_URL.equals(normalized) && !TOKEN_WHERE_HEADER.equals(normalized)) {
            throw new BusinessException("NAS_SSO_TOKEN_MODE_INVALID", "NAS token 传输方式无效");
        }
        return normalized;
    }

    private void validateNasToken(String nasToken) {
        if (!StringUtils.hasText(nasToken) || nasToken.length() > NAS_TOKEN_MAX_LENGTH) {
            throw new BusinessException("NAS_SSO_TOKEN_INVALID", "NAS 登录状态无效");
        }
    }

    private void validateState(String state) {
        if (!StringUtils.hasText(state) || !STATE_PATTERN.matcher(state).matches()) {
            throw new BusinessException("NAS_SSO_STATE_INVALID", "单点登录状态校验失败");
        }
    }

    private void validateCode(String code) {
        if (!StringUtils.hasText(code) || !CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException("NAS_SSO_CODE_INVALID", "单点登录凭证无效或已过期");
        }
    }

    private String issueExchangeCode() {
        byte[] bytes = new byte[EXCHANGE_CODE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void cleanupExpiredHandoffs() {
        Instant now = clock.instant();
        handoffStore.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private record HandoffRecord(String username, String state, Instant expiresAt) {
    }
}
