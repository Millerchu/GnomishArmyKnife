\set ON_ERROR_STOP on
-- 用户偏好（工作台布局等）。可重复执行，不包含业务示例数据。
-- 本脚本不在生产库执行，除非用户另行授权。
-- 优先以应用数据库角色执行；若由其他角色执行，须由表 owner 执行
-- 20261111-user-preference-privileges.sql（psql -v app_role=实际应用角色）。
-- CREATE TABLE IF NOT EXISTS 不会修复已有表权限；主键由应用 ASSIGN_ID 生成，无序列。
CREATE TABLE IF NOT EXISTS public.gak_user_preference (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES public.gak_user (id) ON DELETE CASCADE,
    pref_key VARCHAR(64) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_preference_user_key UNIQUE (user_id, pref_key),
    CONSTRAINT ck_user_preference_key CHECK (length(trim(pref_key)) > 0),
    CONSTRAINT ck_user_preference_schema_version CHECK (schema_version >= 1),
    CONSTRAINT ck_user_preference_version CHECK (version >= 1)
);

-- 唯一约束已建立 (user_id, pref_key) B-tree，也覆盖 user_id 前缀查询；不再建冗余索引。
