CREATE TABLE IF NOT EXISTS gak_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100),
    role_code VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    force_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_time TIMESTAMP,
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE gak_user ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
ALTER TABLE gak_user ADD COLUMN IF NOT EXISTS email VARCHAR(100);
ALTER TABLE gak_user ADD COLUMN IF NOT EXISTS role_code VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE gak_user ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ENABLED';
ALTER TABLE gak_user ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE gak_user ADD COLUMN IF NOT EXISTS force_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE gak_user ADD COLUMN IF NOT EXISTS last_login_time TIMESTAMP;
ALTER TABLE gak_user ADD COLUMN IF NOT EXISTS remark VARCHAR(255);

UPDATE gak_user SET role_code = 'USER' WHERE role_code IS NULL;
UPDATE gak_user SET status = 'ENABLED' WHERE status IS NULL;
UPDATE gak_user SET enabled = TRUE WHERE enabled IS NULL;
UPDATE gak_user SET force_change_password = FALSE WHERE force_change_password IS NULL;

CREATE TABLE IF NOT EXISTS gak_password_memo (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    site_name VARCHAR(64) NOT NULL,
    site_url VARCHAR(255) NOT NULL,
    username VARCHAR(100),
    password_ciphertext TEXT NOT NULL,
    password_nonce VARCHAR(64) NOT NULL,
    registered_phone VARCHAR(20),
    registered_email VARCHAR(100),
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_wow_character (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    character_name VARCHAR(32) NOT NULL,
    class_name VARCHAR(24) NOT NULL,
    spec_name VARCHAR(24),
    race_name VARCHAR(24) NOT NULL,
    realm_name VARCHAR(32) NOT NULL,
    faction VARCHAR(16) NOT NULL,
    level INTEGER NOT NULL,
    item_level INTEGER NOT NULL,
    mythic_best_level INTEGER,
    mythic_dungeon_name VARCHAR(32),
    mythic_score INTEGER,
    profession_primary VARCHAR(32),
    profession_secondary VARCHAR(32),
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE gak_wow_character ADD COLUMN IF NOT EXISTS mythic_dungeon_name VARCHAR(32);

CREATE TABLE IF NOT EXISTS gak_work_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    log_date DATE NOT NULL,
    location VARCHAR(64),
    project_code VARCHAR(128),
    content TEXT NOT NULL,
    zentao_no VARCHAR(255),
    person_day NUMERIC(4, 1) NOT NULL,
    overtime_hours NUMERIC(4, 1),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_work_log_user_date UNIQUE (user_id, log_date)
    );

CREATE TABLE IF NOT EXISTS gak_work_log_type (
    id BIGINT PRIMARY KEY,
    work_log_id BIGINT NOT NULL,
    type_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_work_log_type_work_log
    FOREIGN KEY (work_log_id) REFERENCES gak_work_log (id) ON DELETE CASCADE,
    CONSTRAINT uk_work_log_type_unique
    UNIQUE (work_log_id, type_code)
    );

CREATE INDEX IF NOT EXISTS idx_work_log_user_date ON gak_work_log (user_id, log_date DESC);
CREATE INDEX IF NOT EXISTS idx_work_log_type_code ON gak_work_log_type (type_code);
CREATE INDEX IF NOT EXISTS idx_password_memo_owner_updated ON gak_password_memo (owner_user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_wow_character_owner_sort ON gak_wow_character (owner_user_id, item_level DESC, mythic_score DESC);
