package com.gak.aisubscription.sync;

import java.time.Instant;
/** 仅服务端使用的授权实体，不能直接作为响应返回。 */
public record AiSyncConnection(Long accountId, Long ownerUserId, String encryptedKey,
        String zoneId, boolean enabled, long version, Instant lastSuccessAt, String teamId) {
    @Override public String toString() { return "AiSyncConnection[accountId=" + accountId + ", enabled=" + enabled + "]"; }
}
