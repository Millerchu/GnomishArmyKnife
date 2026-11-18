-- 智订手动业务；先执行 framework 脚本，所有业务金额原币保存。
CREATE TABLE IF NOT EXISTS gak_ai_account (
 id BIGINT PRIMARY KEY, owner_user_id BIGINT NOT NULL REFERENCES gak_user(id),
 tool_code VARCHAR(32) NOT NULL, alias VARCHAR(100) NOT NULL, note VARCHAR(1000),
 password_memo_id BIGINT, archived BOOLEAN NOT NULL DEFAULT FALSE, recorded_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ai_account_owner ON gak_ai_account(owner_user_id, recorded_at DESC);
CREATE TABLE IF NOT EXISTS gak_ai_subscription (
 id BIGINT PRIMARY KEY, owner_user_id BIGINT NOT NULL REFERENCES gak_user(id), account_id BIGINT NOT NULL REFERENCES gak_ai_account(id),
 plan_name VARCHAR(100) NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL, next_billing_date DATE,
 cycle_months INTEGER NOT NULL CHECK(cycle_months BETWEEN 0 AND 120), amount NUMERIC(18,6) NOT NULL CHECK(amount>=0), currency VARCHAR(3) NOT NULL,
 auto_renew BOOLEAN NOT NULL, trial_end_date DATE, reminder_days INTEGER NOT NULL DEFAULT 3,
 terminated BOOLEAN NOT NULL DEFAULT FALSE, recorded_at TIMESTAMP NOT NULL, CHECK(end_date>start_date)
);
CREATE INDEX IF NOT EXISTS idx_ai_subscription_owner ON gak_ai_subscription(owner_user_id,next_billing_date,id);
CREATE TABLE IF NOT EXISTS gak_ai_usage_record (
 id BIGINT PRIMARY KEY, owner_user_id BIGINT NOT NULL REFERENCES gak_user(id), account_id BIGINT NOT NULL REFERENCES gak_ai_account(id),
 kind VARCHAR(16) NOT NULL CHECK(kind IN ('PACKAGE','API')), mode VARCHAR(16) NOT NULL CHECK(mode IN ('INCREMENT','CUMULATIVE')),
 period_key VARCHAR(200) NOT NULL, zone_id VARCHAR(64) NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL,
 metric VARCHAR(64) NOT NULL, unit VARCHAR(32) NOT NULL, dimension VARCHAR(128) NOT NULL DEFAULT '',
 quantity NUMERIC(24,6) NOT NULL CHECK(quantity>=0), cost NUMERIC(18,6) CHECK(cost>=0), currency VARCHAR(3) NOT NULL,
 resolution VARCHAR(16) NOT NULL DEFAULT 'NORMAL', source VARCHAR(16) NOT NULL, external_key VARCHAR(200), status VARCHAR(20) NOT NULL, reason VARCHAR(255), recorded_at TIMESTAMP NOT NULL,
 CHECK(end_date>start_date)
);
CREATE INDEX IF NOT EXISTS idx_ai_usage_owner_period ON gak_ai_usage_record(owner_user_id,start_date,end_date,recorded_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ai_usage_official_key ON gak_ai_usage_record(owner_user_id,account_id,source,external_key) WHERE external_key IS NOT NULL;
CREATE TABLE IF NOT EXISTS gak_ai_payment (
 id BIGINT PRIMARY KEY, owner_user_id BIGINT NOT NULL REFERENCES gak_user(id), account_id BIGINT NOT NULL REFERENCES gak_ai_account(id),
 subscription_id BIGINT REFERENCES gak_ai_subscription(id), type VARCHAR(20) NOT NULL CHECK(type IN ('SUBSCRIPTION','TOPUP','SETTLEMENT','REFUND')),
 amount NUMERIC(18,6) NOT NULL CHECK(amount>0), currency VARCHAR(3) NOT NULL, payment_date DATE NOT NULL,
 coverage_start DATE, coverage_end DATE, original_payment_id BIGINT REFERENCES gak_ai_payment(id),
 request_key VARCHAR(64) NOT NULL, note VARCHAR(1000), recorded_at TIMESTAMP NOT NULL,
 CHECK((coverage_start IS NULL AND coverage_end IS NULL) OR (coverage_start IS NOT NULL AND coverage_end>coverage_start)),
 UNIQUE(owner_user_id,request_key)
);
CREATE INDEX IF NOT EXISTS idx_ai_payment_owner_date ON gak_ai_payment(owner_user_id,payment_date DESC,id);
CREATE INDEX IF NOT EXISTS idx_ai_payment_refund ON gak_ai_payment(original_payment_id) WHERE original_payment_id IS NOT NULL;
