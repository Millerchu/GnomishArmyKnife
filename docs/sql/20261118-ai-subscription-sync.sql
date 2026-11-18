-- 官方组织 API 同步。只在用户触发后执行任务，不后台主动创建每日任务。
CREATE TABLE IF NOT EXISTS gak_ai_sync_connection (
    account_id BIGINT PRIMARY KEY REFERENCES gak_ai_account(id),
    owner_user_id BIGINT NOT NULL,
    encrypted_key TEXT,
    zone_id VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 1,
    last_success_at TIMESTAMPTZ,
    CHECK (NOT enabled OR encrypted_key IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_ai_sync_connection_owner ON gak_ai_sync_connection(owner_user_id, enabled);
ALTER TABLE gak_ai_sync_connection ADD COLUMN IF NOT EXISTS provider_team_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_sync_provider_team ON gak_ai_sync_connection(owner_user_id, provider_team_id)
    WHERE provider_team_id IS NOT NULL;
CREATE TABLE IF NOT EXISTS gak_ai_sync_job (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL REFERENCES gak_ai_account(id),
    local_date DATE NOT NULL,
    trigger_type VARCHAR(16) NOT NULL CHECK (trigger_type IN ('DAILY','MANUAL')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED','RUNNING','RETRY','SUCCEEDED','FAILED','CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    lease_token VARCHAR(64),
    lease_until TIMESTAMPTZ,
    connection_version BIGINT NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_sync_daily ON gak_ai_sync_job(owner_user_id, account_id, local_date, trigger_type) WHERE trigger_type = 'DAILY';
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_sync_inflight ON gak_ai_sync_job(account_id) WHERE status IN ('QUEUED','RUNNING','RETRY');
CREATE INDEX IF NOT EXISTS idx_ai_sync_claim ON gak_ai_sync_job(status, next_attempt_at, lease_until);
CREATE INDEX IF NOT EXISTS idx_ai_sync_owner_jobs ON gak_ai_sync_job(owner_user_id, created_at DESC);
