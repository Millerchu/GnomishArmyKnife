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
ALTER TABLE IF EXISTS gak_system_app ALTER COLUMN route_path DROP NOT NULL;
ALTER TABLE IF EXISTS gak_app_audit_log ALTER COLUMN app_id DROP NOT NULL;
UPDATE gak_system_app SET data_source_mode = 'DEMO' WHERE data_source_mode IS NULL;

CREATE INDEX IF NOT EXISTS idx_work_log_user_date ON gak_work_log (user_id, log_date DESC);
CREATE INDEX IF NOT EXISTS idx_work_log_type_code ON gak_work_log_type (type_code);
CREATE INDEX IF NOT EXISTS idx_password_memo_owner_updated ON gak_password_memo (owner_user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_wow_character_owner_sort ON gak_wow_character (owner_user_id, item_level DESC, mythic_score DESC);
CREATE INDEX IF NOT EXISTS idx_todo_item_owner_sort ON gak_todo_item (owner_user_id, status, important, due_date, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_todo_item_step_task_sort ON gak_todo_item_step (task_id, sort_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_system_app_code ON gak_system_app (app_code);
CREATE INDEX IF NOT EXISTS idx_system_app_enabled_sort ON gak_system_app (enabled, sort_no, id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_app_permission_user_code ON gak_user_app_permission (user_id, app_code);
CREATE INDEX IF NOT EXISTS idx_user_app_permission_user_granted ON gak_user_app_permission (user_id, granted, app_code);
CREATE INDEX IF NOT EXISTS idx_permission_audit_target_created ON gak_permission_audit_log (target_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_app_audit_app_created ON gak_app_audit_log (app_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_data_dictionary_code_active ON gak_data_dictionary (dict_code) WHERE deleted = FALSE;
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

INSERT INTO gak_system_app (
    id, app_code, app_name, route_path, category, data_source_mode, icon_type, icon_preset, icon_text, icon_url,
    icon_storage_type, icon_file_name, security_level, encryption_mode, enabled, sort_no,
    description, remark, created_at, updated_at
) VALUES
    (2001, 'APP_CALCULATOR', '计算器', '/calculator', '效率工具', 'DEMO', 'TEXT', NULL, '计算', NULL, NULL, NULL, 'PUBLIC', 'NONE', TRUE, 10, '日常数值计算与公式换算。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2002, 'APP_WORK_LOG', '工作日志', '/work-log', '办公协作', 'REAL', 'TEXT', NULL, '日志', NULL, NULL, NULL, 'INTERNAL', 'FIELD', TRUE, 20, '记录每日工作内容、工时与项目投入。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2003, 'APP_PASSWORD_MEMO', '密码备忘录', '/password-memo', '安全工具', 'REAL', 'TEXT', NULL, '密码', NULL, NULL, NULL, 'CONFIDENTIAL', 'END_TO_END', TRUE, 30, '集中管理账号密码并做受控查看。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2004, 'APP_TODO_LIST', '待办列表', '/todo-list', '效率工具', 'REAL', 'TEXT', NULL, '待办', NULL, NULL, NULL, 'INTERNAL', 'NONE', TRUE, 40, '管理个人待办、我的一天和重要事项。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2005, 'APP_FUEL_STATS', '油耗统计', '/fuel-stats', '生活管理', 'DEMO', 'TEXT', NULL, '油耗', NULL, NULL, NULL, 'PUBLIC', 'NONE', TRUE, 50, '记录车辆油耗与加油成本趋势。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2006, 'APP_WOW_CHARACTER', 'WoW角色统计', '/wow-character-stats', '娱乐收藏', 'REAL', 'TEXT', NULL, '魔兽', NULL, NULL, NULL, 'PUBLIC', 'NONE', TRUE, 60, '维护角色装等、大秘境和职业分布。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2007, 'APP_PERSONAL_BILLS', '个人账单', '/personal-bills', '财务管理', 'DEMO', 'TEXT', NULL, '账单', NULL, NULL, NULL, 'CONFIDENTIAL', 'FIELD', TRUE, 70, '汇总个人收支、预算与消费明细。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2008, 'APP_KNOWLEDGE_BASE', '经验库', '/knowledge-base', '知识沉淀', 'DEMO', 'TEXT', NULL, '经验', NULL, NULL, NULL, 'INTERNAL', 'NONE', TRUE, 80, '沉淀问题处理经验和通用操作手册。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2009, 'APP_SOFTWARE_REPO', '软件仓库', '/software-repo', '资源管理', 'DEMO', 'TEXT', NULL, '软件', NULL, NULL, NULL, 'INTERNAL', 'NONE', TRUE, 90, '整理常用软件、版本与下载入口。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2010, 'APP_HEALTH_RECORD', '健康', '/health', '生活管理', 'DEMO', 'TEXT', NULL, '健康', NULL, NULL, NULL, 'CONFIDENTIAL', 'FIELD', TRUE, 100, '记录体征、就医与个人健康档案。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2011, 'APP_DATA_DICTIONARY', '数据字典', '/data-dictionary', '系统管理', 'REAL', 'TEXT', NULL, '字典', NULL, NULL, NULL, 'INTERNAL', 'NONE', TRUE, 110, '维护系统可配置选项与字典项。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
