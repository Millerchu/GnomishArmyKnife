# 密码备忘录后端接口代码生成提示词

本文档用于把前端已预留的“密码备忘录”接口一次性生成为后端代码。  
前端项目已固定以下接口路径（基于 `/api` 代理后的实际后端路径）：

- `GET /password-memos`：分页查询密码备忘录列表
- `POST /password-memos`：新增密码备忘录
- `PUT /password-memos/{id}`：编辑密码备忘录
- `DELETE /password-memos/{id}`：删除密码备忘录
- `GET /password-memos/{id}`：查询单条密码备忘录详情
- `POST /password-memos/{id}/verify-access`：输入当前用户密码后校验并返回完整密码

## 1. 推荐统一返回结构

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
- 列表接口返回 `data.list` + `data.total`。
- 详情接口返回 `data` 为单个对象。
- 校验查看密码接口返回 `data.password`。
- 失败时返回清晰 `message`，前端会直接展示。

## 2. 可直接投喂 AI 的全量生成 Prompt

```text
你是一名资深 Java 后端工程师，请基于 Spring Boot 3 + Spring Web + Spring Validation + Spring Security + Spring Data JPA（或 MyBatis）生成“密码备忘录模块”完整代码，要求可直接运行，并且具备基础安全性。

【目标】
实现以下 REST 接口（基础路径 /password-memos）：
1) GET /password-memos
   - 查询参数：pageNo(默认1), pageSize(默认10), keyword(可选)
   - 关键字匹配：siteName、siteUrl、username、registeredPhone、registeredEmail
   - 返回分页列表：{ list: PasswordMemoListItem[], total: number }
   - 列表中不要直接返回完整密码，可返回空值、掩码值，或根本不返回 password 字段
2) POST /password-memos
   - 入参：siteName, siteUrl, username, password, registeredPhone, registeredEmail, remark
3) PUT /password-memos/{id}
   - 入参：siteName, siteUrl, username, password, registeredPhone, registeredEmail, remark
4) DELETE /password-memos/{id}
5) GET /password-memos/{id}
   - 返回详情：siteName, siteUrl, username, registeredPhone, registeredEmail, remark, createdAt, updatedAt
   - 默认不要直接返回完整 password；如确需返回，请增加字段 `maskedPassword`
6) POST /password-memos/{id}/verify-access
   - 入参：loginPassword
   - 逻辑：校验当前登录用户密码正确后，返回该条记录完整密码
   - 返回：{ password: "明文密码" }

【安全要求】
- 密码备忘录本身保存的是“用户在第三方网站/App上的账号密码”，请使用服务端可逆加密存储，例如 AES-GCM
- 严禁明文裸存到数据库
- 当前登录用户密码校验请复用系统登录密码校验逻辑，例如通过 Spring Security PasswordEncoder 比对当前用户 passwordHash
- 只有记录所属人本人可以查询、修改、删除和查看完整密码
- GET /password-memos 和 GET /password-memos/{id} 默认都不直接返回完整密码
- 只有 POST /password-memos/{id}/verify-access 在校验当前用户登录密码成功后，才返回完整密码
- 查看完整密码的行为建议记录审计日志（用户ID、记录ID、时间、IP）

【数据模型】
请生成 password_memo 表，字段至少包含：
- id (Long, PK)
- ownerUserId (Long, 记录所属用户ID)
- siteName
- siteUrl
- username
- passwordCiphertext
- passwordNonce
- registeredPhone
- registeredEmail
- remark
- createdAt
- updatedAt

如果你选择把加密元数据合并存储，也可以设计为：
- passwordEncrypted
- encryptionVersion

【业务规则】
- siteName 必填，长度不超过 64
- siteUrl 必填，长度不超过 255
- password 必填，长度不超过 128
- username、registeredPhone、registeredEmail 至少填写一项
- 列表只返回当前登录用户自己的记录
- 删除和修改时校验记录归属
- keyword 为空时返回当前用户全部记录（分页）
- 返回统一结构：{ code, message, data }
- 增加参数校验、统一异常处理、统一响应封装

【推荐返回 VO】
PasswordMemoListItem:
- id
- siteName
- siteUrl
- username
- registeredPhone
- registeredEmail
- updatedAt

PasswordMemoDetailVO:
- id
- siteName
- siteUrl
- username
- registeredPhone
- registeredEmail
- remark
- maskedPassword
- createdAt
- updatedAt

VerifyAccessResponse:
- password

【代码输出要求】
- 按 Controller / Service / Repository(Mapper) / Entity / DTO / VO / Security / Exception / Config 分层输出
- 提供 MySQL 8 建表 SQL
- 提供 AES 加解密工具类
- 提供至少 5 条测试数据，数据归属于 2 个不同用户
- 提供 curl 示例
- 补充关键注释，不要只给伪代码
```

## 3. 单接口增量 Prompt

### 3.1 查看密码校验接口

```text
请为现有 Spring Boot 项目新增 POST /password-memos/{id}/verify-access 接口。
入参 JSON:
{
  "loginPassword": "当前登录用户密码"
}

要求：
1) 从登录态中拿到当前用户ID
2) 查询该 id 对应的密码备忘录，校验 ownerUserId 必须等于当前用户ID
3) 使用 PasswordEncoder 校验 loginPassword 与当前用户 passwordHash
4) 校验通过后，解密该条记录保存的第三方账号密码并返回：
   { code: "0", message: "success", data: { password: "..." } }
5) 校验失败返回明确 message，例如“当前用户密码错误”
6) 记录审计日志
7) 给出 Controller + Service + DTO + VO + 单元测试
```

### 3.2 列表接口

```text
请为现有 Spring Boot 项目新增 GET /password-memos 分页接口。
查询参数：pageNo, pageSize, keyword
要求：
1) 只查询当前登录用户自己的记录
2) 关键字匹配 site_name、site_url、username、registered_phone、registered_email
3) 返回 data.list + data.total
4) 列表不要返回完整 password，只返回业务展示字段
5) 给出 Controller + Service + Mapper/Repository + VO + 单元测试
```

## 4. 联调约定

- 前端接口文件固定为 `src/api/passwordMemo.js`
- 前端页面固定路由为 `/password-memo`
- 前端在后端不可用时会自动回退本地演示数据
- 前端详情弹窗默认只显示掩码密码，输入当前用户密码后才会调用 `POST /password-memos/{id}/verify-access`
- 如果后端返回结构不是 `data.list/data.total` 或校验接口不是 `data.password`，请同步调整前端解析逻辑
