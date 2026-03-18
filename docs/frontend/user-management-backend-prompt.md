# 用户模块后端接口整合提示词

本文档用于把“待开发的用户管理接口”与“当前已经开发好的登录/注册接口”整合为同一个后端用户模块，便于直接投喂后端 Agent。  
重点不是单纯补几个接口，而是要在不改前端既有接口路径的前提下，把用户相关能力收敛到同一套领域模型、同一个 controller 包路径、同一个 service/repository 体系中，避免认证和用户管理各自维护一套用户逻辑。

前端项目当前已固定以下接口路径（基于 `/api` 代理后的实际后端路径）：

- `GET /auth/captcha`：获取 4 位验证码
- `GET /auth/password-public-key`：获取登录密码 RSA 公钥
- `POST /auth/login`：登录
- `POST /auth/register`：注册
- `GET /system/users`：分页查询用户列表
- `POST /system/users`：新增用户
- `PUT /system/users/{id}`：编辑用户
- `DELETE /system/users/{id}`：删除用户
- `PATCH /system/users/{id}/status`：启用/禁用用户
- `POST /system/users/{id}/reset-password`：重置密码

补充说明：
- 登录、注册接口前端已经接好，后端当前已有部分实现。
- 用户管理接口前端也已经预留好，后端待补齐。
- 本次目标是让后端把这些接口整合成统一的“用户模块”代码结构，而不是继续分散实现。
- 外部 URL 保持不变，避免前端改动；这里要求统一的是后端代码组织，而不是把所有 URL 改成同一个前缀。

## 1. 前端真实联调契约

### 1.1 登录接口真实入参/出参

`POST /auth/login`

请求体：

```json
{
  "username": "admin",
  "encryptedPassword": "RSA加密后的密码密文",
  "captcha": "ABCD"
}
```

成功响应：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "token": "jwt-or-session-token",
    "user": {
      "id": 1,
      "username": "admin",
      "displayName": "系统管理员",
      "phone": "13800000000",
      "email": "admin@example.com",
      "roleCode": "ADMIN",
      "status": "ENABLED",
      "enabled": true,
      "forceChangePassword": false,
      "lastLoginTime": "2026-03-18 10:00:00"
    }
  }
}
```

说明：
- 前端登录页先调 `GET /auth/captcha`，再调 `GET /auth/password-public-key`，然后把明文密码用 RSA 公钥加密后提交。
- 登录成功后，前端从 `data.token` 和 `data.user` 取值。
- 业务接口请求头会自动携带 `Authorization: Bearer {token}`。

### 1.2 注册接口真实入参

`POST /auth/register`

当前前端注册页提交的字段不是旧版的简化结构，而是以下扩展结构：

```json
{
  "username": "zhangsan",
  "password": "123456",
  "initialPassword": "123456",
  "displayName": "张三",
  "phone": "13800001234",
  "email": "zhangsan@example.com",
  "roleCode": "USER",
  "status": "ENABLED",
  "enabled": true
}
```

关键约束：
- 这是本次必须特别处理的点：注册接口相比之前已经增加了 `displayName`、`phone`、`email`、`roleCode`、`status`、`enabled` 等字段。
- 当前前端会同时传 `password` 和 `initialPassword`，两者值相同。后端应优先以 `initialPassword` 作为注册密码来源，同时兼容 `password` 这个旧字段。
- 不允许保存明文密码，必须转为 `passwordHash`。
- 普通自助注册默认角色为 `USER`、默认状态为 `ENABLED`；如果前端传了这两个字段，也要做合法性校验。

### 1.3 用户管理接口真实入参/出参

`GET /system/users`

查询参数：
- `pageNo`：默认 1
- `pageSize`：默认 10
- `keyword`：按 `username/displayName/phone/email` 模糊搜索
- `status`：`ENABLED` / `DISABLED`
- `roleCode`：`ADMIN` / `DEV` / `USER`

列表响应前端优先按以下结构解析：

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

`POST /system/users` 请求体：

```json
{
  "username": "lisi",
  "initialPassword": "Abc123!@#",
  "displayName": "李四",
  "phone": "13900000000",
  "email": "lisi@example.com",
  "roleCode": "DEV",
  "status": "ENABLED",
  "enabled": true,
  "remark": "测试账号"
}
```

`PUT /system/users/{id}` 请求体：

```json
{
  "username": "lisi",
  "displayName": "李四",
  "phone": "13900000000",
  "email": "lisi@example.com",
  "roleCode": "DEV",
  "status": "DISABLED",
  "enabled": false,
  "remark": "已离职"
}
```

`PATCH /system/users/{id}/status` 请求体：

```json
{
  "status": "ENABLED",
  "enabled": true
}
```

`POST /system/users/{id}/reset-password` 请求体：

```json
{
  "newPassword": "NewPass123!",
  "forceChange": true
}
```

## 2. 推荐后端代码组织规范

所有“用户相关”代码统一放到同一个业务模块下，例如：

```text
com.xxx.project
├─ controller
│  └─ user
│     ├─ AuthController.java
│     ├─ SystemUserController.java
│     └─ CurrentUserController.java   // 如果项目里已有 /user/profile、/user/password，可一并归到这里
├─ service
│  └─ user
├─ repository（或 mapper）
│  └─ user
├─ domain / entity
│  └─ user
├─ dto
│  └─ user
├─ vo
│  └─ user
└─ enums
   └─ user
