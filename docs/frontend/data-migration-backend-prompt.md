# 数据迁移后端接口代码生成提示词

本文档用于把系统菜单中的“数据迁移”模块一次性生成为后端代码。  
本模块的目标不是简单文件上传下载，而是面向“跨环境迁移”的完整链路：管理员选择系统数据、业务数据和附件范围，后端按资源清单导出为 ZIP 包，并在目标环境按策略导入。

前端项目当前已固定以下接口路径（基于 `/api` 代理后的实际后端路径）：

- `GET /system/data-migrations/resources`：查询可迁移资源清单
- `GET /system/data-migrations/tasks`：查询迁移任务列表
- `GET /system/data-migrations/tasks/{taskId}`：查询迁移任务详情
- `POST /system/data-migrations/exports`：创建导出任务
- `GET /system/data-migrations/tasks/{taskId}/download`：下载导出 ZIP 包
- `DELETE /system/data-migrations/tasks/{taskId}`：删除导出历史并同步删除压缩包
- `POST /system/data-migrations/imports`：上传 ZIP 包并创建导入任务

补充说明：

- 前端页面路由已新增为 `/system/data-migrations`
- 页面沿用现有系统管理页的全局壁纸背景和毛玻璃容器，不需要后端额外提供页面配置
- 页面只允许管理员使用
- 导出范围分为系统数据和业务数据
- 业务数据支持按应用维度选择，也支持全部导出
- 导出包统一为 ZIP 格式
- 导入时前端支持三种策略：`MERGE` / `OVERWRITE` / `STRICT`
- 如果业务模块存在附件，后端可接入文件服务或对象存储，前端已预留 `includeAttachments` 开关
- 前端已提供“导出导入历史”区域，会按任务类型、状态和分页参数查询真实历史记录
- 前端已提供“删除导出包”按钮，仅对导出历史展示；后端删除时必须同步清理压缩包和冗余文件

## 1. 设计目标

### 1.1 本次先解决的问题

- 管理员可以选择迁移哪些系统资源
- 管理员可以选择迁移哪些业务应用数据，或直接全量迁移
- 导出时生成结构清晰、可校验、可追踪的 ZIP 包
- 导入时支持严格、合并、覆盖三种落库策略
- 导入导出都保留任务记录，便于审计与问题追踪

### 1.2 本次不追求的复杂能力

- 暂不做前端可视化差异对比
- 暂不做在线断点续传
- 暂不做跨版本自动字段映射
- 暂不做多活环境双向同步

## 2. 推荐技术选型

如果项目是 Java 技术栈，推荐使用以下常见中间件或组件即可：

- Web 框架：Spring Boot
- ORM：MyBatis-Plus 或 Spring Data JPA
- 任务调度：Quartz、XXL-JOB 或基于线程池的异步任务执行
- 压缩组件：`zip4j` 或 `commons-compress`
- JSON 处理：Jackson
- 对象存储/文件服务：
  - MinIO
  - Amazon S3
  - 阿里云 OSS
  - 本地文件服务 + Nginx

如果现有系统已经有统一文件服务，请优先复用，不要另起一套附件存储逻辑。

## 3. 前端真实联调契约

### 3.1 查询可迁移资源清单

`GET /system/data-migrations/resources`

建议返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "systemResources": [
      {
        "code": "SYSTEM_USERS",
        "name": "用户与账号",
        "description": "系统用户、角色标识、状态和个人资料等基础账号数据。",
        "attachmentSupported": false
      },
      {
        "code": "SYSTEM_APPS",
        "name": "应用目录",
        "description": "应用管理维护的应用元数据、路由、图标和上下线配置。",
        "attachmentSupported": true
      }
    ],
    "businessApps": [
      {
        "id": 1,
        "appCode": "APP_TODO_LIST",
        "featureCode": "APP_TODO_LIST",
        "name": "待办列表",
        "route": "/todo-list",
        "category": "效率工具",
        "securityLevel": "INTERNAL",
        "enabled": true,
        "description": "安排任务清单、提醒与子任务进度。"
      }
    ]
  }
}
```

说明：

- 前端会优先读取 `systemResources` 和 `businessApps`
- 业务应用编码优先读 `featureCode`，同时兼容 `appCode/code`
- 如果后端尚未维护资源清单，前端会先用本地基线兜底，但最终应以后端定义为准

### 3.2 查询迁移任务列表

`GET /system/data-migrations/tasks`

查询参数建议：

- `pageNo`：默认 1
- `pageSize`：默认 12
- `taskType`：可选，`EXPORT` / `IMPORT`
- `status`：可选

建议返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1001,
        "taskNo": "DM202603290001",
        "taskType": "EXPORT",
        "status": "SUCCESS",
        "packageName": "prod-to-uat-20260329",
        "fileName": "prod-to-uat-20260329.zip",
        "fileSize": 20971520,
        "systemResourceCount": 4,
        "businessAppCount": 3,
        "recordCount": 1268,
        "attachmentCount": 42,
        "downloadUrl": "https://file.example.com/data-migrations/prod-to-uat-20260329.zip",
        "canDownload": true,
        "canDelete": true,
        "message": "导出完成",
        "createdAt": "2026-03-29 10:00:00",
        "finishedAt": "2026-03-29 10:03:15"
      }
    ],
    "total": 1
  }
}
```

