package com.gak.aisubscription.sync;

import java.time.Instant;
/** 不含任何可用凭据的同步配置。 */
public record AiSyncConnectionVO(Long accountId, boolean enabled, String zoneId,
        Instant lastSuccessAt, boolean serverConfigured, String capability, String teamId) { }
