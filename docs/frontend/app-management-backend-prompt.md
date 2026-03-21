# 应用管理后端接口代码生成提示词

本文档用于把系统菜单中的“应用管理”模块一次性生成为后端代码。  
本模块不是孤立 CRUD，而是权限管理和主页应用显示的上游数据源，目标是由管理员统一维护应用名称、图标、路由、密级、加密方式、排序和上下线状态。

前端项目当前已固定以下接口路径（基于 `/api` 代理后的实际后端路径）：

- `GET /system/apps`：分页查询应用目录
- `POST /system/apps`：新增应用
- `PUT /system/apps/{id}`：编辑应用
- `PATCH /system/apps/{id}/status`：启用/停用应用
- `POST /system/apps/icon-upload`：上传应用图标

补充说明：
- 前端页面路由已新增为 `/system/apps`
- 页面形态为“列表展示 + 弹窗新增/编辑”
- 图标支持 4 种来源：`PRESET` / `UPLOAD` / `URL` / `TEXT`
- 权限管理页应优先读取应用目录表，而不是写死前端枚举
- 主页根据“当前用户可访问应用”接口显示应用时，应用元数据也应优先来自应用目录表
- 如果“权限管理模块”已经按 `permission-management-backend-prompt.md` 开发，请直接复用其中建议的 `gak_system_app` 表，不要重新建第二张应用表

## 1. 设计目标

### 1.1 本次先解决的问题

- 由管理员统一维护系统中有哪些应用
- 明确每个应用的名称、编码、路由、分类、图标、密级、加密方式、排序和状态
- 支持预设图标和本地图标上传
- 为权限管理提供稳定的应用目录来源
- 为主页应用展示提供统一元数据

### 1.2 与权限管理的关系

- 权限管理负责“某个用户能访问哪些应用”
- 应用管理负责“系统中有哪些应用，以及这些应用的元数据是什么”
- 两者必须共用同一张应用目录表
- 停用应用后，权限管理应禁止继续授权，主页也不应再展示该应用

### 1.3 图标存储策略建议

图标上传可选两种实现：

- 文件服务器 / 对象存储
  - 数据库保存 `iconUrl`、`iconStorageType`、`iconFileName`
  - 推荐用于前后端分离和后续扩展
- 数据库存储
  - 数据库保存图标二进制或 Base64
  - 后端再通过下载接口或静态访问接口暴露一个 `iconUrl`

无论采用哪种实现，对前端都必须统一返回可直接展示的 `iconUrl`。

## 2. 前端真实联调契约

### 2.1 分页查询应用目录

`GET /system/apps`

查询参数：
- `pageNo`：默认 1
- `pageSize`：默认 8
- `keyword`：按 `name/appCode/route/category` 模糊搜索
- `status`：`ENABLED` / `DISABLED`
- `securityLevel`：`PUBLIC` / `INTERNAL` / `CONFIDENTIAL`

前端优先按以下结构解析：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "appCode": "APP_TODO_LIST",
        "featureCode": "APP_TODO_LIST",
        "name": "待办列表",
        "route": "/todo-list",
        "category": "效率工具",
        "securityLevel": "INTERNAL",
        "encryptionMode": "NONE",
        "iconType": "PRESET",
        "iconPreset": "grid",
        "iconText": "待办",
        "iconUrl": "",
        "iconStorageType": "",
        "iconFileName": "",
        "enabled": true,
        "sortNo": 40,
        "description": "安排任务清单、提醒与子任务进度。",
        "remark": "系统内置应用",
        "grantCount": 12
      }
    ],
    "total": 1
  }
}
```

说明：
- 前端会优先读取 `featureCode`，同时兼容 `appCode/code`
- `grantCount` 非必填，但如果方便建议返回，页面会直接显示“有多少用户被授权”

### 2.2 新增应用

`POST /system/apps`

请求体：

```json
{
  "appCode": "APP_EXAMPLE",
  "featureCode": "APP_EXAMPLE",
  "name": "示例应用",
  "route": "/example",
  "category": "演示分组",
  "securityLevel": "INTERNAL",
  "encryptionMode": "NONE",
  "iconType": "PRESET",
  "iconPreset": "grid",
  "iconText": "示例",
  "iconUrl": "",
  "iconStorageType": "",
  "iconFileName": "",
  "enabled": true,
  "sortNo": 110,
  "description": "示例应用说明",
  "remark": "由应用管理页创建"
}
```

成功响应建议直接返回保存后的应用对象。

### 2.3 编辑应用

`PUT /system/apps/{id}`

请求体与新增一致。

### 2.4 启用/停用应用

`PATCH /system/apps/{id}/status`

请求体：

```json
{
  "status": "DISABLED",
  "enabled": false
}
```

说明：
- 前端会同时传 `status` 和 `enabled`
- 后端建议兼容两个字段，并保持两者语义一致

### 2.5 上传应用图标

`POST /system/apps/icon-upload`

请求：
- `multipart/form-data`
- 字段名：`file`

建议成功响应：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "iconUrl": "https://cdn.example.com/apps/todo-list.png",
    "iconStorageType": "FILE_SERVER",
    "iconFileName": "todo-list.png"
  }
}
```

