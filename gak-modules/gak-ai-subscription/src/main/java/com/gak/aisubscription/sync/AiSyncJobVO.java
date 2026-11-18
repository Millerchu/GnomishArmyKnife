package com.gak.aisubscription.sync;

import java.time.Instant;
/** 客户端任务状态，不暴露租约或授权版本。 */
public record AiSyncJobVO(Long id, Long accountId, AiSyncStatus status, int attempts,
        String errorCode, Instant createdAt, Instant finishedAt) {
    public static AiSyncJobVO from(AiSyncJob job) {
        return new AiSyncJobVO(job.id(), job.accountId(), job.status(), job.attempts(),
                job.errorCode(), job.createdAt(), job.finishedAt());
    }
}
