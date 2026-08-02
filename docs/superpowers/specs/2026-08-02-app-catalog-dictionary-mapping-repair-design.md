# 应用目录字典映射修复设计

## 问题

权限管理加载应用目录时，会按照 `APP_APP_MANAGEMENT / SYSTEM_APP` 的字段使用映射校验应用元数据。当前数据库没有该范围的任何映射，因此所有应用目录记录都会触发 `APP_CATALOG_INVALID`，前端将其误显示为接口不可用。

## 修复范围

- 新增 `APP_DATA_SOURCE_MODE` 字典，包含 `REAL`、`DEMO`。
- 新增 `APP_ICON_TYPE` 字典，包含 `PRESET`、`UPLOAD`、`URL`、`TEXT`。
- 为 `APP_APP_MANAGEMENT / SYSTEM_APP` 建立五条字段映射：`dataSourceMode`、`securityLevel`、`encryptionMode`、`iconType`、`status`。
- 复用现有的 `APP_SECURITY_LEVEL`、`APP_ENCRYPTION_MODE`、`USER_STATUS` 字典。

## 数据与兼容性

所有 schema 语句均以现有记录为前提执行，使用 `WHERE NOT EXISTS` 或按业务范围判断，重复执行不会插入重复字典、字典项或字段映射。当前本地 PostgreSQL 执行相同 SQL，使正在运行的服务立即具备完整映射；不修改已有应用目录记录。

## 验收

- `APP_APP_MANAGEMENT / SYSTEM_APP` 存在 5 条启用的字段映射。
- 后端权限服务可通过应用目录元数据校验。
- 管理员登录后，权限页能加载应用目录和用户清单，不再出现“应用目录存在非法配置”。