说明：
- 前端上传成功后，会把 `iconUrl/iconStorageType/iconFileName` 回填到应用保存表单
- 如果后端还未接通上传接口，前端会先用本地 Base64 预览草稿兜底

## 3. 当前应用目录基线

建议后端初始化至少以下应用目录，避免应用管理、权限管理、主页三处各维护一套应用编码：

| featureCode | name | route | securityLevel | encryptionMode |
| --- | --- | --- | --- | --- |
| `APP_CALCULATOR` | 计算器 | `/calculator` | `PUBLIC` | `NONE` |
| `APP_WORK_LOG` | 工作日志 | `/work-log` | `INTERNAL` | `FIELD` |
| `APP_PASSWORD_MEMO` | 密码备忘录 | `/password-memo` | `CONFIDENTIAL` | `END_TO_END` |
| `APP_TODO_LIST` | 待办列表 | `/todo-list` | `INTERNAL` | `NONE` |
| `APP_FUEL_STATS` | 油耗统计 | `/fuel-stats` | `PUBLIC` | `NONE` |
| `APP_WOW_CHARACTER` | WoW角色统计 | `/wow-character-stats` | `PUBLIC` | `NONE` |
| `APP_PERSONAL_BILLS` | 个人账单 | `/personal-bills` | `CONFIDENTIAL` | `FIELD` |
| `APP_KNOWLEDGE_BASE` | 经验库 | `/knowledge-base` | `INTERNAL` | `NONE` |
| `APP_SOFTWARE_REPO` | 软件仓库 | `/software-repo` | `INTERNAL` | `NONE` |
| `APP_HEALTH_RECORD` | 健康 | `/health` | `CONFIDENTIAL` | `FIELD` |

推荐枚举：
- `securityLevel`：`PUBLIC` / `INTERNAL` / `CONFIDENTIAL`
- `encryptionMode`：`NONE` / `FIELD` / `END_TO_END`
- `iconType`：`PRESET` / `UPLOAD` / `URL` / `TEXT`

## 4. 推荐数据模型

### 4.1 应用目录表

如果权限模块已开发，请直接复用：

- 表名：`gak_system_app`

字段至少包含：
- `id`
- `app_code`：唯一，例如 `APP_TODO_LIST`
- `app_name`
- `route_path`
- `category`
- `icon_type`
- `icon_preset`
- `icon_text`
- `icon_url`
- `icon_storage_type`
- `icon_file_name`
- `security_level`
- `encryption_mode`
- `enabled`
- `sort_no`
- `description`
- `remark`
- `created_at`
- `updated_at`

约束建议：
- 唯一索引：`app_code`
- `sort_no` 可重复，但建议保持有序

### 4.2 审计日志预留

建议后续复用权限模块的审计日志，或新增：

- 表名：`gak_app_audit_log`

字段建议：
- `id`
- `operator_user_id`
- `app_id`
- `action_type`：`CREATE_APP / UPDATE_APP / ENABLE_APP / DISABLE_APP / UPLOAD_ICON`
- `before_json`
- `after_json`
- `ip`
- `user_agent`
- `created_at`

## 5. 核心业务规则

- 只有管理员允许新增、编辑、启用、停用应用、上传图标
- `appCode/featureCode` 必须全局唯一，建议固定后不再随意修改
- 停用应用后：
  - 权限管理中不可再授予新用户
  - 主页不应展示该应用
  - 当前用户权限查询接口建议自动过滤 `enabled = false` 的应用
- 新增和编辑时要校验路由格式，建议以 `/` 开头
- `iconType = PRESET` 时必须校验 `iconPreset` 合法
- `iconType = UPLOAD` 时必须使用上传接口返回的 `iconUrl`
- `iconType = URL` 时优先使用 `iconUrl`
- `iconType = TEXT` 时优先使用 `iconText`
- `securityLevel` 和 `encryptionMode` 必须是合法枚举
- 上传图标只允许图片类型文件，建议限制大小例如 2MB
- 建议记录应用变更与图标上传审计日志

## 6. 推荐后端代码组织

```text
com.xxx.project
├─ controller
│  └─ app
│     ├─ SystemAppController.java
│     └─ SystemAppUploadController.java
├─ service
│  └─ app
├─ repository（或 mapper）
│  └─ app
├─ domain / entity
│  └─ app
├─ dto
│  └─ app
├─ vo
│  └─ app
└─ enums
   └─ app
```

