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

CREATE TABLE IF NOT EXISTS gak_personal_bill (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    bill_type VARCHAR(16) NOT NULL,
    category_name VARCHAR(64) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    account_name VARCHAR(64),
    payment_method VARCHAR(64),
    merchant_name VARCHAR(96),
    bill_date DATE NOT NULL,
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_personal_budget (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    budget_year INTEGER NOT NULL,
    category_name VARCHAR(64) NOT NULL,
    annual_limit NUMERIC(12, 2) NOT NULL,
    alert_threshold NUMERIC(4, 2) NOT NULL,
    note VARCHAR(120),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_todo_item (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    list_code VARCHAR(20) NOT NULL,
    importance VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    important BOOLEAN NOT NULL DEFAULT FALSE,
    due_date DATE,
    reminder_at TIMESTAMP,
    note TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_todo_item_step (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    title VARCHAR(80) NOT NULL,
    done BOOLEAN NOT NULL DEFAULT FALSE,
    sort_no INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_todo_item_step_task
        FOREIGN KEY (task_id) REFERENCES gak_todo_item (id) ON DELETE CASCADE
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
    off_work_time TIME,
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

CREATE TABLE IF NOT EXISTS gak_system_app (
    id BIGINT PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL,
    app_name VARCHAR(64) NOT NULL,
    route_path VARCHAR(128),
    category VARCHAR(64),
    data_source_mode VARCHAR(16) NOT NULL DEFAULT 'DEMO',
    icon_type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    icon_preset VARCHAR(64),
    icon_text VARCHAR(32),
    icon_url VARCHAR(255),
    icon_storage_type VARCHAR(32),
    icon_file_name VARCHAR(255),
    security_level VARCHAR(20) NOT NULL,
    encryption_mode VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_no INTEGER NOT NULL DEFAULT 0,
    description VARCHAR(255),
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_user_app_permission (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    app_id BIGINT NOT NULL,
    app_code VARCHAR(64) NOT NULL,
    granted BOOLEAN NOT NULL DEFAULT TRUE,
    granted_by BIGINT,
    granted_at TIMESTAMP,
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_permission_audit_log (
    id BIGINT PRIMARY KEY,
    operator_user_id BIGINT,
    target_user_id BIGINT,
    action_type VARCHAR(32) NOT NULL,
    before_json TEXT,
    after_json TEXT,
    trace_id VARCHAR(64),
    ip VARCHAR(64),
    user_agent VARCHAR(255),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_app_audit_log (
    id BIGINT PRIMARY KEY,
    operator_user_id BIGINT,
    app_id BIGINT,
    action_type VARCHAR(32) NOT NULL,
    before_json TEXT,
    after_json TEXT,
    ip VARCHAR(64),
    user_agent VARCHAR(255),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_data_dictionary (
    id BIGINT PRIMARY KEY,
    dict_code VARCHAR(64) NOT NULL,
    dict_name VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reference_apps_json TEXT,
    description VARCHAR(255),
    creator_user_id BIGINT,
    creator_name VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS gak_data_dictionary_item (
    id BIGINT PRIMARY KEY,
    dictionary_id BIGINT NOT NULL,
    dict_code VARCHAR(64) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_label VARCHAR(64) NOT NULL,
    item_value VARCHAR(64) NOT NULL,
    sort_no INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(255),
    extra_json TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS gak_data_dictionary_usage (
    id BIGINT PRIMARY KEY,
    dict_code VARCHAR(64) NOT NULL,
    dictionary_id BIGINT,
    app_code VARCHAR(64) NOT NULL,
    app_name VARCHAR(64),
    module_code VARCHAR(64) NOT NULL,
    module_name VARCHAR(64),
    biz_field_code VARCHAR(64) NOT NULL,
    biz_field_name VARCHAR(64),
    usage_type VARCHAR(32) NOT NULL,
    value_mode VARCHAR(32) NOT NULL,
    allow_multiple BOOLEAN NOT NULL DEFAULT FALSE,
    required_flag BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    usage_count BIGINT NOT NULL DEFAULT 0,
    last_used_at TIMESTAMP,
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE gak_system_app ADD COLUMN IF NOT EXISTS icon_preset VARCHAR(64);
ALTER TABLE gak_system_app ADD COLUMN IF NOT EXISTS icon_storage_type VARCHAR(32);
ALTER TABLE gak_system_app ADD COLUMN IF NOT EXISTS icon_file_name VARCHAR(255);
ALTER TABLE gak_system_app ADD COLUMN IF NOT EXISTS data_source_mode VARCHAR(16) NOT NULL DEFAULT 'DEMO';
ALTER TABLE gak_work_log ADD COLUMN IF NOT EXISTS off_work_time TIME;
ALTER TABLE IF EXISTS gak_system_app ALTER COLUMN route_path DROP NOT NULL;
ALTER TABLE IF EXISTS gak_app_audit_log ALTER COLUMN app_id DROP NOT NULL;
UPDATE gak_system_app SET data_source_mode = 'DEMO' WHERE data_source_mode IS NULL;

CREATE INDEX IF NOT EXISTS idx_work_log_user_date ON gak_work_log (user_id, log_date DESC);
CREATE INDEX IF NOT EXISTS idx_work_log_type_code ON gak_work_log_type (type_code);
CREATE INDEX IF NOT EXISTS idx_password_memo_owner_updated ON gak_password_memo (owner_user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_wow_character_owner_sort ON gak_wow_character (owner_user_id, item_level DESC, mythic_score DESC);
CREATE INDEX IF NOT EXISTS idx_personal_bill_owner_date ON gak_personal_bill (owner_user_id, bill_date DESC, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_personal_bill_owner_type_date ON gak_personal_bill (owner_user_id, bill_type, bill_date DESC);
CREATE INDEX IF NOT EXISTS idx_personal_budget_owner_year ON gak_personal_budget (owner_user_id, budget_year, category_name);
CREATE UNIQUE INDEX IF NOT EXISTS uk_personal_budget_owner_year_category ON gak_personal_budget (owner_user_id, budget_year, category_name);
CREATE INDEX IF NOT EXISTS idx_todo_item_owner_sort ON gak_todo_item (owner_user_id, status, important, due_date, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_todo_item_step_task_sort ON gak_todo_item_step (task_id, sort_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_system_app_code ON gak_system_app (app_code);
CREATE INDEX IF NOT EXISTS idx_system_app_enabled_sort ON gak_system_app (enabled, sort_no, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_app_permission_user_code ON gak_user_app_permission (user_id, app_code);
CREATE INDEX IF NOT EXISTS idx_user_app_permission_user_granted ON gak_user_app_permission (user_id, granted, app_code);
CREATE INDEX IF NOT EXISTS idx_permission_audit_target_created ON gak_permission_audit_log (target_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_app_audit_app_created ON gak_app_audit_log (app_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_data_dictionary_code_active ON gak_data_dictionary (dict_code) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS gak_data_migration_task (
    id BIGINT PRIMARY KEY,
    task_no VARCHAR(40) NOT NULL,
    task_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    scope_mode VARCHAR(20),
    package_name VARCHAR(128) NOT NULL,
    system_resource_codes TEXT,
    business_app_codes TEXT,
    include_attachments BOOLEAN NOT NULL DEFAULT FALSE,
    import_mode VARCHAR(20),
    continue_on_error BOOLEAN NOT NULL DEFAULT FALSE,
    record_count BIGINT NOT NULL DEFAULT 0,
    attachment_count BIGINT NOT NULL DEFAULT 0,
    file_url VARCHAR(512),
    file_storage_type VARCHAR(32),
    file_name VARCHAR(255),
    file_size BIGINT,
    error_message TEXT,
    remark VARCHAR(255),
    operator_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS gak_data_migration_task_item (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    resource_code VARCHAR(64) NOT NULL,
    resource_name VARCHAR(128) NOT NULL,
    resource_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    record_count BIGINT NOT NULL DEFAULT 0,
    attachment_count BIGINT NOT NULL DEFAULT 0,
    message VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    CONSTRAINT fk_data_migration_task_item_task
        FOREIGN KEY (task_id) REFERENCES gak_data_migration_task (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS gak_fuel_record (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    vehicle_name VARCHAR(64) NOT NULL,
    fuel_date DATE NOT NULL,
    odometer_km NUMERIC(10, 1) NOT NULL,
    fuel_volume NUMERIC(8, 2) NOT NULL,
    total_amount NUMERIC(10, 2) NOT NULL,
    discounted_amount NUMERIC(10, 2) NOT NULL,
    unit_price NUMERIC(8, 3) NOT NULL,
    fuel_type VARCHAR(16) NOT NULL,
    fill_type VARCHAR(16) NOT NULL,
    station_name VARCHAR(128),
    note VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_fuel_price_snapshot (
    id BIGINT PRIMARY KEY,
    publish_date TIMESTAMP NOT NULL,
    next_adjust_time TIMESTAMP,
    adjust_window VARCHAR(64),
    price_change_hint VARCHAR(255),
    price_92 NUMERIC(6, 2) NOT NULL,
    price_95 NUMERIC(6, 2) NOT NULL,
    price_98 NUMERIC(6, 2) NOT NULL,
    price_diesel NUMERIC(6, 2) NOT NULL,
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE gak_fuel_price_snapshot ADD COLUMN IF NOT EXISTS next_adjust_time TIMESTAMP;
ALTER TABLE gak_fuel_price_snapshot ADD COLUMN IF NOT EXISTS adjust_window VARCHAR(64);
ALTER TABLE gak_fuel_price_snapshot ADD COLUMN IF NOT EXISTS price_change_hint VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uk_data_migration_task_no ON gak_data_migration_task (task_no);
CREATE INDEX IF NOT EXISTS idx_data_migration_task_created ON gak_data_migration_task (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_data_migration_task_type_status ON gak_data_migration_task (task_type, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_data_migration_task_item_task ON gak_data_migration_task_item (task_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_data_dictionary_status_created ON gak_data_dictionary (deleted, status, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_data_dictionary_item_code_active
    ON gak_data_dictionary_item (dictionary_id, item_code)
    WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uk_data_dictionary_item_value_active
    ON gak_data_dictionary_item (dictionary_id, item_value)
    WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_data_dictionary_item_dictionary_sort
    ON gak_data_dictionary_item (dictionary_id, deleted, sort_no, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_data_dictionary_usage_field
    ON gak_data_dictionary_usage (app_code, module_code, biz_field_code);
CREATE INDEX IF NOT EXISTS idx_fuel_record_owner_date
    ON gak_fuel_record (owner_user_id, fuel_date DESC, odometer_km DESC, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_fuel_record_owner_vehicle_date
    ON gak_fuel_record (owner_user_id, vehicle_name, fuel_date ASC, odometer_km ASC);
CREATE INDEX IF NOT EXISTS idx_fuel_price_snapshot_publish_date
    ON gak_fuel_price_snapshot (publish_date DESC, updated_at DESC);

INSERT INTO gak_system_app (
    id, app_code, app_name, route_path, category, data_source_mode, icon_type, icon_preset, icon_text, icon_url,
    icon_storage_type, icon_file_name, security_level, encryption_mode, enabled, sort_no,
    description, remark, created_at, updated_at
) VALUES
    (2001, 'APP_CALCULATOR', '计算器', '/calculator', '效率工具', 'DEMO', 'URL', NULL, '计算', '/app-icons/app-calculator.png', 'PUBLIC_ASSET', 'app-calculator.png', 'PUBLIC', 'NONE', TRUE, 10, '日常数值计算与公式换算。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2002, 'APP_WORK_LOG', '工作日志', '/work-log', '办公协作', 'REAL', 'URL', NULL, '日志', '/app-icons/app-work-log.png', 'PUBLIC_ASSET', 'app-work-log.png', 'INTERNAL', 'FIELD', TRUE, 20, '记录每日工作内容、工时与项目投入。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2003, 'APP_PASSWORD_MEMO', '密码备忘录', '/password-memo', '安全工具', 'REAL', 'URL', NULL, '密码', '/app-icons/app-password-memo.png', 'PUBLIC_ASSET', 'app-password-memo.png', 'CONFIDENTIAL', 'END_TO_END', TRUE, 30, '集中管理账号密码并做受控查看。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2004, 'APP_TODO_LIST', '待办列表', '/todo-list', '效率工具', 'REAL', 'URL', NULL, '待办', '/app-icons/app-todo-list.png', 'PUBLIC_ASSET', 'app-todo-list.png', 'INTERNAL', 'NONE', TRUE, 40, '管理个人待办、我的一天和重要事项。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2005, 'APP_FUEL_STATS', '油耗统计', '/fuel-stats', '生活管理', 'REAL', 'URL', NULL, '油耗', '/app-icons/app-fuel-stats.png', 'PUBLIC_ASSET', 'app-fuel-stats.png', 'PUBLIC', 'NONE', TRUE, 50, '记录车辆油耗与加油成本趋势。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2006, 'APP_WOW_CHARACTER', 'WoW角色统计', '/wow-character-stats', '娱乐收藏', 'REAL', 'URL', NULL, '魔兽', '/app-icons/app-wow-character.png', 'PUBLIC_ASSET', 'app-wow-character.png', 'PUBLIC', 'NONE', TRUE, 60, '维护角色装等、大秘境和职业分布。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2007, 'APP_PERSONAL_BILLS', '个人账单', '/personal-bills', '财务管理', 'REAL', 'URL', NULL, '账单', '/app-icons/app-personal-bills.png', 'PUBLIC_ASSET', 'app-personal-bills.png', 'CONFIDENTIAL', 'FIELD', TRUE, 70, '汇总个人收支、预算与消费明细。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2008, 'APP_KNOWLEDGE_BASE', '经验库', '/knowledge-base', '知识沉淀', 'DEMO', 'URL', NULL, '经验', '/app-icons/app-knowledge-base.png', 'PUBLIC_ASSET', 'app-knowledge-base.png', 'INTERNAL', 'NONE', TRUE, 80, '沉淀问题处理经验和通用操作手册。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2009, 'APP_SOFTWARE_REPO', '软件仓库', '/software-repo', '资源管理', 'DEMO', 'URL', NULL, '软件', '/app-icons/app-software-repo.png', 'PUBLIC_ASSET', 'app-software-repo.png', 'INTERNAL', 'NONE', TRUE, 90, '整理常用软件、版本与下载入口。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2010, 'APP_HEALTH_RECORD', '健康', '/health', '生活管理', 'DEMO', 'URL', NULL, '健康', '/app-icons/app-health-record.png', 'PUBLIC_ASSET', 'app-health-record.png', 'CONFIDENTIAL', 'FIELD', TRUE, 100, '记录体征、就医与个人健康档案。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2011, 'APP_DATA_DICTIONARY', '数据字典', '/data-dictionary', '系统管理', 'REAL', 'URL', NULL, '字典', '/app-icons/app-data-dictionary.png', 'PUBLIC_ASSET', 'app-data-dictionary.png', 'INTERNAL', 'NONE', TRUE, 110, '维护系统可配置选项与字典项。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (app_code) DO UPDATE SET
    app_name = EXCLUDED.app_name,
    route_path = EXCLUDED.route_path,
    category = EXCLUDED.category,
    data_source_mode = EXCLUDED.data_source_mode,
    icon_type = EXCLUDED.icon_type,
    icon_preset = EXCLUDED.icon_preset,
    icon_text = EXCLUDED.icon_text,
    icon_url = EXCLUDED.icon_url,
    icon_storage_type = EXCLUDED.icon_storage_type,
    icon_file_name = EXCLUDED.icon_file_name,
    security_level = EXCLUDED.security_level,
    encryption_mode = EXCLUDED.encryption_mode,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    description = EXCLUDED.description,
    remark = EXCLUDED.remark,
    updated_at = EXCLUDED.updated_at;

INSERT INTO gak_user (
    id, username, password_hash, display_name, phone, email, role_code, status, enabled,
    force_change_password, last_login_time, remark, created_at, updated_at
)
VALUES (
    900000000000000001,
    'admin',
    '$2y$10$z8ekeDBn1ICzkV7tw8WdC.uuyU3XnMIvDiK7SF5LD8Uz0zUiIn2b6',
    '系统管理员',
    NULL,
    NULL,
    'ADMIN',
    'ENABLED',
    TRUE,
    FALSE,
    NULL,
    'schema init',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (username) DO NOTHING;

UPDATE gak_user
SET role_code = 'ADMIN',
    status = 'ENABLED',
    enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'admin';

INSERT INTO gak_fuel_price_snapshot (
    id, publish_date, next_adjust_time, adjust_window, price_change_hint,
    price_92, price_95, price_98, price_diesel, remark, created_at, updated_at
)
VALUES (
    7101,
    CURRENT_TIMESTAMP,
    TIMESTAMP '2026-05-20 00:00:00',
    '5月19日24时',
    '当前以窄幅波动为主，下一轮调价窗口已临近。',
    7.58,
    8.09,
    8.96,
    7.26,
    '默认参考油价',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    publish_date = EXCLUDED.publish_date,
    next_adjust_time = EXCLUDED.next_adjust_time,
    adjust_window = EXCLUDED.adjust_window,
    price_change_hint = EXCLUDED.price_change_hint,
    price_92 = EXCLUDED.price_92,
    price_95 = EXCLUDED.price_95,
    price_98 = EXCLUDED.price_98,
    price_diesel = EXCLUDED.price_diesel,
    remark = EXCLUDED.remark,
    updated_at = EXCLUDED.updated_at;

INSERT INTO gak_fuel_record (
    id, owner_user_id, vehicle_name, fuel_date, odometer_km, fuel_volume, total_amount,
    discounted_amount, unit_price, fuel_type, fill_type, station_name, note, created_at, updated_at
)
SELECT *
FROM (
    VALUES
        (7201, 900000000000000001, 'Model Y', DATE '2026-02-24', 14430.0, 40.15, 312.37, 300.37, 7.481, '95', 'PARTIAL', '中国石化滨江站', '长途出发前补油。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7202, 900000000000000001, '卡罗拉', DATE '2026-03-01', 85736.0, 34.12, 255.22, 251.22, 7.363, '92', 'FULL', '壳牌文一路站', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7203, 900000000000000001, 'Model Y', DATE '2026-03-05', 14876.0, 39.82, 314.18, 302.18, 7.589, '95', 'FULL', '中国石油城西站', '工作周通勤加油。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7204, 900000000000000001, '卡罗拉', DATE '2026-03-12', 86210.0, 33.57, 251.77, 245.77, 7.321, '92', 'FULL', '壳牌文一路站', '城区通勤，油耗相对稳定。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7205, 900000000000000001, 'Model Y', DATE '2026-03-13', 15320.0, 41.26, 326.78, 308.78, 7.484, '95', 'FULL', '中国石化滨江站', '周末高速返程后加满。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7206, 900000000000000001, 'Model Y', DATE '2026-04-02', 15788.0, 38.64, 305.11, 294.11, 7.612, '95', 'FULL', '中国石化滨江站', '清明前通勤用车。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7207, 900000000000000001, '卡罗拉', DATE '2026-04-10', 86695.0, 31.44, 236.09, 231.09, 7.350, '92', 'PARTIAL', '中国石油城北站', '市区短途补油。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7208, 900000000000000001, 'Model Y', DATE '2026-04-18', 16292.0, 40.08, 318.26, 306.26, 7.641, '95', 'FULL', '壳牌滨文站', '周末郊区往返。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7209, 900000000000000001, '卡罗拉', DATE '2026-04-28', 87162.0, 35.02, 264.13, 258.13, 7.372, '92', 'FULL', '壳牌文一路站', '月末补满。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7210, 900000000000000001, 'Model Y', DATE '2026-05-06', 16775.0, 39.27, 313.04, 301.04, 7.665, '95', 'FULL', '中国石化滨江站', '五一返程后加油。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS seeded_records (
    id, owner_user_id, vehicle_name, fuel_date, odometer_km, fuel_volume, total_amount,
    discounted_amount, unit_price, fuel_type, fill_type, station_name, note, created_at, updated_at
)
ON CONFLICT (id) DO UPDATE SET
    owner_user_id = EXCLUDED.owner_user_id,
    vehicle_name = EXCLUDED.vehicle_name,
    fuel_date = EXCLUDED.fuel_date,
    odometer_km = EXCLUDED.odometer_km,
    fuel_volume = EXCLUDED.fuel_volume,
    total_amount = EXCLUDED.total_amount,
    discounted_amount = EXCLUDED.discounted_amount,
    unit_price = EXCLUDED.unit_price,
    fuel_type = EXCLUDED.fuel_type,
    fill_type = EXCLUDED.fill_type,
    station_name = EXCLUDED.station_name,
    note = EXCLUDED.note,
    updated_at = EXCLUDED.updated_at;

INSERT INTO gak_data_dictionary (
    id, dict_code, dict_name, status, reference_apps_json, description,
    creator_user_id, creator_name, created_at, updated_at, deleted
)
SELECT
    5001, 'USER_ROLE_TYPE', '用户角色类型', 'ENABLED', '["用户管理"]', '用户角色下拉选项',
    admin_user.id, admin_user.display_name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_user admin_user
WHERE admin_user.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary dictionary
    WHERE dictionary.dict_code = 'USER_ROLE_TYPE' AND dictionary.deleted = FALSE
  );

INSERT INTO gak_data_dictionary (
    id, dict_code, dict_name, status, reference_apps_json, description,
    creator_user_id, creator_name, created_at, updated_at, deleted
)
SELECT
    5002, 'USER_STATUS', '用户状态', 'ENABLED', '["用户管理"]', '用户状态选项',
    admin_user.id, admin_user.display_name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_user admin_user
WHERE admin_user.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary dictionary
    WHERE dictionary.dict_code = 'USER_STATUS' AND dictionary.deleted = FALSE
  );

INSERT INTO gak_data_dictionary (
    id, dict_code, dict_name, status, reference_apps_json, description,
    creator_user_id, creator_name, created_at, updated_at, deleted
)
SELECT
    5003, 'WORK_LOG_TYPE', '工作日志类型', 'ENABLED', '["工作日志"]', '工作日志类型选项',
    admin_user.id, admin_user.display_name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_user admin_user
WHERE admin_user.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary dictionary
    WHERE dictionary.dict_code = 'WORK_LOG_TYPE' AND dictionary.deleted = FALSE
  );

INSERT INTO gak_data_dictionary (
    id, dict_code, dict_name, status, reference_apps_json, description,
    creator_user_id, creator_name, created_at, updated_at, deleted
)
SELECT
    5004, 'WORK_LOG_PROJECT', '工作日志项目', 'ENABLED', '["工作日志"]', '工作日志项目选项',
    admin_user.id, admin_user.display_name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_user admin_user
WHERE admin_user.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary dictionary
    WHERE dictionary.dict_code = 'WORK_LOG_PROJECT' AND dictionary.deleted = FALSE
  );

INSERT INTO gak_data_dictionary (
    id, dict_code, dict_name, status, reference_apps_json, description,
    creator_user_id, creator_name, created_at, updated_at, deleted
)
SELECT
    5008, 'PERSONAL_BILLS_BILL_CATEGORY', '个人账单分类', 'ENABLED', '["个人账单"]', '个人账单收支分类选项',
    admin_user.id, admin_user.display_name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_user admin_user
WHERE admin_user.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary dictionary
    WHERE dictionary.dict_code = 'PERSONAL_BILLS_BILL_CATEGORY' AND dictionary.deleted = FALSE
  );

INSERT INTO gak_data_dictionary (
    id, dict_code, dict_name, status, reference_apps_json, description,
    creator_user_id, creator_name, created_at, updated_at, deleted
)
SELECT
    5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', '个人预算分类', 'ENABLED', '["个人账单"]', '年度预算分类选项',
    admin_user.id, admin_user.display_name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_user admin_user
WHERE admin_user.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary dictionary
    WHERE dictionary.dict_code = 'PERSONAL_BILLS_BUDGET_CATEGORY' AND dictionary.deleted = FALSE
  );

INSERT INTO gak_data_dictionary (
    id, dict_code, dict_name, status, reference_apps_json, description,
    creator_user_id, creator_name, created_at, updated_at, deleted
)
SELECT
    5010, 'PERSONAL_BILLS_PAYMENT_METHOD', '个人账单支付方式', 'ENABLED', '["个人账单"]', '个人账单支付方式选项',
    admin_user.id, admin_user.display_name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_user admin_user
WHERE admin_user.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary dictionary
    WHERE dictionary.dict_code = 'PERSONAL_BILLS_PAYMENT_METHOD' AND dictionary.deleted = FALSE
  );

INSERT INTO gak_data_dictionary (
    id, dict_code, dict_name, status, reference_apps_json, description,
    creator_user_id, creator_name, created_at, updated_at, deleted
)
SELECT
    5007, 'WORK_LOG_LOCATION', '工作地点', 'ENABLED', '["工作日志"]', '工作地点选项',
    admin_user.id, admin_user.display_name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_user admin_user
WHERE admin_user.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary dictionary
    WHERE dictionary.dict_code = 'WORK_LOG_LOCATION' AND dictionary.deleted = FALSE
  );

INSERT INTO gak_data_dictionary (
    id, dict_code, dict_name, status, reference_apps_json, description,
    creator_user_id, creator_name, created_at, updated_at, deleted
)
SELECT
    5005, 'APP_SECURITY_LEVEL', '应用密级', 'ENABLED', '["应用管理"]', '应用密级选项',
    admin_user.id, admin_user.display_name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_user admin_user
WHERE admin_user.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary dictionary
    WHERE dictionary.dict_code = 'APP_SECURITY_LEVEL' AND dictionary.deleted = FALSE
  );

INSERT INTO gak_data_dictionary (
    id, dict_code, dict_name, status, reference_apps_json, description,
    creator_user_id, creator_name, created_at, updated_at, deleted
)
SELECT
    5006, 'APP_ENCRYPTION_MODE', '应用加密方式', 'ENABLED', '["应用管理"]', '应用加密方式选项',
    admin_user.id, admin_user.display_name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_user admin_user
WHERE admin_user.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary dictionary
    WHERE dictionary.dict_code = 'APP_ENCRYPTION_MODE' AND dictionary.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5101, 5001, 'USER_ROLE_TYPE', 'admin', '管理员', 'ADMIN', 1, 'ENABLED', FALSE, '管理员角色', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5001 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5001 AND item.item_code = 'admin' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5102, 5001, 'USER_ROLE_TYPE', 'dev', '开发', 'DEV', 2, 'ENABLED', FALSE, '开发角色', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5001 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5001 AND item.item_code = 'dev' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5103, 5001, 'USER_ROLE_TYPE', 'user', '普通用户', 'USER', 3, 'ENABLED', TRUE, '默认角色', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5001 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5001 AND item.item_code = 'user' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5201, 5002, 'USER_STATUS', 'enabled', '启用', 'ENABLED', 1, 'ENABLED', TRUE, '默认启用状态', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5002 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5002 AND item.item_code = 'enabled' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5202, 5002, 'USER_STATUS', 'disabled', '禁用', 'DISABLED', 2, 'ENABLED', FALSE, '禁用状态', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5002 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5002 AND item.item_code = 'disabled' AND item.deleted = FALSE
  );

UPDATE gak_data_dictionary_item
SET deleted = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE dictionary_id = 5003
  AND deleted = FALSE
  AND (
    item_code IN ('develop', 'meeting', 'test')
    OR item_value IN ('DEVELOP', 'MEETING', 'TEST')
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5311, 5003, 'WORK_LOG_TYPE', 'normal', '常规', 'NORMAL', 1, 'ENABLED', TRUE, '常规工作记录', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5003 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5003 AND item.item_code = 'normal' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5312, 5003, 'WORK_LOG_TYPE', 'leave', '请假', 'LEAVE', 2, 'ENABLED', FALSE, '请假记录', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5003 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5003 AND item.item_code = 'leave' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5313, 5003, 'WORK_LOG_TYPE', 'business_trip', '出差', 'BUSINESS_TRIP', 3, 'ENABLED', FALSE, '出差记录', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5003 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5003 AND item.item_code = 'business_trip' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5314, 5003, 'WORK_LOG_TYPE', 'sick_leave', '病假', 'SICK_LEAVE', 4, 'ENABLED', FALSE, '病假记录', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5003 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5003 AND item.item_code = 'sick_leave' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5315, 5003, 'WORK_LOG_TYPE', 'other', '其他', 'OTHER', 5, 'ENABLED', FALSE, '其他类型', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5003 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5003 AND item.item_code = 'other' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5401, 5004, 'WORK_LOG_PROJECT', 'gak', 'GAK', 'GAK', 1, 'ENABLED', TRUE, '默认项目', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5004 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5004 AND item.item_code = 'gak' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5402, 5004, 'WORK_LOG_PROJECT', 'client', '客户项目', 'CLIENT', 2, 'ENABLED', FALSE, '客户相关项目', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5004 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5004 AND item.item_code = 'client' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5403, 5004, 'WORK_LOG_PROJECT', 'ops', '运维支持', 'OPS', 3, 'ENABLED', FALSE, '运维与支撑事项', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5004 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5004 AND item.item_code = 'ops' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5451, 5007, 'WORK_LOG_LOCATION', 'sh_office', '上海办公室', '上海办公室', 1, 'ENABLED', TRUE, '默认办公地点', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5007 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5007 AND item.item_code = 'sh_office' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5452, 5007, 'WORK_LOG_LOCATION', 'sz_office', '深圳办公室', '深圳办公室', 2, 'ENABLED', FALSE, '深圳办公地点', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5007 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5007 AND item.item_code = 'sz_office' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5453, 5007, 'WORK_LOG_LOCATION', 'home', '居家', '居家', 3, 'ENABLED', FALSE, '居家办公', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5007 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5007 AND item.item_code = 'home' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5454, 5007, 'WORK_LOG_LOCATION', 'client_site', '客户现场', '客户现场', 4, 'ENABLED', FALSE, '客户办公地点', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5007 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5007 AND item.item_code = 'client_site' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5455, 5007, 'WORK_LOG_LOCATION', 'travel', '出差在途', '出差在途', 5, 'ENABLED', FALSE, '在途办公地点', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5007 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5007 AND item.item_code = 'travel' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5501, 5005, 'APP_SECURITY_LEVEL', 'public', '公开', 'PUBLIC', 1, 'ENABLED', TRUE, '公开级别', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5005 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5005 AND item.item_code = 'public' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5502, 5005, 'APP_SECURITY_LEVEL', 'internal', '内部', 'INTERNAL', 2, 'ENABLED', FALSE, '内部级别', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5005 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5005 AND item.item_code = 'internal' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5503, 5005, 'APP_SECURITY_LEVEL', 'confidential', '机密', 'CONFIDENTIAL', 3, 'ENABLED', FALSE, '机密级别', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5005 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5005 AND item.item_code = 'confidential' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5601, 5006, 'APP_ENCRYPTION_MODE', 'none', '无加密', 'NONE', 1, 'ENABLED', TRUE, '默认加密方式', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5006 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5006 AND item.item_code = 'none' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5602, 5006, 'APP_ENCRYPTION_MODE', 'field', '字段加密', 'FIELD', 2, 'ENABLED', FALSE, '字段级加密', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5006 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5006 AND item.item_code = 'field' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5603, 5006, 'APP_ENCRYPTION_MODE', 'end_to_end', '端到端加密', 'END_TO_END', 3, 'ENABLED', FALSE, '端到端加密方式', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5006 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5006 AND item.item_code = 'end_to_end' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5701, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'food', '餐饮', '餐饮', 1, 'ENABLED', TRUE, '日常餐饮支出', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'food' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5702, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'transport', '交通', '交通', 2, 'ENABLED', FALSE, '公共交通与出行', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'transport' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5703, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'housing', '居家', '居家', 3, 'ENABLED', FALSE, '居家日用与家清', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'housing' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5704, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'entertainment', '娱乐', '娱乐', 4, 'ENABLED', FALSE, '游戏影音与聚会', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'entertainment' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5705, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'digital', '数码', '数码', 5, 'ENABLED', FALSE, '电子设备与配件', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'digital' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5706, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'learning', '学习', '学习', 6, 'ENABLED', FALSE, '课程书籍与培训', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'learning' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5707, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'travel', '旅行', '旅行', 7, 'ENABLED', FALSE, '差旅与旅游相关', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'travel' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5708, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'salary', '工资', '工资', 8, 'ENABLED', FALSE, '固定工资收入', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'salary' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5709, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'bonus', '奖金', '奖金', 9, 'ENABLED', FALSE, '绩效与奖金收入', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'bonus' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5711, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'food', '餐饮', '餐饮', 1, 'ENABLED', TRUE, '餐饮预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'food' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5712, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'transport', '交通', '交通', 2, 'ENABLED', FALSE, '交通预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'transport' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5713, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'housing', '居家', '居家', 3, 'ENABLED', FALSE, '居家预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'housing' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5714, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'entertainment', '娱乐', '娱乐', 4, 'ENABLED', FALSE, '娱乐预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'entertainment' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5715, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'digital', '数码', '数码', 5, 'ENABLED', FALSE, '数码预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'digital' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5716, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'learning', '学习', '学习', 6, 'ENABLED', FALSE, '学习预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'learning' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5717, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'travel', '旅行', '旅行', 7, 'ENABLED', FALSE, '旅行预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'travel' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5721, 5010, 'PERSONAL_BILLS_PAYMENT_METHOD', 'alipay', '支付宝', '支付宝', 1, 'ENABLED', TRUE, '支付宝支付', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5010 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5010 AND item.item_code = 'alipay' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5722, 5010, 'PERSONAL_BILLS_PAYMENT_METHOD', 'wechat_pay', '微信支付', '微信支付', 2, 'ENABLED', FALSE, '微信支付', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5010 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5010 AND item.item_code = 'wechat_pay' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5723, 5010, 'PERSONAL_BILLS_PAYMENT_METHOD', 'bank_card', '银行卡', '银行卡', 3, 'ENABLED', FALSE, '银行卡刷卡或线上支付', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5010 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5010 AND item.item_code = 'bank_card' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5724, 5010, 'PERSONAL_BILLS_PAYMENT_METHOD', 'bank_transfer', '银行转账', '银行转账', 4, 'ENABLED', FALSE, '工资或转账入账', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5010 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5010 AND item.item_code = 'bank_transfer' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5725, 5010, 'PERSONAL_BILLS_PAYMENT_METHOD', 'cash', '现金', '现金', 5, 'ENABLED', FALSE, '线下现金支付', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5010 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5010 AND item.item_code = 'cash' AND item.deleted = FALSE
  );

INSERT INTO gak_data_dictionary_usage (
    id, dict_code, dictionary_id, app_code, app_name, module_code, module_name,
    biz_field_code, biz_field_name, usage_type, value_mode, allow_multiple, required_flag,
    status, usage_count, last_used_at, remark, created_at, updated_at
)
SELECT
    7001005, 'WORK_LOG_TYPE', dictionary.id, 'APP_WORK_LOG', '工作日志', 'WORK_LOG', '工作日志',
    'typeCodes', '日志类型', 'FORM_FIELD', 'ITEM_VALUE', TRUE, TRUE,
    'ENABLED', 0, NULL, 'schema init', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM gak_data_dictionary dictionary
WHERE dictionary.dict_code = 'WORK_LOG_TYPE'
  AND dictionary.deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_usage usage
    WHERE usage.app_code = 'APP_WORK_LOG'
      AND usage.module_code = 'WORK_LOG'
      AND usage.biz_field_code = 'typeCodes'
  );

INSERT INTO gak_data_dictionary_usage (
    id, dict_code, dictionary_id, app_code, app_name, module_code, module_name,
    biz_field_code, biz_field_name, usage_type, value_mode, allow_multiple, required_flag,
    status, usage_count, last_used_at, remark, created_at, updated_at
)
SELECT
    7001006, 'WORK_LOG_PROJECT', dictionary.id, 'APP_WORK_LOG', '工作日志', 'WORK_LOG', '工作日志',
    'projectCode', '所属项目', 'FORM_FIELD', 'ITEM_VALUE', FALSE, FALSE,
    'ENABLED', 0, NULL, 'schema init', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM gak_data_dictionary dictionary
WHERE dictionary.dict_code = 'WORK_LOG_PROJECT'
  AND dictionary.deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_usage usage
    WHERE usage.app_code = 'APP_WORK_LOG'
      AND usage.module_code = 'WORK_LOG'
      AND usage.biz_field_code = 'projectCode'
  );

INSERT INTO gak_data_dictionary_usage (
    id, dict_code, dictionary_id, app_code, app_name, module_code, module_name,
    biz_field_code, biz_field_name, usage_type, value_mode, allow_multiple, required_flag,
    status, usage_count, last_used_at, remark, created_at, updated_at
)
SELECT
    7001030, 'WORK_LOG_LOCATION', dictionary.id, 'APP_WORK_LOG', '工作日志', 'WORK_LOG', '工作日志',
    'location', '工作地点', 'FORM_FIELD', 'ITEM_VALUE', FALSE, FALSE,
    'ENABLED', 0, NULL, 'schema init', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM gak_data_dictionary dictionary
WHERE dictionary.dict_code = 'WORK_LOG_LOCATION'
  AND dictionary.deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_usage usage
    WHERE usage.app_code = 'APP_WORK_LOG'
      AND usage.module_code = 'WORK_LOG'
      AND usage.biz_field_code = 'location'
  );

INSERT INTO gak_data_dictionary_usage (
    id, dict_code, dictionary_id, app_code, app_name, module_code, module_name,
    biz_field_code, biz_field_name, usage_type, value_mode, allow_multiple, required_flag,
    status, usage_count, last_used_at, remark, created_at, updated_at
)
SELECT
    7001041, 'PERSONAL_BILLS_BILL_CATEGORY', dictionary.id, 'APP_PERSONAL_BILLS', '个人账单', 'PERSONAL_BILLS', '个人账单',
    'categoryName', '账单分类', 'FORM_FIELD', 'ITEM_VALUE', FALSE, TRUE,
    'ENABLED', 0, NULL, 'schema init', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM gak_data_dictionary dictionary
WHERE dictionary.dict_code = 'PERSONAL_BILLS_BILL_CATEGORY'
  AND dictionary.deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_usage usage
    WHERE usage.app_code = 'APP_PERSONAL_BILLS'
      AND usage.module_code = 'PERSONAL_BILLS'
      AND usage.biz_field_code = 'categoryName'
  );

INSERT INTO gak_data_dictionary_usage (
    id, dict_code, dictionary_id, app_code, app_name, module_code, module_name,
    biz_field_code, biz_field_name, usage_type, value_mode, allow_multiple, required_flag,
    status, usage_count, last_used_at, remark, created_at, updated_at
)
SELECT
    7001042, 'PERSONAL_BILLS_BUDGET_CATEGORY', dictionary.id, 'APP_PERSONAL_BILLS', '个人账单', 'PERSONAL_BILLS', '个人账单',
    'budgetCategoryName', '预算分类', 'FORM_FIELD', 'ITEM_VALUE', FALSE, TRUE,
    'ENABLED', 0, NULL, 'schema init', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM gak_data_dictionary dictionary
WHERE dictionary.dict_code = 'PERSONAL_BILLS_BUDGET_CATEGORY'
  AND dictionary.deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_usage usage
    WHERE usage.app_code = 'APP_PERSONAL_BILLS'
      AND usage.module_code = 'PERSONAL_BILLS'
      AND usage.biz_field_code = 'budgetCategoryName'
  );

INSERT INTO gak_data_dictionary_usage (
    id, dict_code, dictionary_id, app_code, app_name, module_code, module_name,
    biz_field_code, biz_field_name, usage_type, value_mode, allow_multiple, required_flag,
    status, usage_count, last_used_at, remark, created_at, updated_at
)
SELECT
    7001043, 'PERSONAL_BILLS_PAYMENT_METHOD', dictionary.id, 'APP_PERSONAL_BILLS', '个人账单', 'PERSONAL_BILLS', '个人账单',
    'paymentMethod', '支付方式', 'FORM_FIELD', 'ITEM_VALUE', FALSE, FALSE,
    'ENABLED', 0, NULL, 'schema init', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM gak_data_dictionary dictionary
WHERE dictionary.dict_code = 'PERSONAL_BILLS_PAYMENT_METHOD'
  AND dictionary.deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_usage usage
    WHERE usage.app_code = 'APP_PERSONAL_BILLS'
      AND usage.module_code = 'PERSONAL_BILLS'
      AND usage.biz_field_code = 'paymentMethod'
  );

INSERT INTO gak_personal_budget (
    id, owner_user_id, budget_year, category_name, annual_limit, alert_threshold,
    note, created_at, updated_at
)
SELECT *
FROM (
    VALUES
        (7301, 900000000000000001, 2026, '餐饮', 15000.00, 0.80, '控制外食与聚餐支出。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7302, 900000000000000001, 2026, '交通', 6000.00, 0.85, '通勤和打车统一计入。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7303, 900000000000000001, 2026, '居家', 8000.00, 0.80, '家清与生活用品预算。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7304, 900000000000000001, 2026, '娱乐', 5000.00, 0.75, '游戏与影音订阅。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7305, 900000000000000001, 2026, '数码', 12000.00, 0.70, '设备和外设统一预算。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7306, 900000000000000001, 2026, '学习', 4000.00, 0.80, '课程和书籍预算。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7307, 900000000000000001, 2026, '旅行', 10000.00, 0.70, '年度出游专项预算。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS seeded_budgets (
    id, owner_user_id, budget_year, category_name, annual_limit, alert_threshold,
    note, created_at, updated_at
)
ON CONFLICT (id) DO UPDATE SET
    owner_user_id = EXCLUDED.owner_user_id,
    budget_year = EXCLUDED.budget_year,
    category_name = EXCLUDED.category_name,
    annual_limit = EXCLUDED.annual_limit,
    alert_threshold = EXCLUDED.alert_threshold,
    note = EXCLUDED.note,
    updated_at = EXCLUDED.updated_at;

INSERT INTO gak_personal_bill (
    id, owner_user_id, bill_type, category_name, amount, account_name, payment_method,
    merchant_name, bill_date, note, created_at, updated_at
)
SELECT *
FROM (
    VALUES
        (7401, 900000000000000001, 'INCOME', '工资', 18500.00, '招商银行卡', '银行转账', '工资入账', DATE '2026-05-05', '5 月工资', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7402, 900000000000000001, 'EXPENSE', '餐饮', 86.00, '招商银行卡', '支付宝', '盒马鲜生', DATE '2026-05-06', '工作日晚餐和水果。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7403, 900000000000000001, 'EXPENSE', '交通', 42.00, '微信零钱', '微信支付', '滴滴出行', DATE '2026-05-07', '加班回家打车。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7404, 900000000000000001, 'EXPENSE', '居家', 236.00, '招商银行卡', '支付宝', '京东', DATE '2026-05-08', '洗衣液和厨房清洁用品。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7405, 900000000000000001, 'EXPENSE', '娱乐', 128.00, '微信零钱', '微信支付', 'Steam', DATE '2026-05-09', '周末游戏折扣购买。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7406, 900000000000000001, 'EXPENSE', '数码', 699.00, '招商银行卡', '银行卡', 'Apple', DATE '2026-05-10', '补购配件。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7407, 900000000000000001, 'EXPENSE', '学习', 299.00, '支付宝余额', '支付宝', '极客时间', DATE '2026-05-11', '课程续费。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7408, 900000000000000001, 'INCOME', '奖金', 3200.00, '招商银行卡', '银行转账', '项目奖金', DATE '2026-04-20', '阶段奖金入账。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7409, 900000000000000001, 'EXPENSE', '旅行', 1260.00, '招商银行卡', '银行卡', '携程', DATE '2026-04-27', '端午前行程预订。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7410, 900000000000000001, 'EXPENSE', '餐饮', 68.00, '招商银行卡', '支付宝', '瑞幸咖啡', DATE '2026-04-29', '下午茶和简餐。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS seeded_bills (
    id, owner_user_id, bill_type, category_name, amount, account_name, payment_method,
    merchant_name, bill_date, note, created_at, updated_at
)
ON CONFLICT (id) DO UPDATE SET
    owner_user_id = EXCLUDED.owner_user_id,
    bill_type = EXCLUDED.bill_type,
    category_name = EXCLUDED.category_name,
    amount = EXCLUDED.amount,
    account_name = EXCLUDED.account_name,
    payment_method = EXCLUDED.payment_method,
    merchant_name = EXCLUDED.merchant_name,
    bill_date = EXCLUDED.bill_date,
    note = EXCLUDED.note,
    updated_at = EXCLUDED.updated_at;

INSERT INTO gak_user_app_permission (
    id, user_id, app_id, app_code, granted, granted_by, granted_at, remark, created_at, updated_at
)
SELECT
    400000000000000000 + app.id,
    admin_user.id,
    app.id,
    app.app_code,
    TRUE,
    admin_user.id,
    CURRENT_TIMESTAMP,
    'schema init',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM gak_system_app app
JOIN gak_user admin_user ON admin_user.username = 'admin'
WHERE app.enabled = TRUE
  AND NOT EXISTS (
    SELECT 1
    FROM gak_user_app_permission permission
    WHERE permission.user_id = admin_user.id
      AND permission.app_code = app.app_code
  );
