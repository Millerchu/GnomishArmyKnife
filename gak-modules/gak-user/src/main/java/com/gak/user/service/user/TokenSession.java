package com.gak.user.service.user;

import java.time.Duration;
import java.time.Instant;

/**
 * 内存会话凭证元数据，仅用于 {@link TokenService} 内部生命周期管理。
 */
final class TokenSession {

    private final Long userId;
    private final boolean passwordChangeOnly;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private volatile Instant lastAccessedAt;

    TokenSession(Long userId, Instant issuedAt, Instant expiresAt, Instant lastAccessedAt, boolean passwordChangeOnly) {
        this.userId = userId;
        this.passwordChangeOnly = passwordChangeOnly;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.lastAccessedAt = lastAccessedAt;
    }

    Long userId() {
        return userId;
    }

    boolean passwordChangeOnly() {
        return passwordChangeOnly;
    }

    Instant issuedAt() {
        return issuedAt;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    Instant lastAccessedAt() {
        return lastAccessedAt;
    }

    boolean isExpired(Instant now, Duration idleTtl) {
        return now.isAfter(expiresAt) || now.isAfter(lastAccessedAt.plus(idleTtl));
    }

    void touch(Instant now) {
        if (now.isAfter(lastAccessedAt)) {
            lastAccessedAt = now;
        }
    }
}