说明：
- 应用目录虽然会被权限管理复用，但建议在代码组织上有独立的 app 领域
- 权限模块查询应用目录时可直接调用 app service 或读同一张应用表

## 7. 推荐统一返回结构

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "list": [],
    "total": 0
  }
}
```

说明：
- 列表接口返回 `data.list` + `data.total`
- 新增/编辑接口返回保存后的应用对象
- 状态切换接口返回对象或 `null` 都可
- 上传接口返回 `data.iconUrl`、`data.iconStorageType`、`data.iconFileName`
- 失败时返回清晰 `message`

## 8. 可直接投喂后端 Agent 的全量 Prompt

```text
你是一名资深 Java 后端工程师，请基于 Spring Boot 3 + Spring Web + Spring Validation + Spring Security + Spring Data JPA（或 MyBatis）生成“应用管理模块”完整代码，要求可直接运行，并且能够与权限管理、主页应用显示联动。

【核心目标】
实现一个“应用目录管理”模块，由管理员统一维护系统中的应用元数据：
1) 可分页查询应用目录
2) 可新增应用
3) 可编辑应用
4) 可启用/停用应用
5) 可上传应用图标
6) 与权限管理共用同一张应用目录表
7) 停用应用后，权限管理和主页展示都能感知

【固定接口】
1) GET /system/apps
   - 查询参数：pageNo(默认1), pageSize(默认8), keyword(可选), status(可选), securityLevel(可选)
   - 返回：{ list: SystemAppListItem[], total: number }
   - 每项至少包含：
     id, appCode, featureCode, name, route, category, securityLevel, encryptionMode, iconType, iconPreset, iconText, iconUrl, iconStorageType, iconFileName, enabled, sortNo, description, remark
   - 如方便，建议增加 grantCount

2) POST /system/apps
   - 入参：
     appCode, featureCode, name, route, category, securityLevel, encryptionMode, iconType, iconPreset, iconText, iconUrl, iconStorageType, iconFileName, enabled, sortNo, description, remark

3) PUT /system/apps/{id}
   - 入参同新增

4) PATCH /system/apps/{id}/status
   - 入参：
     {
       status: "ENABLED|DISABLED",
       enabled: true|false
     }

5) POST /system/apps/icon-upload
   - multipart/form-data，字段名 file
   - 返回：
     {
       iconUrl,
       iconStorageType,
       iconFileName
     }

【数据模型要求】
1) 如果项目里已经因为权限管理模块创建了 `gak_system_app`，请直接复用，不要再建第二张应用表
2) `gak_system_app` 字段至少包含：
   id, appCode, appName, routePath, category, iconType, iconPreset, iconText, iconUrl, iconStorageType, iconFileName, securityLevel, encryptionMode, enabled, sortNo, description, remark, createdAt, updatedAt
3) appCode 必须唯一

【当前前端固定应用编码】
请初始化以下应用目录：
- APP_CALCULATOR / 计算器 / /calculator / PUBLIC / NONE
- APP_WORK_LOG / 工作日志 / /work-log / INTERNAL / FIELD
- APP_PASSWORD_MEMO / 密码备忘录 / /password-memo / CONFIDENTIAL / END_TO_END
- APP_TODO_LIST / 待办列表 / /todo-list / INTERNAL / NONE
- APP_FUEL_STATS / 油耗统计 / /fuel-stats / PUBLIC / NONE
- APP_WOW_CHARACTER / WoW角色统计 / /wow-character-stats / PUBLIC / NONE
- APP_PERSONAL_BILLS / 个人账单 / /personal-bills / CONFIDENTIAL / FIELD
- APP_KNOWLEDGE_BASE / 经验库 / /knowledge-base / INTERNAL / NONE
- APP_SOFTWARE_REPO / 软件仓库 / /software-repo / INTERNAL / NONE
- APP_HEALTH_RECORD / 健康 / /health / CONFIDENTIAL / FIELD

【业务规则】
1) 只有管理员可新增、编辑、启用、停用应用、上传图标
2) appCode/featureCode 必须全局唯一，建议新增后不允许随意修改
3) route 为空时允许作为预留应用；不为空时建议必须以 / 开头
4) securityLevel 只允许 PUBLIC / INTERNAL / CONFIDENTIAL
5) encryptionMode 只允许 NONE / FIELD / END_TO_END
6) iconType 只允许 PRESET / UPLOAD / URL / TEXT
7) iconType=PRESET 时必须校验 iconPreset 合法
8) iconType=UPLOAD 时必须通过上传接口或其他受控方式生成 iconUrl
9) iconType=URL 时优先使用 iconUrl
10) iconType=TEXT 时优先使用 iconText
11) 停用应用后，权限管理不可再授权该应用，主页当前用户应用接口也应自动过滤 enabled=false 的应用
12) 如果项目已开发权限模块，请保证 /system/permissions/apps 和 /system/permissions/current-user/apps 实际读取的是同一张应用目录表
13) 图标上传可选择文件服务器 / 对象存储 / 数据库存储，但对前端必须统一返回 iconUrl
14) 上传接口仅允许图片文件，建议限制大小例如 2MB

