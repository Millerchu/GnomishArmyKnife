-- 2026-10-04 个人行程记录与账单来源。可在已有 PostgreSQL 数据库重复执行。
CREATE TABLE IF NOT EXISTS gak_travel_group (
 id BIGINT PRIMARY KEY, owner_user_id BIGINT NOT NULL, name VARCHAR(96) NOT NULL,
 note VARCHAR(1000), created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS gak_travel_record (
 id BIGINT PRIMARY KEY, owner_user_id BIGINT NOT NULL, group_id BIGINT, previous_record_id BIGINT,
 transport_type VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL,
 departure VARCHAR(96) NOT NULL, arrival VARCHAR(96) NOT NULL, departure_date DATE NOT NULL,
 departure_time TIME, arrival_date DATE, arrival_time TIME,
 departure_zone VARCHAR(96) NOT NULL DEFAULT 'Asia/Shanghai', arrival_zone VARCHAR(96) NOT NULL DEFAULT 'Asia/Shanghai',
 service_number VARCHAR(96), carrier VARCHAR(96), seat_class VARCHAR(96), carriage VARCHAR(96), seat VARCHAR(96),
 purpose VARCHAR(20), platform VARCHAR(96), order_number VARCHAR(96), note VARCHAR(1000),
 paid_amount NUMERIC(12,2), refund_amount NUMERIC(12,2), currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
 payment_date DATE, refund_date DATE, use_departure_date BOOLEAN NOT NULL DEFAULT FALSE,
 sync_bill BOOLEAN NOT NULL DEFAULT TRUE, billing_detached BOOLEAN NOT NULL DEFAULT FALSE,
 detached_payment_bill_id BIGINT, detached_refund_bill_id BIGINT,
 request_key VARCHAR(64) NOT NULL, version INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 CONSTRAINT ck_travel_amount CHECK ((paid_amount IS NULL OR paid_amount >= 0) AND
  (refund_amount IS NULL OR (refund_amount >= 0 AND (refund_amount = 0 OR (paid_amount IS NOT NULL AND refund_amount <= paid_amount))))),
 CONSTRAINT ck_travel_type CHECK (transport_type IN ('TRAIN','FLIGHT','BUS','SHIP','OTHER')),
 CONSTRAINT ck_travel_status CHECK (status IN ('PLANNED','COMPLETED','CANCELLED','CHANGED'))
);
CREATE INDEX IF NOT EXISTS idx_travel_owner_date ON gak_travel_record(owner_user_id, departure_date DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_travel_owner_group ON gak_travel_record(owner_user_id, group_id, departure_date);
CREATE INDEX IF NOT EXISTS idx_travel_previous ON gak_travel_record(previous_record_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_travel_owner_request ON gak_travel_record(owner_user_id, request_key);
CREATE INDEX IF NOT EXISTS idx_travel_group_owner ON gak_travel_group(owner_user_id, created_at DESC);
ALTER TABLE gak_personal_bill ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'CNY';
ALTER TABLE gak_personal_bill ADD COLUMN IF NOT EXISTS source_type VARCHAR(32);
ALTER TABLE gak_personal_bill ADD COLUMN IF NOT EXISTS source_id BIGINT;
ALTER TABLE gak_personal_bill ADD COLUMN IF NOT EXISTS source_entry VARCHAR(16);
CREATE UNIQUE INDEX IF NOT EXISTS uk_bill_source_entry ON gak_personal_bill(owner_user_id, source_type, source_id, source_entry);
CREATE INDEX IF NOT EXISTS idx_bill_owner_currency_date ON gak_personal_bill(owner_user_id, currency, bill_date DESC);
INSERT INTO gak_system_app(id,app_code,app_name,route_path,category,data_source_mode,icon_type,icon_text,
 security_level,encryption_mode,enabled,sort_no,description,created_at,updated_at)
VALUES (2020,'APP_TRAVEL_RECORD','行笺','/travel-record','生活管理','REAL','TEXT','行程',
 'CONFIDENTIAL','NONE',TRUE,130,'长期保存出行记录与票据，自动联动个人账单。',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON CONFLICT (app_code) DO NOTHING;

-- 行笺品牌升级：仅更新应用展示信息，可重复执行。
UPDATE gak_system_app
SET app_name = '行笺',
    icon_type = 'URL',
    icon_text = '行笺',
    icon_url = '/app-icons/app-travel-record.webp',
    icon_storage_type = 'PUBLIC_ASSET',
    icon_file_name = 'app-travel-record.webp',
    updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'APP_TRAVEL_RECORD';
