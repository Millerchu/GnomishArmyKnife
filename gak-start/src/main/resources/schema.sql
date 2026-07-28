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
    category VARCHAR(64) NOT NULL DEFAULT '其他',
    site_name VARCHAR(64) NOT NULL,
    site_url VARCHAR(255) NOT NULL,
    username VARCHAR(100),
    password_ciphertext TEXT NOT NULL,
    password_nonce VARCHAR(64) NOT NULL,
    password_started_at TIMESTAMP NOT NULL,
    registered_phone VARCHAR(20),
    registered_email VARCHAR(100),
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE gak_password_memo ADD COLUMN IF NOT EXISTS password_started_at TIMESTAMP;
UPDATE gak_password_memo SET password_started_at = created_at WHERE password_started_at IS NULL;
ALTER TABLE gak_password_memo ALTER COLUMN password_started_at SET NOT NULL;
ALTER TABLE gak_password_memo ADD COLUMN IF NOT EXISTS category VARCHAR(64);
ALTER TABLE gak_password_memo ALTER COLUMN category TYPE VARCHAR(64);
UPDATE gak_password_memo SET category = '生活' WHERE category = 'LIFE';
UPDATE gak_password_memo SET category = '工作' WHERE category = 'WORK';
UPDATE gak_password_memo SET category = '其他' WHERE category = 'OTHER';
UPDATE gak_password_memo SET category = '其他' WHERE category IS NULL OR BTRIM(category) = '';
ALTER TABLE gak_password_memo ALTER COLUMN category SET DEFAULT '其他';
ALTER TABLE gak_password_memo ALTER COLUMN category SET NOT NULL;

