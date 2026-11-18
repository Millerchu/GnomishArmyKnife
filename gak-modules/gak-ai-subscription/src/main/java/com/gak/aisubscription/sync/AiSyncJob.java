package com.gak.aisubscription.sync;

import java.time.Instant;
import java.time.LocalDate;
/** 每次执行通过 leaseToken 隔离，过期执行不能写回。 */
public record AiSyncJob(Long id, Long ownerUserId, Long accountId, LocalDate localDate,
        String triggerType, AiSyncStatus status, int attempts, String leaseToken,
        long connectionVersion, String errorCode, Instant createdAt, Instant finishedAt) { }
