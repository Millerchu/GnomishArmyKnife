package com.gak.aisubscription.sync;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 连接和任务的 SQL 边界；调用者在同一事务内锁定连接后变更任务。 */
@Repository
public class AiSyncRepository {
    private final JdbcTemplate jdbc;
    public AiSyncRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public AiSyncConnection connection(Long owner, Long account, boolean lock) {
        List<AiSyncConnection> connections = jdbc.query("SELECT * FROM gak_ai_sync_connection WHERE owner_user_id=? AND account_id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new AiSyncConnection(rs.getLong("account_id"), rs.getLong("owner_user_id"),
                        rs.getString("encrypted_key"), rs.getString("zone_id"), rs.getBoolean("enabled"),
                        rs.getLong("version"), instant(rs, "last_success_at"), rs.getString("provider_team_id")), owner, account);
        return connections.isEmpty() ? null : connections.get(0);
    }
    /** 权限失效和账号归档后清除密文；同一 SQL 取消在途任务，不触发外部请求。 */
    public void revokeInaccessible() {
        jdbc.update("""
            WITH revoked AS (
              UPDATE gak_ai_sync_connection c SET enabled=FALSE, encrypted_key=NULL, version=version+1
              WHERE c.enabled AND NOT EXISTS (
                SELECT 1 FROM gak_ai_account account JOIN gak_user u ON u.id=account.owner_user_id
                JOIN gak_system_app app ON app.app_code='APP_AI_SUBSCRIPTION'
                WHERE account.id=c.account_id AND account.owner_user_id=c.owner_user_id AND NOT account.archived
                  AND account.tool_code IN ('CHATGPT','GROK') AND u.enabled=TRUE AND u.status='ENABLED' AND app.enabled=TRUE
                  AND (u.role_code='ADMIN' OR EXISTS (SELECT 1 FROM gak_user_app_permission p
                    WHERE p.user_id=u.id AND p.app_code=app.app_code AND p.granted=TRUE)))
              RETURNING account_id
            ) UPDATE gak_ai_sync_job SET status='CANCELLED', lease_token=NULL, lease_until=NULL,
                finished_at=CURRENT_TIMESTAMP, error_code='ACCESS_REVOKED'
              WHERE account_id IN (SELECT account_id FROM revoked) AND status IN ('QUEUED','RUNNING','RETRY')
            """);
    }
    public List<Long> enabledAccounts(Long owner) {
        return jdbc.queryForList("SELECT c.account_id FROM gak_ai_sync_connection c JOIN gak_ai_account a ON a.id=c.account_id WHERE c.owner_user_id=? AND c.enabled AND NOT a.archived ORDER BY c.account_id", Long.class, owner);
    }
    public boolean teamAlreadyAssigned(Long owner, Long account, String teamId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM gak_ai_sync_connection
                WHERE owner_user_id=? AND account_id<>? AND provider_team_id=?)
            """, Boolean.class, owner, account, teamId));
    }
    public void saveConnection(Long owner, Long account, String encrypted, String zone, String teamId) {
        jdbc.update("""
            INSERT INTO gak_ai_sync_connection(account_id,owner_user_id,encrypted_key,zone_id,provider_team_id,enabled,version)
            VALUES(?,?,?,?,?,TRUE,1) ON CONFLICT(account_id) DO UPDATE SET encrypted_key=EXCLUDED.encrypted_key,
            zone_id=EXCLUDED.zone_id,provider_team_id=EXCLUDED.provider_team_id,enabled=TRUE,
            version=gak_ai_sync_connection.version+1
            """, account, owner, encrypted, zone, teamId);
        cancel(owner, account);
    }
    public void revoke(Long owner, Long account) {
        jdbc.update("UPDATE gak_ai_sync_connection SET enabled=FALSE,encrypted_key=NULL,version=version+1 WHERE owner_user_id=? AND account_id=?", owner, account);
        cancel(owner, account);
    }
    private void cancel(Long owner, Long account) {
        jdbc.update("UPDATE gak_ai_sync_job SET status='CANCELLED',lease_token=NULL,lease_until=NULL,finished_at=CURRENT_TIMESTAMP WHERE owner_user_id=? AND account_id=? AND status IN ('QUEUED','RUNNING','RETRY')", owner, account);
    }
    public AiSyncJob inFlight(Long owner, Long account) {
        return first("SELECT * FROM gak_ai_sync_job WHERE owner_user_id=? AND account_id=? AND status IN ('QUEUED','RUNNING','RETRY') ORDER BY created_at DESC LIMIT 1", owner, account);
    }
    public AiSyncJob daily(Long owner, Long account, LocalDate date) {
        return first("SELECT * FROM gak_ai_sync_job WHERE owner_user_id=? AND account_id=? AND local_date=? AND trigger_type='DAILY'", owner, account, date);
    }
    public AiSyncJob latestManual(Long owner, Long account) {
        return first("SELECT * FROM gak_ai_sync_job WHERE owner_user_id=? AND account_id=? AND trigger_type='MANUAL' ORDER BY created_at DESC LIMIT 1", owner, account);
    }
    public AiSyncJob find(Long owner, Long id) {
        return first("SELECT * FROM gak_ai_sync_job WHERE owner_user_id=? AND id=?", owner, id);
    }
    public void insert(Long id, AiSyncConnection connection, LocalDate date, String trigger) {
        jdbc.update("INSERT INTO gak_ai_sync_job(id,owner_user_id,account_id,local_date,trigger_type,status,connection_version) VALUES(?,?,?,?,?,'QUEUED',?)", id, connection.ownerUserId(), connection.accountId(), date, trigger, connection.version());
    }
    public List<AiSyncJob> candidates() {
        return jdbc.query("SELECT * FROM gak_ai_sync_job WHERE (status IN ('QUEUED','RETRY') AND next_attempt_at<=CURRENT_TIMESTAMP) OR (status='RUNNING' AND lease_until<CURRENT_TIMESTAMP) ORDER BY created_at LIMIT 10", (rs,row) -> job(rs));
    }
    public AiSyncJob claim(AiSyncJob job, String token) {
        jdbc.update("UPDATE gak_ai_sync_job SET status='FAILED',error_code='MAX_ATTEMPTS',finished_at=CURRENT_TIMESTAMP WHERE id=? AND attempts>=3 AND status='RUNNING' AND lease_until<CURRENT_TIMESTAMP", job.id());
        int changed = jdbc.update("UPDATE gak_ai_sync_job SET status='RUNNING',attempts=attempts+1,lease_token=?,lease_until=CURRENT_TIMESTAMP+INTERVAL '5 minutes',error_code=NULL WHERE id=? AND attempts<3 AND ((status IN ('QUEUED','RETRY') AND next_attempt_at<=CURRENT_TIMESTAMP) OR (status='RUNNING' AND lease_until<CURRENT_TIMESTAMP))", token, job.id());
        return changed == 1 ? find(job.ownerUserId(), job.id()) : null;
    }
    /** 完成条件含当前租约期限；即使其他 worker 尚未抢占，也拒绝过期 worker。 */
    public boolean complete(AiSyncJob job) {
        return jdbc.update("UPDATE gak_ai_sync_job SET status='SUCCEEDED',finished_at=CURRENT_TIMESTAMP,lease_until=NULL WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until>CURRENT_TIMESTAMP", job.id(), job.leaseToken()) == 1;
    }
    public void markSuccess(AiSyncJob job) {
        jdbc.update("UPDATE gak_ai_sync_connection SET last_success_at=CURRENT_TIMESTAMP WHERE account_id=? AND version=? AND enabled", job.accountId(), job.connectionVersion());
    }
    public boolean fail(AiSyncJob job, AiSyncFailure failure) {
        boolean retry = failure.retryable() && job.attempts()<3;
        long delay = Math.max(failure.retryAfterSeconds(), 30L * (1L << job.attempts()));
        return jdbc.update("UPDATE gak_ai_sync_job SET status=?,error_code=?,lease_until=NULL,next_attempt_at=CURRENT_TIMESTAMP+(? * INTERVAL '1 second'),finished_at=CASE WHEN ? THEN NULL ELSE CURRENT_TIMESTAMP END WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until>CURRENT_TIMESTAMP", retry ? "RETRY" : "FAILED", failure.getMessage(), delay, retry, job.id(), job.leaseToken()) == 1;
    }
    private AiSyncJob first(String sql, Object... arguments) {
        List<AiSyncJob> jobs = jdbc.query(sql, (rs,row) -> job(rs), arguments);
        return jobs.isEmpty() ? null : jobs.get(0);
    }
    private AiSyncJob job(ResultSet rs) throws SQLException {
        return new AiSyncJob(rs.getLong("id"), rs.getLong("owner_user_id"), rs.getLong("account_id"),
                rs.getObject("local_date", LocalDate.class), rs.getString("trigger_type"),
                AiSyncStatus.valueOf(rs.getString("status")), rs.getInt("attempts"), rs.getString("lease_token"),
                rs.getLong("connection_version"), rs.getString("error_code"), instant(rs,"created_at"), instant(rs,"finished_at"));
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
