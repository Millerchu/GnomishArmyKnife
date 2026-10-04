-- 2026-10-05 芽记：成长记录应用，可重复执行，不包含示例业务数据。
CREATE TABLE IF NOT EXISTS gak_growth_child (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES gak_user(id),
    name VARCHAR(64) NOT NULL CHECK (length(trim(name)) > 0),
    birthday DATE NOT NULL,
    gender VARCHAR(16) NOT NULL CHECK (gender IN ('UNKNOWN','BOY','GIRL')),
    note VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (id, owner_user_id)
);
CREATE INDEX IF NOT EXISTS idx_growth_child_owner ON gak_growth_child(owner_user_id, created_at, id);
CREATE TABLE IF NOT EXISTS gak_growth_measurement (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    child_id BIGINT NOT NULL,
    measure_date DATE NOT NULL,
    height_cm NUMERIC(6,2) CHECK (height_cm > 0),
    weight_kg NUMERIC(6,2) CHECK (weight_kg > 0),
    head_cm NUMERIC(6,2) CHECK (head_cm > 0),
    recorded_by VARCHAR(64),
    note VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_growth_measurement_filled CHECK (height_cm IS NOT NULL OR weight_kg IS NOT NULL OR head_cm IS NOT NULL),
    FOREIGN KEY (child_id, owner_user_id) REFERENCES gak_growth_child(id, owner_user_id)
);
CREATE INDEX IF NOT EXISTS idx_growth_measurement_owner_child_date
    ON gak_growth_measurement(owner_user_id, child_id, measure_date DESC, created_at DESC, id DESC);
CREATE TABLE IF NOT EXISTS gak_growth_vaccination (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    child_id BIGINT NOT NULL,
    vaccine_name VARCHAR(128) NOT NULL CHECK (length(trim(vaccine_name)) > 0),
    dose INTEGER NOT NULL CHECK (dose BETWEEN 1 AND 99),
    vaccination_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PLANNED','COMPLETED')),
    institution VARCHAR(128),
    note VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (child_id, owner_user_id) REFERENCES gak_growth_child(id, owner_user_id)
);
CREATE INDEX IF NOT EXISTS idx_growth_vaccination_owner_child_date
    ON gak_growth_vaccination(owner_user_id, child_id, vaccination_date DESC, created_at DESC, id DESC);
CREATE TABLE IF NOT EXISTS gak_growth_milestone (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    child_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL CHECK (length(trim(title)) > 0),
    event_date DATE NOT NULL,
    note VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (child_id, owner_user_id) REFERENCES gak_growth_child(id, owner_user_id)
);
CREATE INDEX IF NOT EXISTS idx_growth_milestone_owner_child_date
    ON gak_growth_milestone(owner_user_id, child_id, event_date DESC, created_at DESC, id DESC);
INSERT INTO gak_system_app(id,app_code,app_name,route_path,category,data_source_mode,
    icon_type,icon_text,icon_url,icon_storage_type,icon_file_name,security_level,
    encryption_mode,enabled,sort_no,description,created_at,updated_at)
VALUES (2021,'APP_GROWTH_RECORD','芽记','/growth-record','个人生活','REAL',
    'URL','芽记','/app-icons/app-growth-record.svg','PUBLIC_ASSET','app-growth-record.svg',
    'CONFIDENTIAL','NONE',TRUE,140,'管理宝宝档案、生长测量、接种记录和成长里程碑。',
    CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON CONFLICT (app_code) DO NOTHING;

