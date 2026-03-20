# 权限管理后端接口代码生成提示词

本文档用于把系统菜单中的“权限管理”模块一次性生成为后端代码，并把主页应用显示切换到“先查当前用户权限，再决定显示哪些应用”的模式。  
本次设计重点不是做复杂 RBAC，而是先把“用户可访问哪些应用”这件事做清楚、做集中、做可审计，同时给后续“应用管理”和“系统日志”预留扩展位。

前端项目当前已固定以下接口路径（基于 `/api` 代理后的实际后端路径）：

- `GET /system/permissions/users`：分页查询可授权用户列表
- `GET /system/permissions/apps`：查询应用目录
- `GET /system/permissions/users/{userId}/app-permissions`：查询指定用户的应用授权
- `PUT /system/permissions/users/{userId}/app-permissions`：保存指定用户的应用授权
- `GET /system/permissions/current-user/apps`：主页读取当前登录用户可见应用

补充说明：
- 前端已新增系统菜单“权限管理”，页面路由为 `/system/permissions`。
- 主页 `/home` 已优先调用 `GET /system/permissions/current-user/apps`，再按授权结果显示应用卡片。
- 当前前端已经预留了“应用管理”和“系统日志”的展示位，但这两个模块本次先不做完整页面，只要求后端设计时留好表结构和接口扩展位。

## 1. 设计目标

### 1.1 本次先解决的问题

- 明确每个用户当前有哪些应用权限
- 可以在一个页面里看到用户清单、应用目录、授权结果，并对单个用户授权
- 主页进入后先查“当前用户可访问应用”，再决定桌面显示哪些应用
- 应用本身的元数据集中维护，避免以后每个前端页面各自写一份应用清单

### 1.2 本次不追求的复杂能力

- 暂不做按钮级、菜单级、字段级权限
- 暂不做多角色叠加授权、数据权限、组织架构权限
- 暂不做完整日志检索页面，只需预留留痕能力

### 1.3 推荐权限模型

采用“用户 - 应用授权”作为第一阶段模型：

- `用户`：系统已有用户表
- `应用目录`：维护每个应用的基础信息、图标、路由、密级、加密方式、上下线状态
- `用户应用授权`：维护某个用户被授权了哪些应用

这样做的原因：
- 前端当前最核心的控制点就是“主页能看到哪些应用”
- 先把应用级授权做稳定，后续再往角色、菜单、日志扩展不会推翻现有结构
- 应用目录表建好后，后续“应用管理”模块可以直接复用

## 2. 前端真实联调契约

### 2.1 用户授权管理页

`GET /system/permissions/users`

查询参数：
- `pageNo`：默认 1
- `pageSize`：默认 8
- `keyword`：按 `username/displayName/phone/email` 模糊搜索
- `status`：`ENABLED` / `DISABLED`
- `roleCode`：`ADMIN` / `DEV` / `USER`

前端优先按以下结构解析：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "username": "admin",
        "displayName": "系统管理员",
        "roleCode": "ADMIN",
        "status": "ENABLED",
        "permissionCount": 10
      }
    ],
    "total": 1
  }
}
```

说明：
- `permissionCount` 是当前用户已授权应用数，前端列表直接展示
- 如果方便，也可以额外返回 `phone/email/enabled`，前端不会报错

### 2.2 应用目录接口

`GET /system/permissions/apps`

建议返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "appCode": "APP_CALCULATOR",
        "featureCode": "APP_CALCULATOR",
        "name": "计算器",
        "route": "/calculator",
        "category": "效率工具",
        "securityLevel": "PUBLIC",
        "encryptionMode": "NONE",
        "enabled": true,
        "sortNo": 10,
        "iconType": "TEXT",
        "iconText": "计算",
        "description": "日常数值计算与公式换算。"
      }
    ]
  }
}
```

说明：
- 前端会优先读 `featureCode`，同时兼容 `appCode/code`
- 该接口本质上就是未来“应用管理”模块的数据来源

### 2.3 查询指定用户授权

`GET /system/permissions/users/{userId}/app-permissions`

