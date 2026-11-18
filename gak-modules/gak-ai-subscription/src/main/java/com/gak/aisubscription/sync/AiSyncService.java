package com.gak.aisubscription.sync;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.gak.aisubscription.service.AiAccessService;
import com.gak.aisubscription.service.AiAccountService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 用户触发只创建持久化任务；网络请求由后台执行，响应不等待外部平台。 */
@Service
public class AiSyncService {
    private static final String CAPABILITY = "OpenAI 组织 API 每日费用；需 Admin API Key，不含 ChatGPT 套餐用量";
    private static final String GROK_CAPABILITY = "xAI 团队 API 每日费用；需团队 ID 和 Management API Key";
    private final AiAccessService access;
    private final AiAccountService accounts;
    private final AiSyncRepository repository;
    private final AiSyncCrypto crypto;
    public AiSyncService(AiAccessService access, AiAccountService accounts, AiSyncRepository repository,
                         AiSyncCrypto crypto) {
        this.access = access; this.accounts = accounts; this.repository = repository; this.crypto = crypto;
    }
    public AiSyncConnectionVO connection(Long owner, Long account) {
        String tool = accounts.requireOwned(owner, account).getToolCode();
        AiSyncConnection connection = repository.connection(owner, account, false);
        return new AiSyncConnectionVO(account, connection != null && connection.enabled(),
                connection == null ? "Asia/Shanghai" : connection.zoneId(),
                connection == null ? null : connection.lastSuccessAt(), crypto.configured(),
                "GROK".equals(tool) ? GROK_CAPABILITY : "CHATGPT".equals(tool) ? CAPABILITY : "手动记录",
                connection == null ? null : connection.teamId());
    }
    @Transactional
    public AiSyncConnectionVO save(Long owner, Long account, SaveAiSyncConnectionRequest request) {
        access.lockOwner(owner);
        String tool = accounts.requireActive(owner, account).getToolCode();
        if (!List.of("CHATGPT", "GROK").contains(tool)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该工具尚未核验官方同步能力");
        }
        if (!crypto.configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "服务端尚未配置同步加密主密钥");
        try { ZoneId.of(request.getZoneId()); }
        catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "时区无效"); }
        String key = request.getApiKey().trim();
        if (key.isBlank() || key.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "授权凭据格式无效");
        }
        String teamId = "GROK".equals(tool) ? request.getTeamId() : null;
        if ("GROK".equals(tool)) {
            if (teamId == null || !teamId.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的 xAI 团队 ID");
            }
            teamId = teamId.toLowerCase(java.util.Locale.ROOT);
            AiSyncConnection existing = repository.connection(owner, account, true);
            if (existing != null && existing.teamId() != null && !teamId.equals(existing.teamId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请为另一个 xAI 团队建立新账号，保留原费用归属");
            }
            if (repository.teamAlreadyAssigned(owner, account, teamId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该 xAI 团队已关联其他智订账号");
            }
        }
        repository.saveConnection(owner, account, crypto.encrypt(owner, account, key), request.getZoneId(), teamId);
        return connection(owner, account);
    }
    @Transactional
    public void revoke(Long owner, Long account) {
        access.lockOwner(owner);
        accounts.requireOwned(owner, account);
        repository.revoke(owner, account);
    }
    @Transactional
    public List<AiSyncJobVO> dailyCheck(Long owner) {
        access.lockOwner(owner);
        List<AiSyncJobVO> jobs = new ArrayList<>();
        for (Long account : repository.enabledAccounts(owner)) jobs.add(enqueue(owner, account, false));
        return jobs;
    }
    @Transactional
    public AiSyncJobVO manual(Long owner, Long account) {
        access.lockOwner(owner);
        return enqueue(owner, account, true);
    }
    public AiSyncJobVO job(Long owner, Long id) {
        AiSyncJob job = repository.find(owner, id);
        if (job == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "同步任务不可用");
        return AiSyncJobVO.from(job);
    }
    private AiSyncJobVO enqueue(Long owner, Long account, boolean manual) {
        accounts.requireActive(owner, account);
        AiSyncConnection connection = repository.connection(owner, account, true);
        if (connection == null || !connection.enabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先配置官方授权");
        }
        AiSyncJob active = repository.inFlight(owner, account);
        if (active != null) return AiSyncJobVO.from(active);
        LocalDate date = LocalDate.now(ZoneId.of(connection.zoneId()));
        if (!manual) {
            AiSyncJob daily = repository.daily(owner, account, date);
            if (daily != null) return AiSyncJobVO.from(daily);
        } else {
            AiSyncJob latest = repository.latestManual(owner, account);
            if (latest != null && Duration.between(latest.createdAt(), Instant.now()).toSeconds() < 60) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "手动刷新请间隔至少一分钟");
            }
        }
        Long id = IdWorker.getId();
        repository.insert(id, connection, date, manual ? "MANUAL" : "DAILY");
        return AiSyncJobVO.from(repository.find(owner, id));
    }
}