### 3.3 查询迁移任务详情

`GET /system/data-migrations/tasks/{taskId}`

建议返回单个任务对象，字段可复用任务列表结构。  
如果任务失败，建议在 `message/errorMessage` 字段返回具体原因。

### 3.4 创建导出任务

`POST /system/data-migrations/exports`

请求体：

```json
{
  "scopeMode": "CUSTOM",
  "packageName": "prod-to-uat-20260329",
  "includeAttachments": true,
  "systemResourceCodes": [
    "SYSTEM_USERS",
    "SYSTEM_APPS",
    "SYSTEM_PERMISSIONS"
  ],
  "businessAppCodes": [
    "APP_TODO_LIST",
    "APP_WORK_LOG"
  ],
  "remark": "生产到测试环境的迁移包"
}
```

成功响应建议返回任务对象：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": 1001,
    "taskNo": "DM202603290001",
    "taskType": "EXPORT",
    "status": "RUNNING",
    "packageName": "prod-to-uat-20260329",
    "canDownload": false,
    "createdAt": "2026-03-29 10:00:00"
  }
}
```

说明：

- 如果导出是异步任务，初始状态返回 `RUNNING` 即可
- 如果导出量很小、后端选择同步返回，也可以直接返回 `SUCCESS + downloadUrl`

### 3.5 下载导出 ZIP 包

`GET /system/data-migrations/tasks/{taskId}/download`

说明：

- 前端按二进制流下载
- 返回值建议直接是文件流
- 响应头应带好 `Content-Disposition`
- 若任务未完成，应返回明确错误信息，不要返回空文件
- 历史列表中的“下载包”按钮直接依赖这个接口

### 3.6 上传 ZIP 包并创建导入任务

`POST /system/data-migrations/imports`

请求：

- `multipart/form-data`
- 文件字段名：`file`
- 文本字段名：`metadata`

其中 `metadata` 是 JSON 字符串，格式如下：

```json
{
  "importMode": "MERGE",
  "includeAttachments": true,
  "continueOnError": false,
  "remark": "导入到目标测试环境"
}
```

建议成功响应：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": 1002,
    "taskNo": "DM202603290002",
    "taskType": "IMPORT",
    "status": "RUNNING",
    "packageName": "prod-to-uat-20260329",
    "message": "导入任务已创建",
    "createdAt": "2026-03-29 10:06:00"
  }
}
```

说明：

- 后端需要解析 ZIP 包里的 `manifest.json`
- 导入可以先落临时文件，也可以先存入文件服务，再异步执行
- 如果压缩包不合法，应直接返回业务错误，不要创建无意义任务

### 3.7 删除导出历史并删除压缩包

`DELETE /system/data-migrations/tasks/{taskId}`

说明：

- 只允许删除导出任务，导入任务默认不提供删除入口
- 删除时应同步删除任务记录关联的 ZIP 文件、临时压缩目录和可清理的冗余归档文件
- 如果文件已经不存在，后端应保证接口幂等，不要因为“文件不存在”导致整次删除失败

建议成功响应：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": 1001,
    "deleted": true
  }
}
```

## 4. 资源清单建议

### 4.1 系统资源编码建议

- `SYSTEM_USERS`：用户与账号
- `SYSTEM_APPS`：应用目录
- `SYSTEM_PERMISSIONS`：权限授权
- `SYSTEM_DICTIONARIES`：数据字典
- `SYSTEM_SETTINGS`：系统参数

### 4.2 业务资源来源建议

业务资源直接复用应用目录表中的应用编码：

- 建议复用 `gak_system_app`
- 业务迁移按 `app_code/feature_code` 作为逻辑分组
- 每个业务模块自己提供“导出该应用数据”和“导入该应用数据”的适配器

推荐抽象一个统一接口，例如：

- `MigrationResourceHandler`
  - `String resourceCode()`
  - `MigrationExportData exportData(MigrationContext context)`
  - `MigrationImportResult importData(MigrationContext context, MigrationPackage pkg)`

这样系统资源和业务资源都能走同一套调度逻辑。

## 5. ZIP 包结构建议

```text
{packageName}.zip
├── manifest.json
├── system/
│   ├── users.json
│   ├── apps.json
│   ├── permissions.json
│   ├── dictionaries.json
│   └── settings.json
├── business/
│   ├── APP_TODO_LIST/
│   │   └── data.json
│   └── APP_WORK_LOG/
│       └── data.json
└── attachments/
    ├── index.json
    └── ...