建议返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "userId": 1,
    "grantedFeatureCodes": [
      "APP_CALCULATOR",
      "APP_WORK_LOG",
      "APP_PASSWORD_MEMO"
    ],
    "apps": [
      {
        "featureCode": "APP_CALCULATOR",
        "name": "计算器"
      }
    ]
  }
}
```

说明：
- 前端当前至少会读取 `grantedFeatureCodes`
- 同时返回 `apps` 方便后续做更完整的详情展示

### 2.4 保存指定用户授权

`PUT /system/permissions/users/{userId}/app-permissions`

请求体：

```json
{
  "grantedFeatureCodes": [
    "APP_CALCULATOR",
    "APP_WORK_LOG",
    "APP_TODO_LIST"
  ],
  "remark": "由权限管理页提交"
}
```

成功响应：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "userId": 1,
    "grantedFeatureCodes": [
      "APP_CALCULATOR",
      "APP_WORK_LOG",
      "APP_TODO_LIST"
    ],
    "permissionCount": 3
  }
}
```

说明：
- 前端当前按“整包覆盖”保存，不是单条增删
- 建议后端做“先删后插”或“差量更新”，但对外语义是“以本次提交为准”

### 2.5 主页读取当前用户可见应用

`GET /system/permissions/current-user/apps`

这是本次最关键接口。主页会先调这个接口，再根据返回结果决定显示哪些应用。

建议返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "userId": 1,
    "grantedFeatureCodes": [
      "APP_CALCULATOR",
      "APP_WORK_LOG",
      "APP_TODO_LIST"
    ],
    "apps": [
      {
        "featureCode": "APP_CALCULATOR",
        "name": "计算器",
        "route": "/calculator",
        "category": "效率工具",
        "securityLevel": "PUBLIC",
        "encryptionMode": "NONE",
        "enabled": true
      }
    ]
  }
}
```

规则：
- 前端会优先读取 `grantedFeatureCodes`
- 也兼容直接返回数组，例如 `data: [{ featureCode: "APP_CALCULATOR" }]`
- 如果当前用户一个应用都没有，应该明确返回空数组，而不是报错

## 3. 当前应用目录基线

后端初始化数据至少覆盖以下应用编码，避免前后端各维护一套枚举：

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

建议枚举：
- `securityLevel`：`PUBLIC` / `INTERNAL` / `CONFIDENTIAL`
- `encryptionMode`：`NONE` / `FIELD` / `END_TO_END`

## 4. 推荐数据模型

### 4.1 应用目录表

建议表名：`gak_system_app`

字段至少包含：
- `id`：主键
- `app_code`：应用编码，唯一，例如 `APP_TODO_LIST`
- `app_name`：应用名称
- `route_path`：前端路由
- `category`：应用分类
- `icon_type`：图标类型，例如 `TEXT / URL / SVG`
- `icon_text`：文本图标预留
- `icon_url`：图标地址预留
- `security_level`：应用密级
- `encryption_mode`：加密方式
- `enabled`：是否启用
- `sort_no`：排序
- `description`：描述
- `remark`：备注
- `created_at`
- `updated_at`

### 4.2 用户应用授权表

建议表名：`gak_user_app_permission`

字段至少包含：
- `id`
- `user_id`
- `app_id`
- `app_code`
- `granted`：是否授权，默认 `true`
- `granted_by`
- `granted_at`
- `remark`
- `created_at`
- `updated_at`

约束建议：
- 唯一索引：`(user_id, app_code)`
- `user_id` 关联用户表
- `app_id` 或 `app_code` 必须能唯一指向应用目录

### 4.3 审计日志预留表

建议表名：`gak_permission_audit_log`

本次可以先只建表，不必完整做查询页面。字段建议：
- `id`
- `operator_user_id`
- `target_user_id`
- `action_type`：如 `GRANT_APPS / REVOKE_APPS / REPLACE_APPS / QUERY_CURRENT_USER_APPS`
- `before_json`
- `after_json`
- `trace_id`
- `ip`
- `user_agent`
- `created_at`

说明：
- 后续系统日志模块可直接复用或汇总这张表

## 5. 核心业务规则

- 主页应用显示必须优先以授权表为准，而不是写死前端常量
- 只有 `enabled = true` 的应用允许被授权、允许展示
- 保存授权时，提交中不存在的应用视为取消授权
- 普通用户只允许查询自己的 `current-user/apps`
- 管理员才允许管理他人的应用授权
- 如果权限系统已启用，但当前用户没有任何授权，主页应显示空状态，而不是默认放行全部应用
- 为避免系统管理员上线后被锁死，请初始化一个 `admin` 账号并授予全部启用应用
- 应用目录信息要可扩展，后续“应用管理”会继续维护图标、密级、排序、上下线、加密方式
- 授权变更建议记录审计日志

## 6. 推荐后端代码组织

```text
com.xxx.project
├─ controller
│  └─ permission
│     ├─ PermissionManagementController.java
│     └─ CurrentUserPermissionController.java
├─ service
│  └─ permission
├─ repository（或 mapper）
│  └─ permission
├─ domain / entity
│  └─ permission
├─ dto
│  └─ permission
├─ vo
│  └─ permission
└─ enums
   └─ permission
