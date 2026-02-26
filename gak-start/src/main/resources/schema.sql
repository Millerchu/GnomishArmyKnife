CREATE TABLE IF NOT EXISTS gak_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

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