```

明确要求：
- `AuthController` 和 `SystemUserController` 虽然 URL 前缀不同，但必须位于同一个 controller 包路径下，例如 `controller.user`。
- 登录、注册、用户管理共用同一张用户表、同一个 `User` 实体、同一个 `UserRepository` / `UserMapper`。
- 不要为登录注册单独维护一套 `AuthUser`，也不要为后台用户管理再建一套 `SystemUser`，否则后续数据会割裂。
- 认证相关能力和用户管理能力可以拆成不同 service，但底层领域对象必须统一。

## 3. 推荐统一返回结构

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
- 登录接口返回 `data.token` + `data.user`
- 获取验证码接口返回 `data.captcha`
- 获取公钥接口返回 `data.publicKey`
- 非列表接口可返回对象或 `null`
- 失败时返回清晰 `message`，前端会直接展示

## 4. 可直接投喂后端 Agent 的全量 Prompt

```text
你是一名资深 Java 后端工程师，请基于 Spring Boot 3 + Spring Web + Spring Validation + Spring Security + Spring Data JPA（或 MyBatis）整合现有“登录/注册接口”和“用户管理接口”，生成一个可直接运行的“统一用户模块”。

【核心目标】
不是只补用户管理 CRUD，而是把当前已经开发好的认证接口与待开发的用户管理接口整合到同一套用户领域模型中：
1) 外部接口 URL 保持不变，不能要求前端改路径
2) 后端代码按同一个用户模块组织，所有用户相关 controller 放在同一个包路径下，例如 `controller.user`
3) 登录、注册、用户管理必须共用同一张用户表、同一个用户实体、同一套 repository/mapper
4) 兼容当前前端真实入参与返回结构，特别是注册接口新增字段

【当前前端已固定的接口】
认证接口：
1) GET /auth/captcha
   - 返回：{ captcha: "ABCD" }
2) GET /auth/password-public-key
   - 返回：{ publicKey: "-----BEGIN PUBLIC KEY-----..." }
3) POST /auth/login
   - 入参：username, encryptedPassword, captcha
   - 说明：password 不是明文传输，而是前端先获取 RSA 公钥后加密，再以 encryptedPassword 提交
   - 成功返回：{ token, user }
4) POST /auth/register
   - 入参：username, password, initialPassword, displayName, phone, email, roleCode, status, enabled
   - 说明：这是扩展后的注册接口，不再是旧版的简单 username + password

用户管理接口：
5) GET /system/users
   - 查询参数：pageNo(默认1), pageSize(默认10), keyword(可选), status(可选), roleCode(可选)
   - 返回分页列表：{ list: UserListItemVO[], total: number }
6) POST /system/users
   - 入参：username, initialPassword, displayName, phone, email, roleCode, status, enabled, remark
7) PUT /system/users/{id}
   - 入参：username, displayName, phone, email, roleCode, status, enabled, remark
8) DELETE /system/users/{id}
9) PATCH /system/users/{id}/status
   - 入参：status(ENABLED/DISABLED), enabled(boolean，可选但建议兼容)
10) POST /system/users/{id}/reset-password
   - 入参：newPassword, forceChange(boolean)

【代码组织要求】
请按以下结构输出代码：
- controller.user.AuthController
- controller.user.SystemUserController
- service.user.AuthService
- service.user.SystemUserService
- repository.user.UserRepository 或 mapper.user.UserMapper
- domain.user.User
- dto.user.*
- vo.user.*
- enums.user.UserStatus / UserRoleCode 等
- common.exception / common.response / security.config 等配套代码

注意：
- AuthController 和 SystemUserController 必须在同一个 controller 包路径下，不要分散在 auth、system、admin 多个平级包里
- 可以拆分 service，但不要拆分用户实体
- 不要新增第二套用户表

【数据模型】
请生成 user 表（或等价实体），字段至少包含：
- id (Long, PK)
- username (varchar, 唯一)
- passwordHash
- displayName
- phone
- email
- roleCode (ADMIN/DEV/USER)
- status (ENABLED/DISABLED)
- enabled (boolean，可由 status 派生，也可落库，但需保持一致)
- forceChangePassword (boolean)
- lastLoginTime (datetime, nullable)
- remark
- createdAt
- updatedAt

【业务规则】
1) 登录与注册、用户管理共用同一张 user 表
2) username 唯一
3) 注册时：
   - username 必填
   - initialPassword 优先作为密码来源；若 initialPassword 为空但 password 有值，则兼容使用 password
   - 如果 password 和 initialPassword 同时传入且值不一致，返回 400
   - displayName、phone、email 可接收并保存
   - roleCode 默认 USER
   - status 默认 ENABLED
   - enabled 默认 true
   - status 和 enabled 若同时存在，必须语义一致