CREATE TABLE IF NOT EXISTS gak_password_memo_history (
    id BIGINT PRIMARY KEY,
    memo_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    password_ciphertext TEXT NOT NULL,
    password_nonce VARCHAR(64) NOT NULL,
    usage_started_at TIMESTAMP NOT NULL,
    usage_ended_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
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
    item_level NUMERIC(8, 2) NOT NULL,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    mythic_best_level INTEGER,
    mythic_dungeon_name VARCHAR(32),
    mythic_score NUMERIC(10, 2),
    profession_primary VARCHAR(32),
    profession_secondary VARCHAR(32),
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE gak_wow_character ADD COLUMN IF NOT EXISTS mythic_dungeon_name VARCHAR(32);
ALTER TABLE gak_wow_character ADD COLUMN IF NOT EXISTS is_featured BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE gak_wow_character SET is_featured = FALSE WHERE is_featured IS NULL;
ALTER TABLE gak_wow_character ALTER COLUMN item_level TYPE NUMERIC(8, 2) USING item_level::NUMERIC(8, 2);
ALTER TABLE gak_wow_character ALTER COLUMN mythic_score TYPE NUMERIC(10, 2) USING mythic_score::NUMERIC(10, 2);

CREATE TABLE IF NOT EXISTS gak_wow_character_mythic_run (
    id BIGINT PRIMARY KEY,
    character_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    dungeon_name VARCHAR(32) NOT NULL,
    best_timed_level INTEGER NOT NULL DEFAULT 0,
    score NUMERIC(10, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE gak_wow_character_mythic_run ADD COLUMN IF NOT EXISTS score NUMERIC(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE gak_wow_character_mythic_run ALTER COLUMN score TYPE NUMERIC(10, 2) USING score::NUMERIC(10, 2);
UPDATE gak_wow_character_mythic_run
SET score = (best_timed_level * 25)::NUMERIC(10, 2)
WHERE best_timed_level > 0
  AND (score IS NULL OR score = 0);

CREATE TABLE IF NOT EXISTS gak_wow_character_weekly_vault (
    id BIGINT PRIMARY KEY,
    character_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    week_start_date DATE NOT NULL,
    raid_progress_count INTEGER NOT NULL DEFAULT 0,
    mythic_progress_count INTEGER NOT NULL DEFAULT 0,
    world_progress_count INTEGER NOT NULL DEFAULT 0,
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_wow_character_keybinding (
    id BIGINT PRIMARY KEY,
    character_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    spec_name VARCHAR(24),
    binding_name VARCHAR(64),
    binding_content TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE gak_wow_character_keybinding ADD COLUMN IF NOT EXISTS binding_name VARCHAR(64);
ALTER TABLE gak_wow_character_keybinding ALTER COLUMN spec_name DROP NOT NULL;
UPDATE gak_wow_character_keybinding
SET binding_name = spec_name
WHERE binding_name IS NULL OR BTRIM(binding_name) = '';
ALTER TABLE gak_wow_character_keybinding ALTER COLUMN binding_name SET NOT NULL;

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

CREATE TABLE IF NOT EXISTS gak_health_record (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    measure_date DATE NOT NULL,
    height_cm NUMERIC(6, 1),
    weight_kg NUMERIC(6, 1),
    body_fat_rate NUMERIC(5, 1),
    systolic_pressure INTEGER,
    diastolic_pressure INTEGER,
    total_cholesterol NUMERIC(6, 2),
    triglycerides NUMERIC(6, 2),
    hdl_cholesterol NUMERIC(6, 2),
    ldl_cholesterol NUMERIC(6, 2),
    fasting_glucose NUMERIC(6, 2),
    heart_rate INTEGER,
    uric_acid INTEGER,
    alanine_aminotransferase INTEGER,
    aspartate_aminotransferase INTEGER,
    gamma_glutamyl_transferase INTEGER,
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_attachment (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    business_type VARCHAR(40),
    business_id BIGINT,
    usage_type VARCHAR(20) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_extension VARCHAR(20) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_provider VARCHAR(32) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    thumbnail_key VARCHAR(512),
    status VARCHAR(20) NOT NULL,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    bound_at TIMESTAMP,
    deleted_at TIMESTAMP,
    purged_at TIMESTAMP,
    legacy_source_key VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS gak_instrument_practice_take (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    instrument_id VARCHAR(24) NOT NULL,
    tuning_id VARCHAR(64) NOT NULL,
    bpm INTEGER NOT NULL,
    meter VARCHAR(8) NOT NULL,
    duration_ms BIGINT NOT NULL,
    events_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_health_visit (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    visit_date DATE NOT NULL,
    hospital_name VARCHAR(64) NOT NULL,
    department_name VARCHAR(64),
    doctor_name VARCHAR(64),
    visit_type VARCHAR(24),
    chief_complaint VARCHAR(240),
    diagnosis_summary VARCHAR(500),
    treatment_plan VARCHAR(500),
    doctor_advice VARCHAR(500),
    case_record_file_name VARCHAR(255),
    case_record_url VARCHAR(255),
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_health_report (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    visit_id BIGINT,
    exam_date DATE NOT NULL,
    hospital_name VARCHAR(64),
    report_title VARCHAR(64) NOT NULL,
    summary VARCHAR(240),
    doctor_advice VARCHAR(240),
    report_file_name VARCHAR(255),
    report_url VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS gak_knowledge_entry (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    title VARCHAR(64) NOT NULL,
    category_name VARCHAR(32) NOT NULL,
    scenario VARCHAR(80) NOT NULL,
    source_name VARCHAR(80),
    tags_text VARCHAR(255),
    summary VARCHAR(180) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE gak_knowledge_entry ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE gak_knowledge_entry ADD COLUMN IF NOT EXISTS reviewed_by BIGINT;
ALTER TABLE gak_knowledge_entry ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
ALTER TABLE gak_knowledge_entry ADD COLUMN IF NOT EXISTS review_remark VARCHAR(200);

UPDATE gak_knowledge_entry
SET status = 'PUBLISHED'
WHERE status IS NULL;

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

CREATE TABLE IF NOT EXISTS gak_requirement (
    id BIGINT PRIMARY KEY,
    creator_user_id BIGINT NOT NULL,
    app_code VARCHAR(64) NOT NULL,
    app_name VARCHAR(64) NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE gak_requirement ADD COLUMN IF NOT EXISTS app_code VARCHAR(64);
ALTER TABLE gak_requirement ADD COLUMN IF NOT EXISTS app_name VARCHAR(64);
UPDATE gak_requirement SET app_code = 'APP_GENERAL' WHERE app_code IS NULL OR TRIM(app_code) = '';
UPDATE gak_requirement SET app_name = '通用' WHERE app_name IS NULL OR TRIM(app_name) = '';
ALTER TABLE gak_requirement ALTER COLUMN app_code SET NOT NULL;
ALTER TABLE gak_requirement ALTER COLUMN app_name SET NOT NULL;

CREATE TABLE IF NOT EXISTS gak_requirement_progress_log (
    id BIGINT PRIMARY KEY,
    requirement_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    remark VARCHAR(300),
    operator_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_requirement_progress_log_requirement
        FOREIGN KEY (requirement_id) REFERENCES gak_requirement (id) ON DELETE CASCADE
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
    business_trip_allowance_scene VARCHAR(32),
    business_trip_allowance_amount NUMERIC(8, 2) NOT NULL DEFAULT 0,
    business_trip_reimbursed BOOLEAN NOT NULL DEFAULT FALSE,
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
    dict_scope VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    status VARCHAR(20) NOT NULL,
    reference_apps_json TEXT,
    description VARCHAR(255),
    creator_user_id BIGINT,
    creator_name VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE gak_data_dictionary ADD COLUMN IF NOT EXISTS dict_scope VARCHAR(20);
UPDATE gak_data_dictionary SET dict_scope = 'PUBLIC' WHERE dict_scope IS NULL OR TRIM(dict_scope) = '';
ALTER TABLE gak_data_dictionary ALTER COLUMN dict_scope SET DEFAULT 'PUBLIC';

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
ALTER TABLE gak_work_log ADD COLUMN IF NOT EXISTS business_trip_allowance_scene VARCHAR(32);
ALTER TABLE gak_work_log ADD COLUMN IF NOT EXISTS business_trip_allowance_amount NUMERIC(8, 2) NOT NULL DEFAULT 0;
ALTER TABLE gak_work_log ADD COLUMN IF NOT EXISTS business_trip_reimbursed BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE gak_work_log SET business_trip_allowance_amount = 0 WHERE business_trip_allowance_amount IS NULL;
UPDATE gak_work_log SET business_trip_reimbursed = FALSE WHERE business_trip_reimbursed IS NULL;
ALTER TABLE gak_work_log DROP CONSTRAINT IF EXISTS uk_work_log_user_date;
CREATE UNIQUE INDEX IF NOT EXISTS uk_work_log_user_date_project
    ON gak_work_log (user_id, log_date, project_code)
    WHERE project_code IS NOT NULL;
ALTER TABLE IF EXISTS gak_system_app ALTER COLUMN route_path DROP NOT NULL;
ALTER TABLE IF EXISTS gak_app_audit_log ALTER COLUMN app_id DROP NOT NULL;
UPDATE gak_system_app SET data_source_mode = 'DEMO' WHERE data_source_mode IS NULL;

CREATE INDEX IF NOT EXISTS idx_work_log_user_date ON gak_work_log (user_id, log_date DESC);
CREATE INDEX IF NOT EXISTS idx_work_log_type_code ON gak_work_log_type (type_code);
CREATE INDEX IF NOT EXISTS idx_password_memo_owner_updated ON gak_password_memo (owner_user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_password_memo_owner_category_updated
    ON gak_password_memo (owner_user_id, category, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_password_memo_history_memo_started
    ON gak_password_memo_history (memo_id, usage_started_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_password_memo_history_owner
    ON gak_password_memo_history (owner_user_id, memo_id);
CREATE INDEX IF NOT EXISTS idx_wow_character_owner_sort ON gak_wow_character (owner_user_id, item_level DESC, mythic_score DESC);
CREATE INDEX IF NOT EXISTS idx_wow_character_owner_featured ON gak_wow_character (owner_user_id, is_featured, item_level DESC, mythic_score DESC);
CREATE INDEX IF NOT EXISTS idx_wow_mythic_run_character_dungeon ON gak_wow_character_mythic_run (character_id, dungeon_name);
CREATE INDEX IF NOT EXISTS idx_wow_mythic_run_owner_character ON gak_wow_character_mythic_run (owner_user_id, character_id, dungeon_name);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wow_mythic_run_character_dungeon ON gak_wow_character_mythic_run (character_id, dungeon_name);
CREATE INDEX IF NOT EXISTS idx_wow_weekly_vault_owner_character_week ON gak_wow_character_weekly_vault (owner_user_id, character_id, week_start_date DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wow_weekly_vault_character_week ON gak_wow_character_weekly_vault (character_id, week_start_date);
DROP INDEX IF EXISTS idx_wow_keybinding_owner_character;
DROP INDEX IF EXISTS uk_wow_keybinding_character_spec;
CREATE INDEX IF NOT EXISTS idx_wow_keybinding_owner_character ON gak_wow_character_keybinding (owner_user_id, character_id, binding_name);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wow_keybinding_character_name ON gak_wow_character_keybinding (character_id, LOWER(binding_name));
CREATE INDEX IF NOT EXISTS idx_personal_bill_owner_date ON gak_personal_bill (owner_user_id, bill_date DESC, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_personal_bill_owner_type_date ON gak_personal_bill (owner_user_id, bill_type, bill_date DESC);
CREATE INDEX IF NOT EXISTS idx_personal_budget_owner_year ON gak_personal_budget (owner_user_id, budget_year, category_name);
CREATE UNIQUE INDEX IF NOT EXISTS uk_personal_budget_owner_year_category ON gak_personal_budget (owner_user_id, budget_year, category_name);
CREATE INDEX IF NOT EXISTS idx_health_record_owner_measure_date ON gak_health_record (owner_user_id, measure_date DESC, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_health_visit_owner_visit_date ON gak_health_visit (owner_user_id, visit_date DESC, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_health_report_owner_exam_date ON gak_health_report (owner_user_id, exam_date DESC, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_health_report_owner_visit_id ON gak_health_report (owner_user_id, visit_id, exam_date DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_attachment_object_key ON gak_attachment (object_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_attachment_legacy_source ON gak_attachment (legacy_source_key)
    WHERE legacy_source_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_attachment_business_active
    ON gak_attachment (business_type, business_id, usage_type, status, sort_no, id);
CREATE INDEX IF NOT EXISTS idx_attachment_owner_status_created
    ON gak_attachment (owner_user_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_attachment_cleanup_deleted
    ON gak_attachment (status, deleted_at) WHERE status = 'DELETED';
CREATE INDEX IF NOT EXISTS idx_instrument_practice_take_owner_instrument_created
    ON gak_instrument_practice_take (owner_user_id, instrument_id, created_at, id);
CREATE INDEX IF NOT EXISTS idx_knowledge_entry_owner_updated_at ON gak_knowledge_entry (owner_user_id, updated_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_knowledge_entry_owner_category ON gak_knowledge_entry (owner_user_id, category_name, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_knowledge_entry_status_updated_at ON gak_knowledge_entry (status, updated_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_knowledge_entry_owner_status_updated_at ON gak_knowledge_entry (owner_user_id, status, updated_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_todo_item_owner_sort ON gak_todo_item (owner_user_id, status, important, due_date, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_todo_item_step_task_sort ON gak_todo_item_step (task_id, sort_no);
CREATE INDEX IF NOT EXISTS idx_requirement_status_updated ON gak_requirement (status, updated_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_requirement_creator_updated ON gak_requirement (creator_user_id, updated_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_requirement_app_status_updated
    ON gak_requirement (app_code, status, updated_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_requirement_progress_log_requirement_created
    ON gak_requirement_progress_log (requirement_id, created_at ASC, id ASC);
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
    fuel_time TIMESTAMP,
    odometer_km NUMERIC(10, 1) NOT NULL,
    fuel_volume NUMERIC(8, 2) NOT NULL,
    machine_unit_price NUMERIC(8, 3),
    total_amount NUMERIC(10, 2) NOT NULL,
    discount_amount NUMERIC(10, 2),
    discounted_amount NUMERIC(10, 2) NOT NULL,
    unit_price NUMERIC(8, 3) NOT NULL,
    fuel_type VARCHAR(16) NOT NULL,
    fill_type VARCHAR(16) NOT NULL,
    fuel_warning_light BOOLEAN NOT NULL DEFAULT FALSE,
    last_record_known BOOLEAN NOT NULL DEFAULT TRUE,
    station_name VARCHAR(128),
    note VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE gak_fuel_record ADD COLUMN IF NOT EXISTS fuel_time TIMESTAMP;
ALTER TABLE gak_fuel_record ADD COLUMN IF NOT EXISTS machine_unit_price NUMERIC(8, 3);
ALTER TABLE gak_fuel_record ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(10, 2);
ALTER TABLE gak_fuel_record ADD COLUMN IF NOT EXISTS fuel_warning_light BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE gak_fuel_record ADD COLUMN IF NOT EXISTS last_record_known BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS gak_fuel_vehicle (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    vehicle_name VARCHAR(64) NOT NULL,
    energy_type VARCHAR(16) NOT NULL,
    default_fuel_type VARCHAR(16) NOT NULL,
    default_vehicle BOOLEAN NOT NULL DEFAULT FALSE,
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_fuel_vehicle_owner_name
    ON gak_fuel_vehicle (owner_user_id, vehicle_name);
CREATE INDEX IF NOT EXISTS idx_fuel_vehicle_owner_default
    ON gak_fuel_vehicle (owner_user_id, default_vehicle DESC, updated_at DESC);
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
    (2008, 'APP_KNOWLEDGE_BASE', '经验库', '/knowledge-base', '知识沉淀', 'REAL', 'URL', NULL, '经验', '/app-icons/app-knowledge-base.png', 'PUBLIC_ASSET', 'app-knowledge-base.png', 'INTERNAL', 'NONE', TRUE, 80, '沉淀问题处理经验和通用操作手册。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2009, 'APP_SOFTWARE_REPO', '软件仓库', '/software-repo', '资源管理', 'DEMO', 'URL', NULL, '软件', '/app-icons/app-software-repo.png', 'PUBLIC_ASSET', 'app-software-repo.png', 'INTERNAL', 'NONE', TRUE, 90, '整理常用软件、版本与下载入口。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2010, 'APP_HEALTH_RECORD', '健康', '/health', '生活管理', 'REAL', 'URL', NULL, '健康', '/app-icons/app-health-record.png', 'PUBLIC_ASSET', 'app-health-record.png', 'CONFIDENTIAL', 'FIELD', TRUE, 100, '记录体征、就医与个人健康档案。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2011, 'APP_DATA_DICTIONARY', '数据字典', '/data-dictionary', '系统管理', 'REAL', 'URL', NULL, '字典', '/app-icons/app-data-dictionary.png', 'PUBLIC_ASSET', 'app-data-dictionary.png', 'INTERNAL', 'NONE', TRUE, 110, '维护系统可配置选项与字典项。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2012, 'APP_INSTRUMENT_PRACTICE', '随身乐器', '/instrument-practice', '音乐练习', 'REAL', 'URL', NULL, '乐器', '/app-icons/app-instrument-practice.webp', 'PUBLIC_ASSET', 'app-instrument-practice.webp', 'PUBLIC', 'NONE', TRUE, 120, '在手机上演奏古筝、吉他、乌克丽丽和钢琴，并使用节拍器与录制回放练习。', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
    5011, 'PASSWORD_MEMO_CATEGORY', '密码备忘录类别', 'ENABLED', '["密码备忘录"]', '密码备忘录使用场景类别',
    admin_user.id, admin_user.display_name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_user admin_user
WHERE admin_user.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary dictionary
    WHERE dictionary.dict_code = 'PASSWORD_MEMO_CATEGORY' AND dictionary.deleted = FALSE
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
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5102, 5001, 'USER_ROLE_TYPE', 'dev', '开发', 'DEV', 2, 'ENABLED', FALSE, '开发角色', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5001 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5001 AND item.item_code = 'dev' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5103, 5001, 'USER_ROLE_TYPE', 'user', '普通用户', 'USER', 3, 'ENABLED', TRUE, '默认角色', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5001 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5001 AND item.item_code = 'user' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5201, 5002, 'USER_STATUS', 'enabled', '启用', 'ENABLED', 1, 'ENABLED', TRUE, '默认启用状态', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5002 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5002 AND item.item_code = 'enabled' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5202, 5002, 'USER_STATUS', 'disabled', '禁用', 'DISABLED', 2, 'ENABLED', FALSE, '禁用状态', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5002 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5002 AND item.item_code = 'disabled' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;

UPDATE gak_data_dictionary_item
SET deleted = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE dictionary_id = 5003
  AND deleted = FALSE
  AND (
    item_code IN ('develop', 'meeting', 'test', 'business_trip')
    OR item_value IN ('DEVELOP', 'MEETING', 'TEST', 'BUSINESS_TRIP')
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
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5318, 5003, 'WORK_LOG_TYPE', 'overtime', '加班', 'OVERTIME', 2, 'ENABLED', FALSE, '加班工作记录', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5003 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5003 AND item.item_code = 'overtime' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5312, 5003, 'WORK_LOG_TYPE', 'leave', '请假', 'LEAVE', 2, 'ENABLED', FALSE, '请假记录', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5003 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5003 AND item.item_code = 'leave' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5316, 5003, 'WORK_LOG_TYPE', 'city_business_trip', '市内出差', 'CITY_BUSINESS_TRIP', 3, 'ENABLED', FALSE, '市内出差记录，补助 100 元', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5003 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5003 AND item.item_code = 'city_business_trip' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5317, 5003, 'WORK_LOG_TYPE', 'out_of_city_business_trip', '市外出差', 'OUT_OF_CITY_BUSINESS_TRIP', 4, 'ENABLED', FALSE, '市外出差记录，按往返/平时计算补助', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5003 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5003 AND item.item_code = 'out_of_city_business_trip' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5314, 5003, 'WORK_LOG_TYPE', 'sick_leave', '病假', 'SICK_LEAVE', 5, 'ENABLED', FALSE, '病假记录', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5003 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5003 AND item.item_code = 'sick_leave' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5315, 5003, 'WORK_LOG_TYPE', 'other', '其他', 'OTHER', 6, 'ENABLED', FALSE, '其他类型', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5003 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5003 AND item.item_code = 'other' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
UPDATE gak_data_dictionary_item
SET sort_no = CASE item_value
        WHEN 'NORMAL' THEN 1
        WHEN 'OVERTIME' THEN 2
        WHEN 'LEAVE' THEN 3
        WHEN 'CITY_BUSINESS_TRIP' THEN 4
        WHEN 'OUT_OF_CITY_BUSINESS_TRIP' THEN 5
        WHEN 'SICK_LEAVE' THEN 6
        WHEN 'OTHER' THEN 7
        ELSE sort_no
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE dictionary_id = 5003
  AND deleted = FALSE
  AND item_value IN ('NORMAL', 'OVERTIME', 'LEAVE', 'CITY_BUSINESS_TRIP', 'OUT_OF_CITY_BUSINESS_TRIP', 'SICK_LEAVE', 'OTHER');
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5401, 5004, 'WORK_LOG_PROJECT', 'gak', 'GAK', 'GAK', 1, 'ENABLED', TRUE, '默认项目', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5004 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5004 AND item.item_code = 'gak' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5402, 5004, 'WORK_LOG_PROJECT', 'client', '客户项目', 'CLIENT', 2, 'ENABLED', FALSE, '客户相关项目', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5004 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5004 AND item.item_code = 'client' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5403, 5004, 'WORK_LOG_PROJECT', 'ops', '运维支持', 'OPS', 3, 'ENABLED', FALSE, '运维与支撑事项', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5004 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5004 AND item.item_code = 'ops' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5451, 5007, 'WORK_LOG_LOCATION', 'sh_office', '上海办公室', '上海办公室', 1, 'ENABLED', TRUE, '默认办公地点', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5007 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5007 AND item.item_code = 'sh_office' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5452, 5007, 'WORK_LOG_LOCATION', 'sz_office', '深圳办公室', '深圳办公室', 2, 'ENABLED', FALSE, '深圳办公地点', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5007 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5007 AND item.item_code = 'sz_office' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5453, 5007, 'WORK_LOG_LOCATION', 'home', '居家', '居家', 3, 'ENABLED', FALSE, '居家办公', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5007 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5007 AND item.item_code = 'home' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5454, 5007, 'WORK_LOG_LOCATION', 'client_site', '客户现场', '客户现场', 4, 'ENABLED', FALSE, '客户办公地点', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5007 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5007 AND item.item_code = 'client_site' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5455, 5007, 'WORK_LOG_LOCATION', 'travel', '出差在途', '出差在途', 5, 'ENABLED', FALSE, '在途办公地点', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5007 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5007 AND item.item_code = 'travel' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5501, 5005, 'APP_SECURITY_LEVEL', 'public', '公开', 'PUBLIC', 1, 'ENABLED', TRUE, '公开级别', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5005 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5005 AND item.item_code = 'public' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5502, 5005, 'APP_SECURITY_LEVEL', 'internal', '内部', 'INTERNAL', 2, 'ENABLED', FALSE, '内部级别', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5005 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5005 AND item.item_code = 'internal' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5503, 5005, 'APP_SECURITY_LEVEL', 'confidential', '机密', 'CONFIDENTIAL', 3, 'ENABLED', FALSE, '机密级别', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5005 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5005 AND item.item_code = 'confidential' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5601, 5006, 'APP_ENCRYPTION_MODE', 'none', '无加密', 'NONE', 1, 'ENABLED', TRUE, '默认加密方式', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5006 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5006 AND item.item_code = 'none' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5602, 5006, 'APP_ENCRYPTION_MODE', 'field', '字段加密', 'FIELD', 2, 'ENABLED', FALSE, '字段级加密', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5006 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5006 AND item.item_code = 'field' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5603, 5006, 'APP_ENCRYPTION_MODE', 'end_to_end', '端到端加密', 'END_TO_END', 3, 'ENABLED', FALSE, '端到端加密方式', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5006 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5006 AND item.item_code = 'end_to_end' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5701, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'food', '餐饮', '餐饮', 1, 'ENABLED', TRUE, '日常餐饮支出', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'food' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5702, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'transport', '交通', '交通', 2, 'ENABLED', FALSE, '公共交通与出行', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'transport' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5703, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'housing', '居家', '居家', 3, 'ENABLED', FALSE, '居家日用与家清', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'housing' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5704, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'entertainment', '娱乐', '娱乐', 4, 'ENABLED', FALSE, '游戏影音与聚会', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'entertainment' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5705, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'digital', '数码', '数码', 5, 'ENABLED', FALSE, '电子设备与配件', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'digital' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5706, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'learning', '学习', '学习', 6, 'ENABLED', FALSE, '课程书籍与培训', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'learning' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5707, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'travel', '旅行', '旅行', 7, 'ENABLED', FALSE, '差旅与旅游相关', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'travel' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5708, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'salary', '工资', '工资', 8, 'ENABLED', FALSE, '固定工资收入', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'salary' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5709, 5008, 'PERSONAL_BILLS_BILL_CATEGORY', 'bonus', '奖金', '奖金', 9, 'ENABLED', FALSE, '绩效与奖金收入', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5008 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5008 AND item.item_code = 'bonus' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5711, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'food', '餐饮', '餐饮', 1, 'ENABLED', TRUE, '餐饮预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'food' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5712, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'transport', '交通', '交通', 2, 'ENABLED', FALSE, '交通预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'transport' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5713, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'housing', '居家', '居家', 3, 'ENABLED', FALSE, '居家预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'housing' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5714, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'entertainment', '娱乐', '娱乐', 4, 'ENABLED', FALSE, '娱乐预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'entertainment' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5715, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'digital', '数码', '数码', 5, 'ENABLED', FALSE, '数码预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'digital' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5716, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'learning', '学习', '学习', 6, 'ENABLED', FALSE, '学习预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'learning' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5717, 5009, 'PERSONAL_BILLS_BUDGET_CATEGORY', 'travel', '旅行', '旅行', 7, 'ENABLED', FALSE, '旅行预算', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5009 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5009 AND item.item_code = 'travel' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5721, 5010, 'PERSONAL_BILLS_PAYMENT_METHOD', 'alipay', '支付宝', '支付宝', 1, 'ENABLED', TRUE, '支付宝支付', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5010 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5010 AND item.item_code = 'alipay' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5722, 5010, 'PERSONAL_BILLS_PAYMENT_METHOD', 'wechat_pay', '微信支付', '微信支付', 2, 'ENABLED', FALSE, '微信支付', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5010 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5010 AND item.item_code = 'wechat_pay' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5723, 5010, 'PERSONAL_BILLS_PAYMENT_METHOD', 'bank_card', '银行卡', '银行卡', 3, 'ENABLED', FALSE, '银行卡刷卡或线上支付', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5010 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5010 AND item.item_code = 'bank_card' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5724, 5010, 'PERSONAL_BILLS_PAYMENT_METHOD', 'bank_transfer', '银行转账', '银行转账', 4, 'ENABLED', FALSE, '工资或转账入账', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5010 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5010 AND item.item_code = 'bank_transfer' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;
INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5725, 5010, 'PERSONAL_BILLS_PAYMENT_METHOD', 'cash', '现金', '现金', 5, 'ENABLED', FALSE, '线下现金支付', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE EXISTS (SELECT 1 FROM gak_data_dictionary WHERE id = 5010 AND deleted = FALSE)
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = 5010 AND item.item_code = 'cash' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5731, dictionary.id, dictionary.dict_code, 'life', '生活', '生活', 1, 'ENABLED', TRUE,
       '个人生活场景账号', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_data_dictionary dictionary
WHERE dictionary.dict_code = 'PASSWORD_MEMO_CATEGORY' AND dictionary.deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = dictionary.id AND item.item_code = 'life' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5732, dictionary.id, dictionary.dict_code, 'work', '工作', '工作', 2, 'ENABLED', FALSE,
       '工作办公场景账号', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_data_dictionary dictionary
WHERE dictionary.dict_code = 'PASSWORD_MEMO_CATEGORY' AND dictionary.deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = dictionary.id AND item.item_code = 'work' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT 5733, dictionary.id, dictionary.dict_code, 'other', '其他', '其他', 99, 'ENABLED', FALSE,
       '未归入生活或工作的其他场景', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_data_dictionary dictionary
WHERE dictionary.dict_code = 'PASSWORD_MEMO_CATEGORY' AND dictionary.deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_item item
    WHERE item.dictionary_id = dictionary.id AND item.item_code = 'other' AND item.deleted = FALSE
  )
ON CONFLICT (id) DO NOTHING;


INSERT INTO gak_data_dictionary_usage (
    id, dict_code, dictionary_id, app_code, app_name, module_code, module_name,
    biz_field_code, biz_field_name, usage_type, value_mode, allow_multiple, required_flag,
    status, usage_count, last_used_at, remark, created_at, updated_at
)
SELECT
    7001001, 'USER_ROLE_TYPE', dictionary.id, 'APP_USER_MANAGEMENT', 'User Management', 'SYSTEM_USER', 'System User',
    'roleCode', 'Role Code', 'FORM_FIELD', 'ITEM_VALUE', FALSE, TRUE,
    'ENABLED', 0, NULL, 'schema init', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM gak_data_dictionary dictionary
WHERE dictionary.dict_code = 'USER_ROLE_TYPE'
  AND dictionary.deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_usage usage
    WHERE usage.app_code = 'APP_USER_MANAGEMENT'
      AND usage.module_code = 'SYSTEM_USER'
      AND usage.biz_field_code = 'roleCode'
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO gak_data_dictionary_usage (
    id, dict_code, dictionary_id, app_code, app_name, module_code, module_name,
    biz_field_code, biz_field_name, usage_type, value_mode, allow_multiple, required_flag,
    status, usage_count, last_used_at, remark, created_at, updated_at
)
SELECT
    7001002, 'USER_STATUS', dictionary.id, 'APP_USER_MANAGEMENT', 'User Management', 'SYSTEM_USER', 'System User',
    'status', 'User Status', 'FORM_FIELD', 'ITEM_VALUE', FALSE, TRUE,
    'ENABLED', 0, NULL, 'schema init', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM gak_data_dictionary dictionary
WHERE dictionary.dict_code = 'USER_STATUS'
  AND dictionary.deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_usage usage
    WHERE usage.app_code = 'APP_USER_MANAGEMENT'
      AND usage.module_code = 'SYSTEM_USER'
      AND usage.biz_field_code = 'status'
  )
ON CONFLICT (id) DO NOTHING;


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
    'projectCode', '所属项目', 'FORM_FIELD', 'ITEM_VALUE', FALSE, TRUE,
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
UPDATE gak_data_dictionary_usage
SET required_flag = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE app_code = 'APP_WORK_LOG'
  AND module_code = 'WORK_LOG'
  AND biz_field_code = 'projectCode';

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

INSERT INTO gak_data_dictionary_usage (
    id, dict_code, dictionary_id, app_code, app_name, module_code, module_name,
    biz_field_code, biz_field_name, usage_type, value_mode, allow_multiple, required_flag,
    status, usage_count, last_used_at, remark, created_at, updated_at
)
SELECT
    7001044, 'PASSWORD_MEMO_CATEGORY', dictionary.id, 'APP_PASSWORD_MEMO', '密码备忘录', 'PASSWORD_MEMO', '密码备忘录',
    'category', '类别', 'FORM_FIELD', 'ITEM_VALUE', FALSE, TRUE,
    'ENABLED', 0, NULL, 'schema init', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM gak_data_dictionary dictionary
WHERE dictionary.dict_code = 'PASSWORD_MEMO_CATEGORY'
  AND dictionary.deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary_usage usage
    WHERE usage.app_code = 'APP_PASSWORD_MEMO'
      AND usage.module_code = 'PASSWORD_MEMO'
      AND usage.biz_field_code = 'category'
  )
ON CONFLICT (id) DO NOTHING;

CREATE TEMP TABLE seed_wow_dictionaries (
    id BIGINT PRIMARY KEY,
    dict_code VARCHAR(64) NOT NULL,
    dict_name VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reference_apps_json TEXT,
    description VARCHAR(255),
    creator_user_id BIGINT,
    creator_name VARCHAR(64)
);

INSERT INTO seed_wow_dictionaries (
    id, dict_code, dict_name, status, reference_apps_json, description, creator_user_id, creator_name
)
VALUES
    (6013, 'WOW_FACTION', '魔兽阵营', 'ENABLED', '["WoW角色统计"]', 'WoW 阵营选项', 900000000000000001, '系统管理员'),
    (6014, 'WOW_CLASS_NAME', '魔兽职业', 'ENABLED', '["WoW角色统计"]', 'WoW 职业选项', 900000000000000001, '系统管理员'),
    (6015, 'WOW_MYTHIC_DUNGEON', '大秘境副本', 'ENABLED', '["WoW角色统计"]', 'WoW 大秘境副本选项', 900000000000000001, '系统管理员'),
    (2035628832674516994, 'WOW_CHARACTER_RACE', '魔兽角色种族', 'ENABLED', '["WoW角色统计"]', 'WoW 可玩种族选项，含阵营与职业限制元数据', 900000000000000001, '系统管理员'),
    (6018, 'WOW_CLASS_SPEC', '魔兽职业专精', 'ENABLED', '["WoW角色统计"]', 'WoW 职业专精选项，itemValue 使用唯一 code', 900000000000000001, '系统管理员'),
    (6019, 'WOW_PRIMARY_PROFESSION', '魔兽主专业', 'ENABLED', '["WoW角色统计"]', 'WoW 主专业选项', 900000000000000001, '系统管理员');

UPDATE gak_data_dictionary target
SET dict_name = source.dict_name,
    status = source.status,
    reference_apps_json = source.reference_apps_json,
    description = source.description,
    creator_user_id = source.creator_user_id,
    creator_name = source.creator_name,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE
FROM seed_wow_dictionaries source
WHERE target.dict_code = source.dict_code;

INSERT INTO gak_data_dictionary (
    id, dict_code, dict_name, status, reference_apps_json, description,
    creator_user_id, creator_name, created_at, updated_at, deleted
)
SELECT
    source.id, source.dict_code, source.dict_name, source.status, source.reference_apps_json,
    source.description, source.creator_user_id, source.creator_name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM seed_wow_dictionaries source
WHERE NOT EXISTS (
    SELECT 1 FROM gak_data_dictionary existing WHERE existing.dict_code = source.dict_code
);

CREATE TEMP TABLE seed_wow_dictionary_items (
    id BIGINT PRIMARY KEY,
    dict_code VARCHAR(64) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_label VARCHAR(64) NOT NULL,
    item_value VARCHAR(64) NOT NULL,
    sort_no INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    is_default BOOLEAN NOT NULL,
    description VARCHAR(255),
    extra_json TEXT
);

INSERT INTO seed_wow_dictionary_items (
    id, dict_code, item_code, item_label, item_value, sort_no, status, is_default, description, extra_json
)
VALUES
    (6013001, 'WOW_FACTION', 'alliance', '联盟', 'ALLIANCE', 1, 'ENABLED', TRUE, '联盟阵营', NULL),
    (6013002, 'WOW_FACTION', 'horde', '部落', 'HORDE', 2, 'ENABLED', FALSE, '部落阵营', NULL),
    (6014001, 'WOW_CLASS_NAME', 'death_knight', '死亡骑士', '死亡骑士', 1, 'ENABLED', FALSE, '死亡骑士', '{"color":"#C41F3B","textColor":"#ffffff"}'),
    (6014002, 'WOW_CLASS_NAME', 'demon_hunter', '恶魔猎手', '恶魔猎手', 2, 'ENABLED', FALSE, '恶魔猎手', '{"color":"#A330C9","textColor":"#ffffff"}'),
    (6014003, 'WOW_CLASS_NAME', 'druid', '德鲁伊', '德鲁伊', 3, 'ENABLED', FALSE, '德鲁伊', '{"color":"#FF7D0A","textColor":"#1f1607"}'),
    (6014004, 'WOW_CLASS_NAME', 'evoker', '唤魔师', '唤魔师', 4, 'ENABLED', FALSE, '唤魔师', '{"color":"#33937F","textColor":"#ffffff"}'),
    (6014005, 'WOW_CLASS_NAME', 'hunter', '猎人', '猎人', 5, 'ENABLED', FALSE, '猎人', '{"color":"#ABD473","textColor":"#1f2910"}'),
    (6014006, 'WOW_CLASS_NAME', 'mage', '法师', '法师', 6, 'ENABLED', FALSE, '法师', '{"color":"#69CCF0","textColor":"#07202f"}'),
    (6014007, 'WOW_CLASS_NAME', 'monk', '武僧', '武僧', 7, 'ENABLED', FALSE, '武僧', '{"color":"#00FF96","textColor":"#062119"}'),
    (6014008, 'WOW_CLASS_NAME', 'paladin', '圣骑士', '圣骑士', 8, 'ENABLED', FALSE, '圣骑士', '{"color":"#F58CBA","textColor":"#2d0f1d"}'),
    (6014009, 'WOW_CLASS_NAME', 'priest', '牧师', '牧师', 9, 'ENABLED', FALSE, '牧师', '{"color":"#F4F4F4","textColor":"#111111"}'),
    (6014010, 'WOW_CLASS_NAME', 'rogue', '潜行者', '潜行者', 10, 'ENABLED', FALSE, '潜行者', '{"color":"#FFF569","textColor":"#312b07"}'),
    (6014011, 'WOW_CLASS_NAME', 'shaman', '萨满', '萨满', 11, 'ENABLED', FALSE, '萨满', '{"color":"#0070DE","textColor":"#ffffff"}'),
    (6014012, 'WOW_CLASS_NAME', 'warlock', '术士', '术士', 12, 'ENABLED', FALSE, '术士', '{"color":"#9482C9","textColor":"#100d1d"}'),
    (6014013, 'WOW_CLASS_NAME', 'warrior', '战士', '战士', 13, 'ENABLED', TRUE, '战士', '{"color":"#C79C6E","textColor":"#23170d"}'),
    (6015001, 'WOW_MYTHIC_DUNGEON', 'magisters_terrace', '魔导师平台', '魔导师平台', 1, 'ENABLED', FALSE, '赛季副本', NULL),
    (6015002, 'WOW_MYTHIC_DUNGEON', 'myssara_caverns', '迈萨拉洞窟', '迈萨拉洞窟', 2, 'ENABLED', FALSE, '赛季副本', NULL),
    (6015003, 'WOW_MYTHIC_DUNGEON', 'the_nexus_sinnus', '节点希纳斯', '节点希纳斯', 3, 'ENABLED', FALSE, '赛季副本', NULL),
    (6015004, 'WOW_MYTHIC_DUNGEON', 'windrunner_spire', '风行者之塔', '风行者之塔', 4, 'ENABLED', FALSE, '赛季副本', NULL),
    (6015005, 'WOW_MYTHIC_DUNGEON', 'aegis_academy', '艾杰斯亚学院', '艾杰斯亚学院', 5, 'ENABLED', FALSE, '赛季副本', NULL),
    (6015006, 'WOW_MYTHIC_DUNGEON', 'saron_mine', '萨隆矿坑', '萨隆矿坑', 6, 'ENABLED', FALSE, '赛季副本', NULL),
    (6015007, 'WOW_MYTHIC_DUNGEON', 'seat_of_the_triumvirate', '执政团之座', '执政团之座', 7, 'ENABLED', FALSE, '赛季副本', NULL),
    (6015008, 'WOW_MYTHIC_DUNGEON', 'skyreach', '通天峰', '通天峰', 8, 'ENABLED', FALSE, '赛季副本', NULL),
    (6018001, 'WOW_CHARACTER_RACE', 'human', '人类', '人类', 1, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE"],"allowedClassCodes":["death_knight","hunter","mage","monk","paladin","priest","rogue","warlock","warrior"]}'),
    (6018002, 'WOW_CHARACTER_RACE', 'dwarf', '矮人', '矮人', 2, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE"],"allowedClassCodes":["death_knight","hunter","mage","monk","paladin","priest","rogue","shaman","warlock","warrior"]}'),
    (6018003, 'WOW_CHARACTER_RACE', 'night_elf', '暗夜精灵', '暗夜精灵', 3, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE"],"allowedClassCodes":["death_knight","demon_hunter","druid","hunter","mage","monk","priest","rogue","warlock","warrior"]}'),
    (6018004, 'WOW_CHARACTER_RACE', 'gnome', '侏儒', '侏儒', 4, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE"],"allowedClassCodes":["death_knight","hunter","mage","monk","priest","rogue","warlock","warrior"]}'),
    (6018005, 'WOW_CHARACTER_RACE', 'draenei', '德莱尼', '德莱尼', 5, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE"],"allowedClassCodes":["death_knight","hunter","mage","monk","paladin","priest","rogue","shaman","warlock","warrior"]}'),
    (6018006, 'WOW_CHARACTER_RACE', 'worgen', '狼人', '狼人', 6, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE"],"allowedClassCodes":["death_knight","druid","hunter","mage","monk","priest","rogue","warlock","warrior"]}'),
    (6018007, 'WOW_CHARACTER_RACE', 'pandaren', '熊猫人', '熊猫人', 7, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE","HORDE"],"allowedClassCodes":["death_knight","hunter","mage","monk","priest","rogue","shaman","warlock","warrior"]}'),
    (6018008, 'WOW_CHARACTER_RACE', 'dracthyr', '龙希尔', '龙希尔', 8, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE","HORDE"],"allowedClassCodes":["evoker","hunter","mage","priest","rogue","warlock","warrior"]}'),
    (6018009, 'WOW_CHARACTER_RACE', 'void_elf', '虚空精灵', '虚空精灵', 9, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE"],"allowedClassCodes":["death_knight","demon_hunter","hunter","mage","monk","priest","rogue","warlock","warrior"]}'),
    (6018010, 'WOW_CHARACTER_RACE', 'lightforged_draenei', '光铸德莱尼', '光铸德莱尼', 10, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE"],"allowedClassCodes":["death_knight","hunter","mage","monk","paladin","priest","rogue","warlock","warrior"]}'),
    (6018011, 'WOW_CHARACTER_RACE', 'dark_iron_dwarf', '黑铁矮人', '黑铁矮人', 11, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE"],"allowedClassCodes":["death_knight","hunter","mage","monk","paladin","priest","rogue","shaman","warlock","warrior"]}'),
    (6018012, 'WOW_CHARACTER_RACE', 'kul_tiran', '库尔提拉斯人', '库尔提拉斯人', 12, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE"],"allowedClassCodes":["death_knight","druid","hunter","mage","monk","priest","rogue","shaman","warlock","warrior"]}'),
    (6018013, 'WOW_CHARACTER_RACE', 'mechagnome', '机械侏儒', '机械侏儒', 13, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE"],"allowedClassCodes":["death_knight","hunter","mage","monk","priest","rogue","warlock","warrior"]}'),
    (6018014, 'WOW_CHARACTER_RACE', 'earthen', '土灵', '土灵', 14, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE","HORDE"],"allowedClassCodes":["hunter","mage","monk","paladin","priest","rogue","shaman","warlock","warrior"]}'),
    (6018015, 'WOW_CHARACTER_RACE', 'orc', '兽人', '兽人', 15, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["HORDE"],"allowedClassCodes":["death_knight","hunter","mage","monk","priest","rogue","shaman","warlock","warrior"]}'),
    (6018016, 'WOW_CHARACTER_RACE', 'undead', '亡灵', '亡灵', 16, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["HORDE"],"allowedClassCodes":["death_knight","hunter","mage","monk","priest","rogue","warlock","warrior"]}'),
    (6018017, 'WOW_CHARACTER_RACE', 'tauren', '牛头人', '牛头人', 17, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["HORDE"],"allowedClassCodes":["death_knight","druid","hunter","mage","monk","paladin","priest","rogue","shaman","warlock","warrior"]}'),
    (6018018, 'WOW_CHARACTER_RACE', 'troll', '巨魔', '巨魔', 18, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["HORDE"],"allowedClassCodes":["death_knight","druid","hunter","mage","monk","priest","rogue","shaman","warlock","warrior"]}'),
    (6018019, 'WOW_CHARACTER_RACE', 'blood_elf', '血精灵', '血精灵', 19, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["HORDE"],"allowedClassCodes":["death_knight","demon_hunter","hunter","mage","monk","paladin","priest","rogue","warlock","warrior"]}'),
    (6018020, 'WOW_CHARACTER_RACE', 'goblin', '地精', '地精', 20, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["HORDE"],"allowedClassCodes":["death_knight","hunter","mage","monk","priest","rogue","shaman","warlock","warrior"]}'),
    (6018021, 'WOW_CHARACTER_RACE', 'nightborne', '夜之子', '夜之子', 21, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["HORDE"],"allowedClassCodes":["death_knight","hunter","mage","monk","priest","rogue","warlock","warrior"]}'),
    (6018022, 'WOW_CHARACTER_RACE', 'highmountain_tauren', '至高岭牛头人', '至高岭牛头人', 22, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["HORDE"],"allowedClassCodes":["death_knight","druid","hunter","mage","monk","priest","rogue","shaman","warlock","warrior"]}'),
    (6018023, 'WOW_CHARACTER_RACE', 'maghar_orc', '玛格汉兽人', '玛格汉兽人', 23, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["HORDE"],"allowedClassCodes":["death_knight","hunter","mage","monk","priest","rogue","shaman","warlock","warrior"]}'),
    (6018024, 'WOW_CHARACTER_RACE', 'zandalari_troll', '赞达拉巨魔', '赞达拉巨魔', 24, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["HORDE"],"allowedClassCodes":["death_knight","druid","hunter","mage","monk","paladin","priest","rogue","shaman","warlock","warrior"]}'),
    (6018025, 'WOW_CHARACTER_RACE', 'vulpera', '狐人', '狐人', 25, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["HORDE"],"allowedClassCodes":["death_knight","hunter","mage","monk","priest","rogue","shaman","warlock","warrior"]}'),
    (6018026, 'WOW_CHARACTER_RACE', 'haranir', '哈拉尼尔', '哈拉尼尔', 26, 'ENABLED', FALSE, 'WoW 可玩种族', '{"factions":["ALLIANCE","HORDE"],"allowedClassCodes":["druid","hunter","mage","monk","priest","rogue","shaman","warlock","warrior"]}'),
    (6019001, 'WOW_CLASS_SPEC', 'blood_death_knight', '鲜血', 'blood_death_knight', 1, 'ENABLED', TRUE, 'WoW 职业专精', '{"classCode":"death_knight"}'),
    (6019002, 'WOW_CLASS_SPEC', 'frost_death_knight', '冰霜', 'frost_death_knight', 2, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"death_knight"}'),
    (6019003, 'WOW_CLASS_SPEC', 'unholy_death_knight', '邪恶', 'unholy_death_knight', 3, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"death_knight"}'),
    (6019004, 'WOW_CLASS_SPEC', 'devourer', '噬灭', 'devourer', 4, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"demon_hunter"}'),
    (6019005, 'WOW_CLASS_SPEC', 'havoc', '浩劫', 'havoc', 5, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"demon_hunter"}'),
    (6019006, 'WOW_CLASS_SPEC', 'vengeance', '复仇', 'vengeance', 6, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"demon_hunter"}'),
    (6019007, 'WOW_CLASS_SPEC', 'balance', '平衡', 'balance', 7, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"druid"}'),
    (6019008, 'WOW_CLASS_SPEC', 'feral', '野性', 'feral', 8, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"druid"}'),
    (6019009, 'WOW_CLASS_SPEC', 'guardian', '守护', 'guardian', 9, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"druid"}'),
    (6019010, 'WOW_CLASS_SPEC', 'restoration_druid', '恢复', 'restoration_druid', 10, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"druid"}'),
    (6019011, 'WOW_CLASS_SPEC', 'augmentation', '增辉', 'augmentation', 11, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"evoker"}'),
    (6019012, 'WOW_CLASS_SPEC', 'devastation', '湮灭', 'devastation', 12, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"evoker"}'),
    (6019013, 'WOW_CLASS_SPEC', 'preservation', '恩护', 'preservation', 13, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"evoker"}'),
    (6019014, 'WOW_CLASS_SPEC', 'beast_mastery', '野兽控制', 'beast_mastery', 14, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"hunter"}'),
    (6019015, 'WOW_CLASS_SPEC', 'marksmanship', '射击', 'marksmanship', 15, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"hunter"}'),
    (6019016, 'WOW_CLASS_SPEC', 'survival', '生存', 'survival', 16, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"hunter"}'),
    (6019017, 'WOW_CLASS_SPEC', 'arcane', '奥术', 'arcane', 17, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"mage"}'),
    (6019018, 'WOW_CLASS_SPEC', 'fire', '火焰', 'fire', 18, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"mage"}'),
    (6019019, 'WOW_CLASS_SPEC', 'frost_mage', '冰霜', 'frost_mage', 19, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"mage"}'),
    (6019020, 'WOW_CLASS_SPEC', 'brewmaster', '酒仙', 'brewmaster', 20, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"monk"}'),
    (6019021, 'WOW_CLASS_SPEC', 'mistweaver', '织雾', 'mistweaver', 21, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"monk"}'),
    (6019022, 'WOW_CLASS_SPEC', 'windwalker', '踏风', 'windwalker', 22, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"monk"}'),
    (6019023, 'WOW_CLASS_SPEC', 'holy_paladin', '神圣', 'holy_paladin', 23, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"paladin"}'),
    (6019024, 'WOW_CLASS_SPEC', 'protection_paladin', '防护', 'protection_paladin', 24, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"paladin"}'),
    (6019025, 'WOW_CLASS_SPEC', 'retribution', '惩戒', 'retribution', 25, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"paladin"}'),
    (6019026, 'WOW_CLASS_SPEC', 'discipline', '戒律', 'discipline', 26, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"priest"}'),
    (6019027, 'WOW_CLASS_SPEC', 'holy_priest', '神圣', 'holy_priest', 27, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"priest"}'),
    (6019028, 'WOW_CLASS_SPEC', 'shadow', '暗影', 'shadow', 28, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"priest"}'),
    (6019029, 'WOW_CLASS_SPEC', 'assassination', '奇袭', 'assassination', 29, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"rogue"}'),
    (6019030, 'WOW_CLASS_SPEC', 'outlaw', '狂徒', 'outlaw', 30, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"rogue"}'),
    (6019031, 'WOW_CLASS_SPEC', 'subtlety', '敏锐', 'subtlety', 31, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"rogue"}'),
    (6019032, 'WOW_CLASS_SPEC', 'elemental', '元素', 'elemental', 32, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"shaman"}'),
    (6019033, 'WOW_CLASS_SPEC', 'enhancement', '增强', 'enhancement', 33, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"shaman"}'),
    (6019034, 'WOW_CLASS_SPEC', 'restoration_shaman', '恢复', 'restoration_shaman', 34, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"shaman"}'),
    (6019035, 'WOW_CLASS_SPEC', 'affliction', '痛苦', 'affliction', 35, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"warlock"}'),
    (6019036, 'WOW_CLASS_SPEC', 'demonology', '恶魔学识', 'demonology', 36, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"warlock"}'),
    (6019037, 'WOW_CLASS_SPEC', 'destruction', '毁灭', 'destruction', 37, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"warlock"}'),
    (6019038, 'WOW_CLASS_SPEC', 'arms', '武器', 'arms', 38, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"warrior"}'),
    (6019039, 'WOW_CLASS_SPEC', 'fury', '狂怒', 'fury', 39, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"warrior"}'),
    (6019040, 'WOW_CLASS_SPEC', 'protection_warrior', '防护', 'protection_warrior', 40, 'ENABLED', FALSE, 'WoW 职业专精', '{"classCode":"warrior"}'),
    (6019101, 'WOW_PRIMARY_PROFESSION', 'alchemy', '炼金术', '炼金术', 1, 'ENABLED', TRUE, 'WoW 主专业', NULL),
    (6019102, 'WOW_PRIMARY_PROFESSION', 'blacksmithing', '锻造', '锻造', 2, 'ENABLED', FALSE, 'WoW 主专业', NULL),
    (6019103, 'WOW_PRIMARY_PROFESSION', 'enchanting', '附魔', '附魔', 3, 'ENABLED', FALSE, 'WoW 主专业', NULL),
    (6019104, 'WOW_PRIMARY_PROFESSION', 'engineering', '工程学', '工程学', 4, 'ENABLED', FALSE, 'WoW 主专业', NULL),
    (6019105, 'WOW_PRIMARY_PROFESSION', 'herbalism', '草药学', '草药学', 5, 'ENABLED', FALSE, 'WoW 主专业', NULL),
    (6019106, 'WOW_PRIMARY_PROFESSION', 'inscription', '铭文', '铭文', 6, 'ENABLED', FALSE, 'WoW 主专业', NULL),
    (6019107, 'WOW_PRIMARY_PROFESSION', 'jewelcrafting', '珠宝加工', '珠宝加工', 7, 'ENABLED', FALSE, 'WoW 主专业', NULL),
    (6019108, 'WOW_PRIMARY_PROFESSION', 'leatherworking', '制皮', '制皮', 8, 'ENABLED', FALSE, 'WoW 主专业', NULL),
    (6019109, 'WOW_PRIMARY_PROFESSION', 'mining', '采矿', '采矿', 9, 'ENABLED', FALSE, 'WoW 主专业', NULL),
    (6019110, 'WOW_PRIMARY_PROFESSION', 'skinning', '剥皮', '剥皮', 10, 'ENABLED', FALSE, 'WoW 主专业', NULL),
    (6019111, 'WOW_PRIMARY_PROFESSION', 'tailoring', '裁缝', '裁缝', 11, 'ENABLED', FALSE, 'WoW 主专业', NULL);

CREATE TEMP TABLE existing_wow_item_targets AS
SELECT DISTINCT ON (target.dict_code, target.item_code)
    target.id AS target_id,
    target.dict_code,
    target.item_code
FROM gak_data_dictionary_item target
JOIN seed_wow_dictionary_items source
    ON source.dict_code = target.dict_code
   AND source.item_code = target.item_code
ORDER BY target.dict_code, target.item_code, target.deleted ASC, target.id ASC;

UPDATE gak_data_dictionary_item target
SET item_label = source.item_label,
    item_value = source.item_value,
    sort_no = source.sort_no,
    status = source.status,
    is_default = source.is_default,
    description = source.description,
    extra_json = source.extra_json,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE
FROM seed_wow_dictionary_items source
JOIN existing_wow_item_targets keep
    ON keep.dict_code = source.dict_code
   AND keep.item_code = source.item_code
WHERE target.id = keep.target_id;

INSERT INTO gak_data_dictionary_item (
    id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status,
    is_default, description, extra_json, created_at, updated_at, deleted
)
SELECT
    source.id, dictionary.id, source.dict_code, source.item_code, source.item_label, source.item_value,
    source.sort_no, source.status, source.is_default, source.description, source.extra_json,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM seed_wow_dictionary_items source
JOIN gak_data_dictionary dictionary ON dictionary.dict_code = source.dict_code
WHERE NOT EXISTS (
    SELECT 1
    FROM existing_wow_item_targets keep
    WHERE keep.dict_code = source.dict_code
      AND keep.item_code = source.item_code
);

UPDATE gak_data_dictionary_item target
SET deleted = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE target.dict_code IN ('WOW_FACTION', 'WOW_CLASS_NAME', 'WOW_MYTHIC_DUNGEON', 'WOW_CHARACTER_RACE', 'WOW_CLASS_SPEC', 'WOW_PRIMARY_PROFESSION')
  AND NOT EXISTS (
    SELECT 1
    FROM seed_wow_dictionary_items source
    LEFT JOIN existing_wow_item_targets keep
        ON keep.dict_code = source.dict_code
       AND keep.item_code = source.item_code
    WHERE target.dict_code = source.dict_code
      AND target.item_code = source.item_code
      AND target.id = COALESCE(keep.target_id, source.id)
  );

CREATE TEMP TABLE seed_wow_dictionary_usage (
    id BIGINT PRIMARY KEY,
    dict_code VARCHAR(64) NOT NULL,
    app_code VARCHAR(64) NOT NULL,
    app_name VARCHAR(64),
    module_code VARCHAR(64) NOT NULL,
    module_name VARCHAR(64),
    biz_field_code VARCHAR(64) NOT NULL,
    biz_field_name VARCHAR(64),
    usage_type VARCHAR(32) NOT NULL,
    value_mode VARCHAR(32) NOT NULL,
    allow_multiple BOOLEAN NOT NULL,
    required_flag BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    remark VARCHAR(255)
);

INSERT INTO seed_wow_dictionary_usage (
    id, dict_code, app_code, app_name, module_code, module_name, biz_field_code, biz_field_name,
    usage_type, value_mode, allow_multiple, required_flag, status, remark
)
VALUES
    (7001022, 'WOW_FACTION', 'APP_WOW_CHARACTER', 'WoW角色统计', 'WOW_CHARACTER', 'WoW角色', 'faction', '阵营', 'VALUE_VALIDATION', 'ITEM_VALUE', FALSE, TRUE, 'ENABLED', 'WoW 阵营字段'),
    (7001023, 'WOW_CLASS_NAME', 'APP_WOW_CHARACTER', 'WoW角色统计', 'WOW_CHARACTER', 'WoW角色', 'className', '职业', 'VALUE_VALIDATION', 'ITEM_VALUE', FALSE, TRUE, 'ENABLED', '当前类名字段存中文标签，使用 itemValue 校验'),
    (7001024, 'WOW_MYTHIC_DUNGEON', 'APP_WOW_CHARACTER', 'WoW角色统计', 'WOW_CHARACTER', 'WoW角色', 'mythicDungeonName', '大秘境副本', 'VALUE_VALIDATION', 'ITEM_VALUE', FALSE, FALSE, 'ENABLED', '大秘境副本名称字段'),
    (7001026, 'WOW_CHARACTER_RACE', 'APP_WOW_CHARACTER', 'WoW角色统计', 'WOW_CHARACTER', 'WoW角色', 'raceName', '种族', 'VALUE_VALIDATION', 'ITEM_VALUE', FALSE, TRUE, 'ENABLED', 'WoW 种族字段'),
    (7001027, 'WOW_CLASS_SPEC', 'APP_WOW_CHARACTER', 'WoW角色统计', 'WOW_CHARACTER', 'WoW角色', 'specName', '专精', 'VALUE_VALIDATION', 'ITEM_VALUE', FALSE, TRUE, 'ENABLED', 'WoW 专精选项'),
    (7001028, 'WOW_PRIMARY_PROFESSION', 'APP_WOW_CHARACTER', 'WoW角色统计', 'WOW_CHARACTER', 'WoW角色', 'professionPrimary', '专业1', 'VALUE_VALIDATION', 'ITEM_VALUE', FALSE, FALSE, 'ENABLED', 'WoW 主专业字段'),
    (7001029, 'WOW_PRIMARY_PROFESSION', 'APP_WOW_CHARACTER', 'WoW角色统计', 'WOW_CHARACTER', 'WoW角色', 'professionSecondary', '专业2', 'VALUE_VALIDATION', 'ITEM_VALUE', FALSE, FALSE, 'ENABLED', 'WoW 主专业字段');

UPDATE gak_data_dictionary_usage target
SET dict_code = source.dict_code,
    dictionary_id = dictionary.id,
    app_name = source.app_name,
    module_code = source.module_code,
    module_name = source.module_name,
    biz_field_name = source.biz_field_name,
    usage_type = source.usage_type,
    value_mode = source.value_mode,
    allow_multiple = source.allow_multiple,
    required_flag = source.required_flag,
    status = source.status,
    remark = source.remark,
    updated_at = CURRENT_TIMESTAMP
FROM seed_wow_dictionary_usage source
JOIN gak_data_dictionary dictionary ON dictionary.dict_code = source.dict_code
WHERE target.app_code = source.app_code
  AND target.module_code = source.module_code
  AND target.biz_field_code = source.biz_field_code;

INSERT INTO gak_data_dictionary_usage (
    id, dict_code, dictionary_id, app_code, app_name, module_code, module_name, biz_field_code, biz_field_name,
    usage_type, value_mode, allow_multiple, required_flag, status, usage_count, last_used_at, remark, created_at, updated_at
)
SELECT
    source.id, source.dict_code, dictionary.id, source.app_code, source.app_name, source.module_code, source.module_name,
    source.biz_field_code, source.biz_field_name, source.usage_type, source.value_mode, source.allow_multiple,
    source.required_flag, source.status, 0, NULL, source.remark, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM seed_wow_dictionary_usage source
JOIN gak_data_dictionary dictionary ON dictionary.dict_code = source.dict_code
WHERE NOT EXISTS (
    SELECT 1
    FROM gak_data_dictionary_usage existing
    WHERE existing.app_code = source.app_code
      AND existing.module_code = source.module_code
      AND existing.biz_field_code = source.biz_field_code
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

INSERT INTO gak_health_record (
    id, owner_user_id, measure_date, height_cm, weight_kg, body_fat_rate,
    systolic_pressure, diastolic_pressure, total_cholesterol, triglycerides,
    hdl_cholesterol, ldl_cholesterol, fasting_glucose, heart_rate, uric_acid,
    alanine_aminotransferase, aspartate_aminotransferase, gamma_glutamyl_transferase,
    note, created_at, updated_at
)
SELECT *
FROM (
    VALUES
        (7501, 900000000000000001, DATE '2026-01-15', 175.0, 78.8, 23.1, 129, 85, 5.52, 1.96, 1.08, 3.45, 5.31, 76, 462, 54, 38, 75, '年初熬夜较多。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7502, 900000000000000001, DATE '2026-02-12', 175.0, 77.9, 22.4, 125, 82, 5.34, 1.74, 1.11, 3.28, 5.18, 74, 448, 46, 34, 69, '恢复晨练。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7503, 900000000000000001, DATE '2026-03-09', 175.0, 76.9, 21.8, 122, 80, 5.12, 1.58, 1.16, 3.14, 5.02, 72, 431, 38, 31, 61, '控制夜宵。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7504, 900000000000000001, DATE '2026-04-06', 175.0, 76.1, 21.1, 120, 79, 4.95, 1.42, 1.19, 3.02, 4.96, 71, 418, 33, 29, 55, '体检前一周规律作息。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7505, 900000000000000001, DATE '2026-04-28', 175.0, 75.6, 20.6, 118, 78, 4.86, 1.31, 1.22, 2.96, 4.90, 69, 402, 29, 27, 49, '复查指标继续改善。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7506, 900000000000000001, DATE '2026-05-10', 175.0, 75.2, 20.2, 117, 77, 4.79, 1.24, 1.25, 2.89, 4.87, 68, 396, 27, 25, 45, '晨起空腹测量。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS seeded_health_records (
    id, owner_user_id, measure_date, height_cm, weight_kg, body_fat_rate,
    systolic_pressure, diastolic_pressure, total_cholesterol, triglycerides,
    hdl_cholesterol, ldl_cholesterol, fasting_glucose, heart_rate, uric_acid,
    alanine_aminotransferase, aspartate_aminotransferase, gamma_glutamyl_transferase,
    note, created_at, updated_at
)
ON CONFLICT (id) DO UPDATE SET
    owner_user_id = EXCLUDED.owner_user_id,
    measure_date = EXCLUDED.measure_date,
    height_cm = EXCLUDED.height_cm,
    weight_kg = EXCLUDED.weight_kg,
    body_fat_rate = EXCLUDED.body_fat_rate,
    systolic_pressure = EXCLUDED.systolic_pressure,
    diastolic_pressure = EXCLUDED.diastolic_pressure,
    total_cholesterol = EXCLUDED.total_cholesterol,
    triglycerides = EXCLUDED.triglycerides,
    hdl_cholesterol = EXCLUDED.hdl_cholesterol,
    ldl_cholesterol = EXCLUDED.ldl_cholesterol,
    fasting_glucose = EXCLUDED.fasting_glucose,
    heart_rate = EXCLUDED.heart_rate,
    uric_acid = EXCLUDED.uric_acid,
    alanine_aminotransferase = EXCLUDED.alanine_aminotransferase,
    aspartate_aminotransferase = EXCLUDED.aspartate_aminotransferase,
    gamma_glutamyl_transferase = EXCLUDED.gamma_glutamyl_transferase,
    note = EXCLUDED.note,
    updated_at = EXCLUDED.updated_at;

INSERT INTO gak_health_visit (
    id, owner_user_id, visit_date, hospital_name, department_name, doctor_name, visit_type,
    chief_complaint, diagnosis_summary, treatment_plan, doctor_advice,
    case_record_file_name, case_record_url, note, created_at, updated_at
)
SELECT *
FROM (
    VALUES
        (7601, 900000000000000001, DATE '2026-02-20', '市人民医院', '消化内科', '张医生', 'OUTPATIENT', '近期体检提示转氨酶偏高，来院复诊。', '考虑轻度脂肪肝伴肝功能轻度异常。', '建议继续减重，配合肝胆彩超和肝功能复查。', '减少夜宵与饮酒，8 周后复查。', NULL, NULL, '复诊记录。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7602, 900000000000000001, DATE '2026-03-18', '市人民医院', '风湿免疫科', '李医生', 'OUTPATIENT', '尿酸偏高，近期脚趾偶发酸胀。', '高尿酸血症，暂未见明确急性痛风发作。', '先饮食控制并增加饮水量，必要时药物干预。', '减少高嘌呤摄入，1 个月后复查尿酸。', NULL, NULL, '结合年度体检结果随访。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7603, 900000000000000001, DATE '2026-04-09', '市体检中心', '健康管理中心', '王医生', 'FOLLOW_UP', '年度体检后复盘异常指标。', '总体较上年改善，血脂与肝功能已回落。', '维持运动频率和体重控制。', '半年后复查血脂、血压。', NULL, NULL, '体检总检回访。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS seeded_health_visits (
    id, owner_user_id, visit_date, hospital_name, department_name, doctor_name, visit_type,
    chief_complaint, diagnosis_summary, treatment_plan, doctor_advice,
    case_record_file_name, case_record_url, note, created_at, updated_at
)
ON CONFLICT (id) DO UPDATE SET
    owner_user_id = EXCLUDED.owner_user_id,
    visit_date = EXCLUDED.visit_date,
    hospital_name = EXCLUDED.hospital_name,
    department_name = EXCLUDED.department_name,
    doctor_name = EXCLUDED.doctor_name,
    visit_type = EXCLUDED.visit_type,
    chief_complaint = EXCLUDED.chief_complaint,
    diagnosis_summary = EXCLUDED.diagnosis_summary,
    treatment_plan = EXCLUDED.treatment_plan,
    doctor_advice = EXCLUDED.doctor_advice,
    case_record_file_name = EXCLUDED.case_record_file_name,
    case_record_url = EXCLUDED.case_record_url,
    note = EXCLUDED.note,
    updated_at = EXCLUDED.updated_at;

INSERT INTO gak_health_report (
    id, owner_user_id, visit_id, exam_date, hospital_name, report_title,
    summary, doctor_advice, report_file_name, report_url, created_at, updated_at
)
SELECT *
FROM (
    VALUES
        (7701, 900000000000000001, NULL, DATE '2026-02-18', '市体检中心', '2026 年度体检报告', '体重、血脂较上年改善，肝功能和尿酸仍需继续观察。', '继续控制饮食并定期复查。', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7702, 900000000000000001, 7601, DATE '2026-02-20', '市人民医院', '肝功能复查', 'ALT、GGT 较年初下降，趋势向好。', '维持减重和作息管理。', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7703, 900000000000000001, 7602, DATE '2026-03-18', '市人民医院', '尿酸复查', '尿酸仍高于理想区间，但较前次有所回落。', '增加饮水量并继续饮食控制。', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        (7704, 900000000000000001, 7603, DATE '2026-04-09', '市体检中心', '总检回访摘要', '核心风险项均较去年改善，继续保持。', '半年后安排常规复查。', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS seeded_health_reports (
    id, owner_user_id, visit_id, exam_date, hospital_name, report_title,
    summary, doctor_advice, report_file_name, report_url, created_at, updated_at
)
ON CONFLICT (id) DO UPDATE SET
    owner_user_id = EXCLUDED.owner_user_id,
    visit_id = EXCLUDED.visit_id,
    exam_date = EXCLUDED.exam_date,
    hospital_name = EXCLUDED.hospital_name,
    report_title = EXCLUDED.report_title,
    summary = EXCLUDED.summary,
    doctor_advice = EXCLUDED.doctor_advice,
    report_file_name = EXCLUDED.report_file_name,
    report_url = EXCLUDED.report_url,
    updated_at = EXCLUDED.updated_at;

INSERT INTO gak_knowledge_entry (
    id, owner_user_id, title, category_name, scenario, source_name, tags_text,
    summary, content, status, reviewed_by, reviewed_at, review_remark, created_at, updated_at
)
SELECT *
FROM (
    VALUES
        (7801, 900000000000000001, '需求评审先写“本期不做什么”', '工作', '需求评审 / 项目启动', '项目复盘', '需求,边界,沟通', '先把不做的范围说清楚，能显著降低后续返工。', '评审时先列出当前版本明确不做的功能、依赖前提和风险点，比只讲要做什么更容易对齐边界，后续也更少扯皮。', 'PUBLISHED', 900000000000000001, TIMESTAMP '2026-03-10 21:36:00', '初始化发布', TIMESTAMP '2026-03-10 21:36:00', TIMESTAMP '2026-03-10 21:36:00'),
        (7802, 900000000000000001, '复杂问题先做最小验证闭环', '工作', '技术排查 / 系统改造', '线上故障处理', '开发,排查,验证', '越复杂的问题越不能一次改太多变量。', '先找一条最小可验证路径，例如只替换一处接口返回或只改一个状态流，能快速判断方向是否正确，避免在错误路径上投入过多时间。', 'PUBLISHED', 900000000000000001, TIMESTAMP '2026-03-07 17:24:00', '初始化发布', TIMESTAMP '2026-03-07 17:24:00', TIMESTAMP '2026-03-07 17:24:00'),
        (7803, 900000000000000001, '囤货前先换算单位价格', '生活', '家庭采购 / 囤货', '消费复盘', '采购,预算,生活', '不要只看满减和大包装，先看每 100g 或每件单价。', '纸巾、洗衣液、米面粮油这类长期消耗品，统一换算到单位价格后再决定是否囤货，能避免“买便宜了但囤多了”的误判。', 'PUBLISHED', 900000000000000001, TIMESTAMP '2026-03-08 09:42:00', '初始化发布', TIMESTAMP '2026-03-08 09:42:00', TIMESTAMP '2026-03-08 09:42:00'),
        (7804, 900000000000000001, '晨间固定动作比宏大计划更稳定', '生活', '习惯养成 / 精力管理', '个人实践', '习惯,自律,健康', '每天稳定做少量动作，比复杂计划更能长期坚持。', '把早晨前 30 分钟固定成喝水、拉伸、列 3 个重点任务这类低摩擦动作，更容易形成长期稳定的正反馈。', 'PUBLISHED', 900000000000000001, TIMESTAMP '2026-03-06 08:12:00', '初始化发布', TIMESTAMP '2026-03-06 08:12:00', TIMESTAMP '2026-03-06 08:12:00'),
        (7805, 900000000000000001, '软件安装包统一带版本和平台', '工具', '软件归档 / 文件管理', '软件仓库整理', '文件管理,软件,规范', '命名统一后，后续检索和分发会轻松很多。', '建议统一成“软件名_版本号_平台_补充信息”的命名模式，后续做检索、同步和自动扫描时会省掉大量确认成本。', 'PUBLISHED', 900000000000000001, TIMESTAMP '2026-03-05 22:18:00', '初始化发布', TIMESTAMP '2026-03-05 22:18:00', TIMESTAMP '2026-03-05 22:18:00'),
        (7806, 900000000000000001, '记账时把固定支出单独看', '财务', '记账 / 预算复盘', '个人账单复盘', '记账,预算,复盘', '先拆出固定支出，才容易看清真正可优化的部分。', '房租、订阅、通勤等固定支出先单列，再看餐饮、购物、娱乐这些弹性支出，才能区分结构性问题和临时波动。', 'PUBLISHED', 900000000000000001, TIMESTAMP '2026-03-04 20:41:00', '初始化发布', TIMESTAMP '2026-03-04 20:41:00', TIMESTAMP '2026-03-04 20:41:00'),
        (7807, 900000000000000001, '学新东西时主动找反例', '学习', '学习新工具 / 新方法', '长期自学总结', '学习,方法论,边界', '除了看成功案例，也要主动找不适用场景。', '只看正例很容易高估方法的通用性，反例能更快帮助建立边界感，也更利于迁移到真实问题里。', 'PUBLISHED', 900000000000000001, TIMESTAMP '2026-03-03 19:55:00', '初始化发布', TIMESTAMP '2026-03-03 19:55:00', TIMESTAMP '2026-03-03 19:55:00'),
        (7808, 900000000000000001, '健康指标先看趋势再看单次异常', '健康', '体检复盘 / 日常自查', '个人健康记录', '健康,体检,复查', '单次异常值要结合趋势和上下文一起判断。', '血脂、尿酸、转氨酶这类指标不要只盯一次结果，更要结合近几次复查趋势、饮食作息和近期状态一起看，才更接近真实结论。', 'PUBLISHED', 900000000000000001, TIMESTAMP '2026-03-02 18:20:00', '初始化发布', TIMESTAMP '2026-03-02 18:20:00', TIMESTAMP '2026-03-02 18:20:00')
) AS seeded_knowledge_entries (
    id, owner_user_id, title, category_name, scenario, source_name, tags_text,
    summary, content, status, reviewed_by, reviewed_at, review_remark, created_at, updated_at
)
ON CONFLICT (id) DO UPDATE SET
    owner_user_id = EXCLUDED.owner_user_id,
    title = EXCLUDED.title,
    category_name = EXCLUDED.category_name,
    scenario = EXCLUDED.scenario,
    source_name = EXCLUDED.source_name,
    tags_text = EXCLUDED.tags_text,
    summary = EXCLUDED.summary,
    content = EXCLUDED.content,
    status = EXCLUDED.status,
    reviewed_by = EXCLUDED.reviewed_by,
    reviewed_at = EXCLUDED.reviewed_at,
    review_remark = EXCLUDED.review_remark,
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