```

说明：
- 本次可以不做一个巨大而复杂的 RBAC 模块，先把“用户应用授权”独立成清晰模块
- 应用目录与授权表都归到同一个 permission 领域，避免后面应用管理又单独拆出一套重复模型

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
- 查询用户授权接口返回 `data.grantedFeatureCodes`
- 主页权限接口返回 `data.grantedFeatureCodes` 和可选的 `data.apps`
- 失败时返回清晰 `message`，前端会直接展示

## 8. 可直接投喂后端 Agent 的全量 Prompt

```text
你是一名资深 Java 后端工程师，请基于 Spring Boot 3 + Spring Web + Spring Validation + Spring Security + Spring Data JPA（或 MyBatis）生成“权限管理模块”完整代码，要求可直接运行，并与现有用户模块、主页应用展示联动。

【核心目标】
围绕“用户有哪些应用权限”设计并实现一套清晰、可扩展的权限模块：
1) 可以分页查询用户授权列表
2) 可以查询应用目录
3) 可以查看和保存某个用户的应用授权
4) 主页进入后优先查询当前登录用户的应用权限，再决定显示哪些应用
5) 预留应用管理和系统日志扩展位

【固定接口】
1) GET /system/permissions/users
   - 查询参数：pageNo(默认1), pageSize(默认8), keyword(可选), status(可选), roleCode(可选)
   - 返回：{ list: UserPermissionListItem[], total: number }
   - 每个用户项至少包含：id, username, displayName, roleCode, status, permissionCount

2) GET /system/permissions/apps
   - 返回应用目录列表
   - 每个应用项至少包含：
     id, appCode/featureCode, name, route, category, securityLevel, encryptionMode, enabled, sortNo, description

3) GET /system/permissions/users/{userId}/app-permissions
   - 返回：
     {
       userId,
       grantedFeatureCodes: string[],
       apps: AppPermissionVO[]
     }

4) PUT /system/permissions/users/{userId}/app-permissions
   - 入参：
     {
       grantedFeatureCodes: string[],
       remark: string
     }
   - 语义：以本次提交为准覆盖该用户的应用授权

5) GET /system/permissions/current-user/apps
   - 从当前登录态中拿 userId
   - 返回当前用户可见应用：
     {
       userId,
       grantedFeatureCodes: string[],
       apps: AppCatalogVO[]
     }
   - 如果当前用户没有任何授权，也要返回成功 + 空数组，不能报错

【当前前端固定应用编码】
请至少初始化以下应用目录数据，并作为后端合法应用编码：
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

【数据模型要求】
1) 生成应用目录表 `gak_system_app`
   字段至少包含：
   id, appCode, appName, routePath, category, iconType, iconText, iconUrl, securityLevel, encryptionMode, enabled, sortNo, description, remark, createdAt, updatedAt

2) 生成用户应用授权表 `gak_user_app_permission`
   字段至少包含：
   id, userId, appId, appCode, granted, grantedBy, grantedAt, remark, createdAt, updatedAt
   唯一约束建议：userId + appCode

