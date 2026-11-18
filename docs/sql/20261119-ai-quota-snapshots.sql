-- 智订额度快照，先执行 20261118-ai-subscription-business.sql。
CREATE TABLE IF NOT EXISTS gak_ai_quota_snapshot (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES gak_user(id),
    account_id BIGINT NOT NULL REFERENCES gak_ai_account(id),
    observed_at TIMESTAMP NOT NULL,
    record_json JSONB NOT NULL,
    UNIQUE (owner_user_id, account_id, observed_at)
);
