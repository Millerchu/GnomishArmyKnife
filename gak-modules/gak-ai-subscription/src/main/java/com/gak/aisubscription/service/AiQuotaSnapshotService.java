package com.gak.aisubscription.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.aisubscription.dto.SaveAiQuotaSnapshotRequest;
import com.gak.aisubscription.vo.AiQuotaSnapshotVO;
import com.gak.aisubscription.vo.AiLatestQuotaVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 额度窗口是观测值，独立保存。重置计数和百分比均不写入用量与费用。 */
@Service
public class AiQuotaSnapshotService {
    private static final int HISTORY_LIMIT = 50;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AiAccessService access;
    private final AiAccountService accounts;

    public AiQuotaSnapshotService(JdbcTemplate jdbc, ObjectMapper json, AiAccessService access, AiAccountService accounts) {
        this.jdbc = jdbc;
        this.json = json;
        this.access = access;
        this.accounts = accounts;
    }

    public List<AiQuotaSnapshotVO> list(Long owner, Long accountId) {
        accounts.requireOwned(owner, accountId);
        return jdbc.query("""
                SELECT id, record_json FROM gak_ai_quota_snapshot
                WHERE owner_user_id=? AND account_id=? ORDER BY observed_at DESC, id DESC LIMIT ?
                """, (row, index) -> view(row.getLong("id"), decode(row.getString("record_json"))),
                owner, accountId, HISTORY_LIMIT);
    }

    /** 活跃账号各取最新一条，利用账号和采集时间索引，避免读取完整历史。 */
    public List<AiLatestQuotaVO> latest(Long owner) {
        return jdbc.query("""
                SELECT account.id, account.alias, account.tool_code, latest.record_json
                FROM gak_ai_account account
                JOIN LATERAL (
                    SELECT snapshot.record_json FROM gak_ai_quota_snapshot snapshot
                    WHERE snapshot.owner_user_id = account.owner_user_id AND snapshot.account_id = account.id
                    ORDER BY snapshot.observed_at DESC LIMIT 1
                ) latest ON TRUE
                WHERE account.owner_user_id = ? AND NOT account.archived
                ORDER BY account.tool_code, account.alias, account.id
                """, (row, index) -> {
                    var snapshot = decode(row.getString("record_json"));
                    return new AiLatestQuotaVO(row.getLong("id"), row.getString("alias"), row.getString("tool_code"),
                            snapshot.observedAt(), snapshot.windows());
                }, owner);
    }

    @Transactional
    public AiQuotaSnapshotVO save(Long owner, SaveAiQuotaSnapshotRequest snapshot) {
        access.lockOwner(owner);
        accounts.requireActive(owner, snapshot.accountId());
        AiRules.check(!snapshot.observedAt().isAfter(LocalDateTime.now(AiSummaryService.DEFAULT_ZONE).plusMinutes(1)),
                "采集时间不能晚于当前时间");
        var names = new HashSet<String>();
        for (var window : snapshot.windows()) {
            AiRules.check(names.add(window.label().trim()), "同一快照内额度窗口名称不能重复");
            AiRules.check(window.percent() != null || window.remaining() != null || window.resetHint() != null
                    && !window.resetHint().isBlank(), "请填写额度数值或厂商显示的状态");
            AiRules.check(window.remaining() == null || window.unit() != null && !window.unit().isBlank(),
                    "剩余数量需要填写单位");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO gak_ai_quota_snapshot (id,owner_user_id,account_id,observed_at,record_json)
                VALUES (?,?,?,?,CAST(? AS jsonb)) ON CONFLICT (owner_user_id,account_id,observed_at)
                DO UPDATE SET record_json=EXCLUDED.record_json RETURNING id
                """, Long.class, IdWorker.getId(), owner, snapshot.accountId(), snapshot.observedAt(), encode(snapshot));
        return view(id, snapshot);
    }

    private AiQuotaSnapshotVO view(Long id, SaveAiQuotaSnapshotRequest snapshot) {
        return new AiQuotaSnapshotVO(id, snapshot.accountId(), snapshot.observedAt(), snapshot.windows(),
                snapshot.resets() == null ? List.of() : snapshot.resets());
    }

    private String encode(SaveAiQuotaSnapshotRequest snapshot) {
        try { return json.writeValueAsString(snapshot); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("无法保存额度快照", exception); }
    }

    private SaveAiQuotaSnapshotRequest decode(String record) {
        try { return json.readValue(record, SaveAiQuotaSnapshotRequest.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("无法读取额度快照", exception); }
    }
}