3) 预留权限审计日志表 `gak_permission_audit_log`
   字段至少包含：
   id, operatorUserId, targetUserId, actionType, beforeJson, afterJson, traceId, ip, userAgent, createdAt

【业务规则】
1) 主页应用显示必须以授权表为准
2) 只有 enabled=true 的应用才允许授权和展示
3) 保存授权时，以本次提交的 grantedFeatureCodes 为准覆盖用户授权
4) 普通用户只能查询自己的 current-user/apps
5) 只有管理员可查询/修改其他用户授权
6) 用户列表支持按 username/displayName/phone/email 模糊搜索
7) 需要兼容现有用户表，不要重复造一套用户体系
8) 为避免系统管理员没有入口，初始化脚本请给 admin 授予全部启用应用
9) 权限变更建议记录审计日志

【推荐返回 VO】
UserPermissionListItem:
- id
- username
- displayName
- roleCode
- status
- permissionCount

AppCatalogVO:
- id
- appCode
- featureCode
- name
- route
- category
- securityLevel
- encryptionMode
- enabled
- sortNo
- description

UserAppPermissionVO:
- userId
- grantedFeatureCodes
- apps

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
- 提供 MySQL 8 建表 SQL
- 提供初始化数据 SQL，至少初始化 10 个应用目录、1 个 admin 账号的全量授权
- 提供 curl 示例
- 在关键权限判断和覆盖授权逻辑处补充注释，不要只给伪代码
- 如果你选择 MyBatis，请给出 Mapper XML 或注解 SQL
- 如果项目已有统一响应封装、登录用户获取工具、审计字段基类，请复用，不要重复造轮子
```

## 9. 单接口增量 Prompt

### 9.1 当前用户可见应用接口

```text
请为现有 Spring Boot 项目新增 GET /system/permissions/current-user/apps 接口。

要求：
1) 从登录态中拿到当前用户ID
2) 查询该用户被授权且 enabled=true 的应用目录
3) 返回：
   {
     code: "0",
     message: "success",
     data: {
       userId: 1,
       grantedFeatureCodes: ["APP_CALCULATOR", "APP_TODO_LIST"],
       apps: [
         {
           featureCode: "APP_CALCULATOR",
           name: "计算器",
           route: "/calculator",
           securityLevel: "PUBLIC",
           encryptionMode: "NONE",
           enabled: true
         }
       ]
     }
   }
4) 如果没有任何授权，也返回成功 + 空数组
5) 普通用户只能查自己
6) 给出 Controller + Service + Repository/Mapper + VO + 单元测试
```

### 9.2 覆盖保存用户应用授权接口

```text
请为现有 Spring Boot 项目新增 PUT /system/permissions/users/{userId}/app-permissions 接口。

入参 JSON:
{
  "grantedFeatureCodes": [
    "APP_CALCULATOR",
    "APP_WORK_LOG",
    "APP_TODO_LIST"
  ],
  "remark": "由权限管理页提交"
}

要求：
1) 只有管理员可以调用
2) 校验 userId 对应用户存在
3) 校验 grantedFeatureCodes 中的应用编码都存在，且应用 enabled=true
4) 以本次提交为准覆盖保存授权结果
5) 返回当前用户最终授权结果和 permissionCount
6) 记录授权前后差异到审计日志
7) 提供 Controller + Service + DTO + Repository/Mapper + 单元测试
```

## 10. 联调约定

- 前端权限接口文件固定为 `src/api/permission.js`
- 前端权限管理页面固定路由为 `/system/permissions`
- 主页会优先请求 `GET /system/permissions/current-user/apps`
- 前端当前兼容读取 `featureCode/code/appCode`
- 前端当前兼容读取 `grantedFeatureCodes/grantedAppCodes/featureCodes/appCodes`
- 应用目录里如果后端后续增加 `iconUrl/securityLevel/encryptionMode/enabled/sortNo`，前端可直接复用
- 后端如果没有把“未命中路由”正确返回 404，请顺手修一下统一异常处理，避免接口没挂上时前端误判成业务 500