4) 登录时：
   - 先校验 captcha
   - 使用 RSA 私钥解密 encryptedPassword，再用 BCrypt 等方式校验 passwordHash
   - 登录成功后返回 token 和 user 信息
   - 更新 lastLoginTime
5) 新增用户时：
   - username 唯一校验
   - initialPassword 必填，长度至少 6 位
   - status 和 enabled 一致性校验
6) 编辑用户时：
   - 支持更新 username、displayName、phone、email、roleCode、status、enabled、remark
   - 如果更新 username，也要校验唯一
7) 删除用户时：
   - 默认管理员账号不可删除，例如 username=admin
   - 当前登录用户不能删除自己
8) 状态变更时：
   - 只允许 ENABLED / DISABLED
   - 若 enabled 传入，必须和 status 语义一致
9) 重置密码时：
   - newPassword 至少 6 位
   - 密码只保存加密后的 passwordHash
   - forceChange=true 时设置下次登录强制改密
10) 列表查询时：
   - 关键字匹配 username / displayName / phone / email
   - 支持按 status、roleCode 过滤
   - 按 createdAt DESC, id DESC 排序
11) 所有接口增加参数校验、统一异常处理、统一返回结构

【推荐返回 VO】
UserLoginVO:
- token
- user

UserProfileVO:
- id
- username
- displayName
- phone
- email
- roleCode
- status
- enabled
- forceChangePassword
- lastLoginTime

UserListItemVO:
- id
- username
- displayName
- phone
- email
- roleCode
- status
- enabled
- lastLoginTime
- createdAt
- remark

【统一返回格式】
统一返回：
{
  "code": "0",
  "message": "success",
  "data": ...
}
失败时 code 非 0，message 为可读错误信息。

【安全与实现要求】
1) 密码不允许明文入库
2) 登录密码采用“前端 RSA 公钥加密传输 + 后端私钥解密 + BCrypt 校验”
3) token 可用 JWT 或项目现有方案，但要与 Bearer Token 方式兼容
4) 若项目已有登录/注册代码，请直接在原基础上重构整合，不要重复造一套新逻辑

【代码输出要求】
1) 输出完整可运行代码，不要只给伪代码
2) 按 Controller / Service / Repository(Mapper) / Entity / DTO / VO / Exception / Config 分层输出
3) 提供 MySQL 8 建表 SQL
4) 提供至少 6 条初始化测试数据，包含 admin、dev、普通用户
5) 提供各接口 curl 示例
6) 给出如果项目已存在老的 AuthController / RegisterService / UserService 时，应该如何合并重构的说明
7) 关键逻辑补充必要注释
```

## 5. 可按需拆分的增量 Prompt

### 5.1 先做“认证 + 用户管理整合重构”

```text
请基于现有 Spring Boot 项目做一次“用户模块整合重构”：
1) 保留现有 URL：/auth/* 和 /system/users/*
2) 将登录、注册、用户管理 controller 统一收敛到同一个包路径，例如 controller.user
3) 登录、注册、用户管理共用同一个 User 实体、同一个 user 表、同一个 repository/mapper
4) 注册接口兼容当前前端最新入参：username, password, initialPassword, displayName, phone, email, roleCode, status, enabled
5) 输出重构后的包结构、关键代码和迁移说明
```

### 5.2 只补用户管理接口

```text
请在现有 Spring Boot 用户模块中补齐以下接口，并确保与现有登录/注册共用同一张 user 表：
1) GET /system/users
2) POST /system/users
3) PUT /system/users/{id}
4) DELETE /system/users/{id}
5) PATCH /system/users/{id}/status
6) POST /system/users/{id}/reset-password

要求：
- controller 放在 controller.user 包下
- service/repository/entity 与认证模块共用
- 返回结构统一为 { code, message, data }
- 列表结构为 data.list + data.total
```

### 5.3 只修注册接口字段兼容

```text
请为现有 Spring Boot 项目修正 POST /auth/register 接口，使其兼容前端最新注册请求：
{
  "username": "...",
  "password": "...",
  "initialPassword": "...",
  "displayName": "...",
  "phone": "...",
  "email": "...",
  "roleCode": "USER",
  "status": "ENABLED",
  "enabled": true
}

要求：
1) initialPassword 优先，password 作为兼容旧字段
2) 若 password 与 initialPassword 同时传入但不一致，返回 400
3) displayName、phone、email、roleCode、status、enabled 要能落库或正确映射
4) status 与 enabled 做一致性校验
5) 密码使用 BCrypt 加密保存
6) 返回统一结构 { code, message, data }
```

## 6. 联调备注

- 前端登录页依赖 `GET /auth/captcha` 和 `GET /auth/password-public-key`，这两个接口不能漏。
- 前端登录成功后要求 `data.token`、`data.user`，否则无法写入本地登录态。
- 前端注册页当前已经传扩展字段，后端不要再按旧版简化注册 DTO 处理。
- 前端用户管理页优先解析 `data.list` 和 `data.total`。
- 前端当前登录后的业务请求会自动带 `Bearer Token`。
