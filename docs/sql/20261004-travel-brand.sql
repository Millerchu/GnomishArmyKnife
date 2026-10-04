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