```

`manifest.json` 建议包含：

- `packageName`
- `packageVersion`
- `sourceEnv`
- `createdAt`
- `createdBy`
- `scopeMode`
- `systemResourceCodes`
- `businessAppCodes`
- `recordCount`
- `attachmentCount`
- `checksum`
- `compatibleVersion`

## 6. 数据模型建议

### 6.1 迁移任务表

建议表名：`gak_data_migration_task`

字段至少包含：

- `id`
- `task_no`
- `task_type`：`EXPORT` / `IMPORT`
- `status`：`PENDING / RUNNING / SUCCESS / FAILED / PARTIAL_SUCCESS`
- `scope_mode`
- `package_name`
- `system_resource_codes`
- `business_app_codes`
- `include_attachments`
- `import_mode`
- `continue_on_error`
- `record_count`
- `attachment_count`
- `file_url`
- `file_storage_type`
- `file_name`
- `file_size`
- `error_message`
- `remark`
- `operator_user_id`
- `created_at`
- `finished_at`

说明：

- 这张表必须长期保存导出、导入历史，不能只保留最近一次任务
- 建议按 `created_at desc` 建索引，保证历史列表分页查询稳定
- 删除导出任务时需要同步删除任务关联的物理压缩包或对象存储文件

### 6.2 迁移任务明细表

建议表名：`gak_data_migration_task_item`

字段建议：

- `id`
- `task_id`
- `resource_code`
- `resource_name`
- `resource_type`：`SYSTEM` / `BUSINESS`
- `status`
- `record_count`
- `attachment_count`
- `message`
- `created_at`
- `finished_at`

## 7. 核心业务规则

### 7.1 权限规则

- 只有管理员可以调用导入导出接口
- 普通用户即使知道接口地址，也必须被后端拒绝

### 7.2 导出规则

- `scopeMode = ALL` 时，系统资源和业务资源都按全量处理
- `scopeMode = SYSTEM_ONLY` 时，忽略 `businessAppCodes`
- `scopeMode = BUSINESS_ONLY` 时，忽略 `systemResourceCodes`
- `scopeMode = CUSTOM` 时，以显式传入的资源编码为准
- 至少要导出一项资源，不能创建空任务

### 7.3 导入规则

- 必须校验 ZIP 格式和 `manifest.json`
- 必须校验资源编码是否合法
- `STRICT`：发现版本不兼容、主键冲突或依赖缺失立即失败
- `MERGE`：同主键数据优先按增量更新处理
- `OVERWRITE`：同主键数据允许覆盖
- `continueOnError = true` 时允许记录局部失败并继续执行，但最终状态建议标记为 `PARTIAL_SUCCESS`

### 7.4 附件规则

- `includeAttachments = true` 时：
  - 导出需要同时归档附件
  - 导入需要恢复附件并更新新的文件地址或文件 ID
- 建议通过统一文件服务处理附件，不要每个业务模块自己直写磁盘

## 8. 推荐实现步骤

1. 建立迁移任务表和任务明细表
2. 完成资源清单接口
3. 完成导出任务创建、打包和下载接口
4. 完成导入上传、校验和执行接口
5. 接入文件服务处理附件
6. 为系统资源和业务资源分别实现迁移处理器
7. 补充审计日志、权限校验和异常处理

## 9. 生成要求

请直接生成可运行的后端代码，至少包含以下内容：

- Controller
- DTO / VO
- Service
- 异步任务执行逻辑
- 任务表和明细表实体
- 资源处理器抽象接口
- ZIP 打包与解包逻辑
- `manifest.json` 读写逻辑
- 文件服务集成接口（若项目已有文件服务则优先复用）
- 必要的参数校验、权限校验和异常处理

代码注释请保持简洁、准确，注释重点放在迁移任务流转、导入导出策略和附件处理边界，不要写无意义注释。
