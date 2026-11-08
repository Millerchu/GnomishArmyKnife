-- 2026-11-07 系统异常日志的隔离库升级脚本。
-- 只建表、索引、应用入口和管理员授权。可重复执行，不删除历史日志。
-- 本任务不在生产库执行本脚本，也不据此做生产清理。
-- 优先以应用数据库角色执行，确保新表归该角色所有。若由其他角色执行，须接着由表 owner
-- 执行同目录 20261107-system-exception-log-privileges.sql（psql -v app_role=实际应用角色）。
-- CREATE TABLE IF NOT EXISTS 不会修复已有表权限，重跑本脚本不能替代补授权。

CREATE TABLE IF NOT EXISTS gak_system_exception_log (
    id BIGINT PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    source VARCHAR(32) NOT NULL,
    exception_type VARCHAR(255) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    stack_trace VARCHAR(4000),
    stack_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    http_method VARCHAR(16),
    request_path VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_system_exception_log_source CHECK (source IN ('HTTP', 'ASYNC_TASK', 'SCHEDULER'))
);
CREATE INDEX IF NOT EXISTS idx_system_exception_log_occurred
    ON gak_system_exception_log (occurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_system_exception_log_type_occurred
    ON gak_system_exception_log (exception_type, occurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_system_exception_log_trace
    ON gak_system_exception_log (trace_id);

INSERT INTO gak_system_app(
    id, app_code, app_name, route_path, category, data_source_mode,
    icon_type, icon_text, security_level, encryption_mode, enabled, sort_no,
    description, created_at, updated_at)
VALUES (
    2022, 'APP_SYSTEM_LOG', '系统日志', '/system-log', '系统管理', 'REAL',
    'TEXT', '日志', 'INTERNAL', 'NONE', TRUE, 150,
    '查看后端未处理异常，供管理员排查问题。',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (app_code) DO NOTHING;

INSERT INTO gak_user_app_permission (
    id, user_id, app_id, app_code, granted, granted_by, granted_at, remark, created_at, updated_at)
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
WHERE app.app_code = 'APP_SYSTEM_LOG'
  AND NOT EXISTS (
      SELECT 1
      FROM gak_user_app_permission permission
      WHERE permission.user_id = admin_user.id
        AND permission.app_code = app.app_code
  );
