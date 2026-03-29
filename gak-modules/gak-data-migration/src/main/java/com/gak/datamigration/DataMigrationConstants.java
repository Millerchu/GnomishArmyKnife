package com.gak.datamigration;

/**
 * 数据迁移常量。
 */
public final class DataMigrationConstants {

    public static final String TASK_TYPE_EXPORT = "EXPORT";
    public static final String TASK_TYPE_IMPORT = "IMPORT";

    public static final String TASK_STATUS_PENDING = "PENDING";
    public static final String TASK_STATUS_RUNNING = "RUNNING";
    public static final String TASK_STATUS_SUCCESS = "SUCCESS";
    public static final String TASK_STATUS_FAILED = "FAILED";
    public static final String TASK_STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";

    public static final String SCOPE_MODE_ALL = "ALL";
    public static final String SCOPE_MODE_SYSTEM_ONLY = "SYSTEM_ONLY";
    public static final String SCOPE_MODE_BUSINESS_ONLY = "BUSINESS_ONLY";
    public static final String SCOPE_MODE_CUSTOM = "CUSTOM";

    public static final String IMPORT_MODE_MERGE = "MERGE";
    public static final String IMPORT_MODE_OVERWRITE = "OVERWRITE";
    public static final String IMPORT_MODE_STRICT = "STRICT";

    public static final String RESOURCE_TYPE_SYSTEM = "SYSTEM";
    public static final String RESOURCE_TYPE_BUSINESS = "BUSINESS";

    public static final String SYSTEM_RESOURCE_USERS = "SYSTEM_USERS";
    public static final String SYSTEM_RESOURCE_APPS = "SYSTEM_APPS";
    public static final String SYSTEM_RESOURCE_PERMISSIONS = "SYSTEM_PERMISSIONS";
    public static final String SYSTEM_RESOURCE_DICTIONARIES = "SYSTEM_DICTIONARIES";

    public static final String FILE_STORAGE_LOCAL = "LOCAL";
    public static final String PACKAGE_VERSION = "1.0.0";
    public static final String COMPATIBLE_VERSION = "1.x";
    public static final String MANIFEST_FILE_NAME = "manifest.json";
    public static final String ATTACHMENT_INDEX_FILE = "attachments/index.json";
    public static final String APP_ICON_ATTACHMENT_DIR = "attachments/system-app-icons";

    private DataMigrationConstants() {
    }
}
