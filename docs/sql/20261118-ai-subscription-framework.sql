-- 智订应用框架：重复执行保留已有应用配置及显式授权/撤权。
BEGIN;
INSERT INTO gak_system_app(id, app_code, app_name, route_path, category, data_source_mode,
    icon_type, icon_text, icon_url, icon_storage_type, icon_file_name, security_level,
    encryption_mode, enabled, sort_no, description, created_at, updated_at)
VALUES (2023, 'APP_AI_SUBSCRIPTION', '智订', '/ai-subscriptions', '效率工具', 'REAL',
    'URL', '智订', '/app-icons/app-ai-subscription.png', 'PUBLIC_ASSET', 'app-ai-subscription.png',
    'CONFIDENTIAL', 'NONE', TRUE, 160, 'AI 订阅、用量与费用管理（应用框架，业务功能建设中）。',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (app_code) DO NOTHING;
INSERT INTO gak_user_app_permission (
    id, user_id, app_id, app_code, granted, granted_by, granted_at, remark, created_at, updated_at)
SELECT 400000000000000000 + app.id, admin_user.id, app.id, app.app_code,
    TRUE, admin_user.id, CURRENT_TIMESTAMP, 'ai subscription framework init', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM gak_system_app app
JOIN gak_user admin_user ON admin_user.username = 'admin'
WHERE app.app_code = 'APP_AI_SUBSCRIPTION'
  AND NOT EXISTS (
    SELECT 1 FROM gak_user_app_permission permission
    WHERE permission.user_id = admin_user.id AND permission.app_code = app.app_code
  );
COMMIT;