【推荐返回 VO】
SystemAppListItem:
- id
- appCode
- featureCode
- name
- route
- category
- securityLevel
- encryptionMode
- iconType
- iconPreset
- iconText
- iconUrl
- iconStorageType
- iconFileName
- enabled
- sortNo
- description
- remark
- grantCount

UploadIconResponse:
- iconUrl
- iconStorageType
- iconFileName

【返回格式】
统一返回：
{
  "code": "0",
  "message": "success",
  "data": ...
}
失败时 code 非 0，message 为可读错误信息。

【代码输出要求】
- 按 Controller / Service / Repository(Mapper) / Entity / DTO / VO / Exception / Config 分层输出
- 提供 MySQL 8 建表 SQL；如果表已存在，请给增量 alter SQL
- 提供初始化应用目录数据 SQL
- 提供图标上传接口实现；如果使用对象存储或本地文件系统，请说明目录结构和访问方式
- 提供 curl 示例
- 关键校验、停用应用联动逻辑、与权限模块共表复用逻辑处补充注释
- 如果项目已有统一响应封装、登录用户获取工具、审计字段基类，请复用
```

## 9. 单接口增量 Prompt

### 9.1 新增应用接口

```text
请为现有 Spring Boot 项目新增 POST /system/apps 接口。

入参 JSON:
{
  "appCode": "APP_EXAMPLE",
  "featureCode": "APP_EXAMPLE",
  "name": "示例应用",
  "route": "/example",
  "category": "演示分组",
  "securityLevel": "INTERNAL",
  "encryptionMode": "NONE",
  "iconType": "PRESET",
  "iconPreset": "grid",
  "iconText": "示例",
  "iconUrl": "",
  "iconStorageType": "",
  "iconFileName": "",
  "enabled": true,
  "sortNo": 110,
  "description": "示例应用说明",
  "remark": "由应用管理页创建"
}

要求：
1) 只有管理员可以调用
2) 校验 appCode 唯一
3) 校验 securityLevel、encryptionMode、iconType 合法
4) route 非空时必须以 / 开头
5) iconType=PRESET 时校验 iconPreset 合法
6) 保存成功返回完整应用对象
7) 给出 Controller + Service + DTO + Repository/Mapper + 单元测试
```

### 9.2 应用状态切换接口

```text
请为现有 Spring Boot 项目新增 PATCH /system/apps/{id}/status 接口。

入参 JSON:
{
  "status": "DISABLED",
  "enabled": false
}

要求：
1) 只有管理员可以调用
2) status 只允许 ENABLED / DISABLED
3) 兼容 enabled 布尔字段
4) 停用应用后，后续权限查询与主页应用展示都不能继续返回该应用
5) 记录审计日志
6) 给出 Controller + Service + DTO + Repository/Mapper + 单元测试
```

### 9.3 图标上传接口

```text
请为现有 Spring Boot 项目新增 POST /system/apps/icon-upload 接口。

要求：
1) 只有管理员可以调用
2) 请求类型为 multipart/form-data，字段名 file
3) 只允许上传图片文件，限制大小例如 2MB
4) 可自行选择：
   - 存数据库并提供可访问的下载地址
   - 存本地文件系统 / MinIO / OSS 并返回访问地址
5) 成功返回：
   {
     code: "0",
     message: "success",
     data: {
       iconUrl: "https://...",
       iconStorageType: "FILE_SERVER|DB|OSS|MINIO",
       iconFileName: "todo-list.png"
     }
   }
6) 给出 Controller + Service + 存储实现 + 单元测试
```

## 10. 联调约定

- 前端接口文件固定为 `src/api/appManagement.js`
- 前端页面固定路由为 `/system/apps`
- 前端保存应用时会按 `iconType` 提交 `iconPreset/iconText/iconUrl/iconStorageType/iconFileName`
- 前端图标上传会先调用 `POST /system/apps/icon-upload`
- 权限管理页会优先读取应用目录数据作为授权矩阵的应用来源
- 主页应用显示会优先读取当前用户权限接口，如果后端在该接口中返回 `apps`，前端会优先使用后端返回的应用元数据
- 如果后端暂未接通，前端会回退到本地应用目录草稿；后端接通后请保持 `appCode/featureCode` 稳定，避免权限数据失配
