package com.gak.aisubscription.sync;

import com.gak.aisubscription.service.AiAccessService;
import com.gak.aisubscription.service.AiAccountService;
import com.gak.aisubscription.service.AiUsageService;
import com.gak.framework.message.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 仅恢复用户已创建任务；重启后从数据库恢复，并通过版本及租约令牌隔离旧执行。 */
@Component
@EnableScheduling
public class AiSyncWorker {
    private final AiSyncRepository repository;
    private final AiSyncCrypto crypto;
    private final OpenAiCostsClient client;
    private final GrokCostsClient grok;
    private final AiAccessService access;
    private final AiAccountService accounts;
    private final AiUsageService usage;
    private final TransactionTemplate transactions;
    private final MessagePublisher messages;
    public AiSyncWorker(AiSyncRepository repository, AiSyncCrypto crypto, OpenAiCostsClient client, GrokCostsClient grok,
                        AiAccessService access, AiAccountService accounts, AiUsageService usage,
                        PlatformTransactionManager transactionManager, MessagePublisher messages) {
        this.repository = repository; this.crypto = crypto; this.client = client; this.grok = grok;
        this.access = access; this.accounts = accounts; this.usage = usage;
        this.transactions = new TransactionTemplate(transactionManager); this.messages = messages;
    }
    @Scheduled(fixedDelayString = "${gak.ai-sync.worker-delay-ms:10000}", initialDelay = 15000)
    public void poll() {
        transactions.executeWithoutResult(status -> repository.revokeInaccessible());
        for (AiSyncJob candidate : repository.candidates()) run(candidate);
    }
    void run(AiSyncJob candidate) {
        AiSyncJob job;
        try { job = transactions.execute(status -> claim(candidate)); }
        catch (RuntimeException failure) { return; }
        if (job == null) return;
        try {
            AiSyncConnection connection = repository.connection(job.ownerUserId(), job.accountId(), false);
            if (!valid(connection, job)) return;
            String key = crypto.decrypt(job.ownerUserId(), job.accountId(), connection.encryptedKey());
            // 按 UTC 日桶回查 35 个完整日，纳入延迟修正。
            LocalDate end = LocalDate.now(ZoneOffset.UTC);
            LocalDate start = end.minusDays(35);
            List<AiOfficialCost> costs = connection.teamId() == null
                    ? client.fetch(key, start, end) : grok.fetch(key, connection.teamId(), start, end);
            transactions.executeWithoutResult(status -> complete(job, costs, start, end));
        } catch (AiSyncFailure failure) {
            transactions.executeWithoutResult(status -> fail(job, failure));
        } catch (RuntimeException ignored) {
            transactions.executeWithoutResult(status -> fail(job, new AiSyncFailure("SYNC_INTERNAL_ERROR", true, 60)));
        }
    }
    private AiSyncJob claim(AiSyncJob candidate) {
        access.lockOwner(candidate.ownerUserId());
        AiSyncConnection connection = repository.connection(candidate.ownerUserId(), candidate.accountId(), true);
        if (!valid(connection, candidate)) return null;
        accounts.requireActive(candidate.ownerUserId(), candidate.accountId());
        return repository.claim(candidate, UUID.randomUUID().toString());
    }
    /** 任务完成、快照及最后成功时间共享事务；任何持久化失败均回滚任务成功状态。 */
    private void complete(AiSyncJob job, List<AiOfficialCost> costs, LocalDate start, LocalDate end) {
        access.lockOwner(job.ownerUserId());
        AiSyncConnection connection = repository.connection(job.ownerUserId(), job.accountId(), true);
        if (!valid(connection, job)) return;
        accounts.requireActive(job.ownerUserId(), job.accountId());
        if (!repository.complete(job)) return;
        String prefix = connection.teamId() == null ? "OPENAI_COST:" : "XAI_COST:";
        usage.replaceOfficialCostWindow(job.ownerUserId(), job.accountId(), start, end, prefix, costs);
        repository.markSuccess(job);
    }
    private void fail(AiSyncJob job, AiSyncFailure failure) {
        AiSyncConnection connection = repository.connection(job.ownerUserId(), job.accountId(), true);
        if (!valid(connection, job)) return;
        if (!repository.fail(job, failure)) return;
        if ("AUTH_REQUIRED".equals(failure.getMessage()) || "KEY_REAUTH_REQUIRED".equals(failure.getMessage())) {
            repository.revoke(job.ownerUserId(), job.accountId());
            messages.publish(new PublishMessageCommand("AI_SUBSCRIPTION", "ai-sync-auth:" + job.accountId() + ":" + job.connectionVersion(),
                    null, MessageAudience.USERS, List.of(job.ownerUserId()), MessageCategory.SECURITY, MessagePriority.IMPORTANT,
                    "智订官方同步授权失效", "组织或团队 API 授权已停止使用，请在智订中重新配置授权。历史费用记录仍然保留。", MessageTarget.HOME));
        }
    }
    private boolean valid(AiSyncConnection connection, AiSyncJob job) {
        return connection != null && connection.enabled() && connection.version() == job.connectionVersion();
    }
}
