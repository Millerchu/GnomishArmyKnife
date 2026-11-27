# GnomishArmyKnife (GAK) 后端全面代码评审报告

> **评审日期**：2026-11-26（**整改更新**：2026-11-27 完成全部 5 项 P0 严重问题修复）
>
> **评审基线**：`repos/backend`（分支 `main`，Java 21 / Spring Boot 3.4.1 / MyBatis Plus 3.5.9 / PostgreSQL）  
> **评审范围**：`gak-framework`、`gak-start`（含 `schema.sql` 与配置项）、以及 `gak-modules/*` 全部 21 个业务模块（共 **544 个主源码文件 / 45,371 行**，**76 个测试文件 / 11,058 行**；`gak-common` 当前为空模块）  
> **评审依据**：仓库 `AGENTS.md` 工程与类设计规范、《阿里巴巴 Java 开发手册（1.4.0）》及现代 Java 21 / Web 安全最佳实践

---

## 一、执行摘要

### 1.1 总体结论

后端代码整体分层清晰、业务建模扎实，且在近期演进的标杆模块（如 `gak-system-log`、`gak-workbench`、`gak-message`、`gak-growth-record`）中展现了很强的并发控制、脱敏审计和防御式编程水平。原评审发现的 **5 项 P0 级严重安全漏洞已于 2026-11-27 全部修复并通过单元测试与 MockMvc 权限回归验证**；当前剩余历史债主要集中在 P1–P3 维度的“统一应用鉴权、大表分页与 N+1 查询、缓存与事务一致性、代码体量与类设计规范”。

### 1.2 构建与测试基线

- **2026-11-26 初评基线**：在 JDK 21 环境下执行 `.\mvnw.cmd test`，26 个 Reactor 模块 **BUILD SUCCESS**（01:12），单元测试总数 **467**（失败 0 / 错误 0），跳过 **45**（`gak-start` 24/25、`gak-message` 11/11、`gak-system-log` 8/65、`gak-workbench` 2/82）。静态 hygiene：`printStackTrace`: 0 / `System.out`: 0 / `// TODO`: 0；但存在 63 处内部类/嵌套记录、2 处 `Executors.new*`、1 处 `new Thread`。
- **2026-11-27 P0 修复后验证**：执行 `.\scripts\backend-dev.ps1 test`（`.\mvnw.cmd test`），26 个 Reactor 模块 **BUILD SUCCESS**（01:29），总测试数 **486**，实际执行 **441**（失败 0 / 错误 0），跳过 **45**（与既有条件集成测试一致）。

### 1.3 问题严重级别分布

| 级别 | 定义 | 数量 | 当前状态 | 核心聚焦点 |
| --- | --- | --- | --- | --- |
| **P0 严重** | 匿名/普通用户可直接越权接管系统、窃取敏感数据或离线解密密文 | **5** | **5/5 已修复** | 后台用户管理管理员鉴权、Token 过期/吊销与启用态校验、医疗报告下载鉴权归属、密码备忘录强制外置密钥与 AAD、WoW 共享指南管理员鉴权 |
| **P1 高危** | 权限绕过、同源 XSS、ZIP 炸弹/路径风险、严重 N+1/万级 UPDATE、数据与缓存一致性缺陷 | **25** | 待修复 | 10+ 模块缺应用级权限校验、改密无鉴权防爆破、SVG XSS、ZIP 炸弹与路径边界、AI 同步万级 UPDATE、油耗/知识库/乐器性能瓶颈、字典缓存脏读、SSE 线程安全 |
| **P2 中等** | 内存分页扩展性瓶颈、并发竞态、可空字段无法置空、异常日志缺失、索引缺口 | **16 类** | 待修复 | 12+ 模块全表 `selectList` 内存分页、`GlobalExceptionHandler` 不记 500 堆栈、MyBatis-Plus `NOT_NULL` 置空失效、多处并发竞态与索引缺口 |
| **P3 建议** | 巨型类治理、违反内部类规范、文案/格式/微优化、测试覆盖缺口 | **4 类** | 待修复 | `WowCharacterService`(1872行)/`WorkLogService`(1012行)、公开嵌套 record/Payload 违反 `AGENTS.md`、多模块缺少 Controller/核心 Service 单测 |

---

## 二、P0 严重问题（已完成修复）

### P0-1：`gak-user` 后台用户管理全部 6 个接口缺失管理员权限校验（垂直越权 / 全站接管） — `[已修复 2026-11-27]`

- **代码位置**：
  - `gak-modules/gak-user/src/main/java/com/gak/user/controller/user/SystemUserController.java:41-86`
  - `gak-modules/gak-user/src/main/java/com/gak/user/service/user/SystemUserService.java:51-183`
- **问题现象与风险**：
  - `/system/users` 下全部 6 个管理接口（分页查全部用户、创建用户、编辑用户角色/状态、删除用户、启停用户、重置任意用户密码）**仅调用 `tokenService.requireCurrentUserId(httpServletRequest)` 校验登录态**，Controller 与 Service 均未校验操作者是否为 `ADMIN` 且处于启用状态。
  - 叠加 `/auth/register` 对外开放自助注册与 `/auth/captcha` 明文返回验证码，任意匿名攻击者可：① 注册普通 `USER` 账号 → ② 登录获取 Token → ③ 调用 `PUT /system/users/{ownId}` 将自身提权为 `ADMIN`，或调用 `POST /system/users/{adminId}/reset-password` 接管默认管理员账号，导致全站沦陷。
- **修复建议**：
  1. 在 `SystemUserService` 所有管理方法入口统一校验操作者存在、已启用（`enabled == true && "ENABLED".equals(status)`）且 `roleCode == ADMIN`；
  2. 禁止降级/禁用默认 `admin` 账号以及当前登录管理员自身；
  3. 补充普通用户访问 `/system/users/**` 返回 `403` 的集成测试。
- **整改记录（2026-11-27）**：
  - `SystemUserController` 全接口透传 `currentUserId`；`SystemUserService` 新增 `requireAdminOperator` 与受保护账号校验，禁止降级/禁用默认 `admin` 与当前登录管理员自身，并在禁用/删除/改角色/重置密码时吊销目标用户令牌；补充 `SystemUserServiceTest` 与 `SystemUserControllerSecurityTest`（MockMvc 验证未登录 401、普通用户 403、停用管理员 401）。

### P0-2：`gak-user` Token 无过期时间、无吊销机制且鉴权不校验账号启用状态（权限永久残留 + OOM） — `[已修复 2026-11-27]`

- **代码位置**：
  - `gak-modules/gak-user/src/main/java/com/gak/user/service/user/TokenService.java:20-38`
  - `gak-modules/gak-user/src/main/java/com/gak/user/service/user/AuthService.java:116-138`
  - `gak-modules/gak-user/src/main/java/com/gak/user/service/user/SystemUserService.java:144-183`
- **问题现象与风险**：
  - `tokenStore` 为无界 `ConcurrentHashMap<String, Long>`，既无 TTL 过期与定时淘汰（长期运行持续增长直至 OOM），也无 `logout` / `revokeByUserId` 吊销能力。
  - 管理员禁用/删除用户、重置密码，或用户修改密码后，原有 Token 仍永久有效（直至 JVM 重启）。
  - `TokenService.requireCurrentUserId()` 仅查内存 Map，不检查账号是否仍存在及启用；除 `gak-system-log`、`gak-message`、`gak-ai-subscription` 等少数模块额外检查了启用状态外，大量业务与管理接口（含 `PermissionManagementService`、`SystemAppService`、`DataDictionaryService`、`DataMigrationAdminGuard` 等）均未校验启用状态，被禁用账号仍可继续读写。
- **修复建议**：
  1. 为 Token 引入绝对过期 + 滑动续期机制与容量上限（如 Caffeine 缓存），提供 `revokeToken(token)` 和 `revokeByUserId(userId)`；
  2. 在用户登出、禁用、删除、改密、重置密码时立即吊销目标用户全部 Token；
  3. 在统一认证层（建议收敛为全局 `HandlerInterceptor` 默认拦截 + 显式白名单）校验用户有效性与启用状态。
- **整改记录（2026-11-27）**：
  - 改造 `TokenService`（配合独立会话元数据类 `TokenSession`），支持 24h 绝对过期、2h 滑动续期、10000 容量淘汰、`revokeToken`/`revokeRequestToken`/`revokeByUserId`，并在校验时实时检查 `UserMapper` 用户存在性与启用态（失效即吊销并抛 401）；`AuthController` 新增幂等 `POST /auth/logout`；`AuthService.changePassword` 拦截禁用账号并在改密后吊销该用户全部令牌；补充 `TokenServiceTest`、`AuthServiceTest`、`AuthControllerLogoutTest`。统一全局 `HandlerInterceptor` 收口继续留在 P1-1 推进。

### P0-3：`gak-health-record` 医疗报告文件下载接口完全未鉴权（匿名下载他人敏感医疗文件） — `[已修复 2026-11-27]`

- **代码位置**：
  - `gak-modules/gak-health-record/src/main/java/com/gak/healthrecord/controller/HealthRecordController.java:128-134`
  - `gak-modules/gak-health-record/src/main/java/com/gak/healthrecord/service/HealthRecordService.java:310-324`
- **问题现象与风险**：
  - `GET /health-records/report-files/{fileName:.+}` 方法签名甚至未接收 `HttpServletRequest`，完全未调用 `tokenService.requireCurrentUserId`，也未校验该文件是否归属当前用户。
  - 任意未登录用户只要获知或枚举文件名即可直接下载任意用户的体检报告、病历等高度敏感个人医疗文件。
- **修复建议**：
  1. 若需保留历史兼容路径，必须先校验登录态 + `APP_HEALTH_RECORD` 权限，再查询数据库确认该 `fileName` 属于当前用户的 `HealthReport.reportUrl` 或 `HealthVisit.caseRecordUrl`；
  2. 长期建议彻底下线该直链下载接口，统一收口至带 `AttachmentBusinessAccessPolicy` 保护的通用附件下载服务。
- **整改记录（2026-11-27）**：
  - `HealthRecordController.getReportFile` 增加 `HttpServletRequest` 登录鉴权并附带 `Content-Disposition: attachment`、`X-Content-Type-Options: nosniff`、`Cache-Control: no-store`；`HealthRecordService.loadReportFile` 校验用户启用态、`APP_HEALTH_RECORD` 应用授权及数据库文件归属（`HealthReportMapper.existsOwnedReportFile` / `HealthVisitMapper.existsOwnedCaseRecordFile`），拆出独立 `HealthReportFileResource`；补充 `HealthRecordServiceTest` 与 `HealthRecordReportFileControllerTest`。

### P0-4：`gak-password-memo` 硬编码默认 AES-256 密钥且主配置文件未覆盖（全量密码可离线解密） — `[已修复 2026-11-27]`

- **代码位置**：
  - `gak-modules/gak-password-memo/src/main/java/com/gak/passwordmemo/service/PasswordMemoCryptoService.java:24-35`
  - `gak-start/src/main/resources/application.properties`（未配置 `gak.security.memo.aes-key`）
- **问题现象与风险**：
  - 源码中硬编码了默认密钥 `DEFAULT_AES_KEY_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="`（解码为 `0123456789abcdef0123456789abcdef`）作为 `@Value` 兜底值，且 `application.properties` 未显式覆盖该属性。
  - 任何漏配环境变量的部署都会静默使用该公开密钥加密所有用户的密码备忘录；结合 `gak-data-migration` 导出包包含全部密文与 nonce，任何获取导出包或数据库备份的人均可直接离线解出全部明文密码。
- **修复建议**：
  1. 移除源码中的硬编码默认密钥，改为强制外部注入（`${GAK_PASSWORD_MEMO_AES_KEY}`），启动时若缺失或命中已知弱密钥则 fail-fast 拒绝启动；
  2. 参考 `AiSyncCrypto` 的做法，在 AES-GCM 加密时绑定 `AAD`（如 `ownerUserId:memoId`），防止密文在不同记录或不同用户间被替换复用；
  3. 制定旧密钥数据重加密迁移方案并在生产环境轮换密钥。
- **整改记录（2026-11-27）**：
  - 移除 `PasswordMemoCryptoService` 硬编码默认密钥，改为强制读取 `${gak.security.memo.aes-key:}`（即 `GAK_PASSWORD_MEMO_AES_KEY`），缺失、格式非法或命中已知弱密钥时启动 fail-fast；新增 `v2:` 密文格式并在 AES-GCM 中绑定 `ownerUserId:memoId` AAD，拆出独立 `EncryptedPasswordPayload`；`PasswordMemoService` 在新建备忘录时预生成 `memoId` 并在全链路加解密传入 `(ownerUserId, memoId)`；`application.properties` 与 NAS 部署模板 `deploy/nas/docker-compose.yml`、`.env.example` 已同步声明 `GAK_PASSWORD_MEMO_AES_KEY`（测试配置仅在 `gak-start/pom.xml` surefire 与 `GakStartApplicationTests` 注入）。生产库如存有旧密钥密文，仍需在切换前按旧密钥解密并用新密钥 + `v2:` AAD 重加密轮换。

### P0-5：`gak-wow-character` 全局共享职业专精指南更新接口缺失管理员鉴权 — `[已修复 2026-11-27]`

- **代码位置**：
  - `gak-modules/gak-wow-character/src/main/java/com/gak/wowcharacter/controller/WowSpecializationGuideController.java:40-46`
  - `gak-modules/gak-wow-character/src/main/java/com/gak/wowcharacter/service/WowSpecializationGuideService.java:36-57`
- **问题现象与风险**：
  - `gak_wow_specialization_guide` 是全站用户共享表（无 `owner_user_id`），但 `PUT /wow-specialization-guides/{id}` 仅校验了登录态，未校验管理员身份。
  - 任意普通登录用户均可篡改全站所有用户看到的职业天赋、导入码、手法说明和外链 `sourceUrl`（可替换为钓鱼/恶意站点）。
- **修复建议**：
  - 在 `WowSpecializationGuideService.update()` 接收 `currentUserId` 并校验管理员角色（及启用状态），非管理员返回 `403 FORBIDDEN`。
- **整改记录（2026-11-27）**：
  - `WowSpecializationGuideController.update` 透传 `currentUserId`；`WowSpecializationGuideService` 注入 `UserMapper` 并校验操作者存在、已启用且为 `ADMIN`（否则返回 401/403）；补充 `WowSpecializationGuideServiceTest` 与 `WowSpecializationGuideControllerTest`。

---

## 三、P1 高危问题（优先排期修复）

### 3.1 认证、权限与数据保护

| 编号 | 模块 | 代码位置 | 风险现象 | 修复建议 |
| --- | --- | --- | --- | --- |
| **P1-1** | 跨 10+ 模块 | `PersonalBillService:83-247`、`FuelRecordService:179-184`、`FuelVehicleService:111-116`、`HealthRecordService:571-576`、`WorkLogController:50-164`、`WowCharacterController:42-129`、`TodoItemController:39-90`、`KnowledgeBaseController:39-95`、`InstrumentPracticeTakeController:36-53` 及多处 `AttachmentAccessPolicy` | **应用级权限校验大面积缺失**：仅校验登录态而未校验 `gak_user_app_permission` 授权，未获授权用户可直接绕过前端导航守卫调用 REST API（违反 `AGENTS.md:33`）。另 `FuelRecordController:105-109` 的 `GET /fuel-records/latest-prices` 连登录态都未校验，可被匿名刷第三方聚合数据配额。 | 在 `gak-framework` 或权限模块抽离统一的 `@RequireAppPermission` + `HandlerInterceptor` 强制校验登录态、账号启用态与应用授权；`/fuel-records/latest-prices` 补齐登录与权限校验。 |
| **P1-2** | `gak-user` | `AuthController.java:64-68`、`AuthService.java:116-138` | **`POST /auth/change-password` 无认证、无验证码、无限流**：凭 `username + oldPassword` 即可高频试错，且明确区分 `AUTH_INVALID` / `PASSWORD_UNCHANGED`，可完全绕过登录验证码离线爆破任意账号密码，且允许已禁用账号改密。 | 正常改密要求已登录且仅改本人密码；若需“强制改密前未登录态改密”，应由登录流程签发短期一次性改密票据 + 验证码 + 失败限流，并校验账号启用态。 |
| **P1-3** | `gak-user` | `AuthController.java:42-47`、`AuthService.java:82-113` | **验证码明文返回且登录无限流**：`GET /auth/captcha` 直接在 JSON 中返回明文验证码，自动化脚本可直接解析绕过；登录接口缺少按账号/IP 的失败锁定。 | 改为服务端渲染图形验证码（Base64 PNG）不再返回明文；增加登录失败计数与冷却锁定策略。 |
| **P1-4** | `gak-user` | `AuthService.java:90-98, 117-129` | **用户名枚举漏洞**：先查用户不存在直接抛 `AUTH_INVALID`，后解密密文失败抛 `PASSWORD_DECRYPT_FAILED`，提交非法密文即可精确区分用户名是否存在。 | 先解密密文再查用户；对不存在用户执行虚拟 BCrypt 校验以平衡响应耗时。 |
| **P1-5** | `gak-user` / `gak-start` | `AuthService.java:83-113`、`schema.sql:954-974` | **`forceChangePassword` 未在后端强制拦截**：重置密码设置 `forceChange=true` 后登录仍签发全权限 Token；且 `schema.sql` 默认 `admin` 种子账号 `force_change_password = FALSE`。 | `forceChangePassword=true` 时仅签发受限改密 Token 或拦截除改密外全部接口；新建库默认 `admin` 设为强制首次改密。 |
| **P1-6** | `gak-start` | `gak-start/src/main/resources/application.properties:5-7` | **配置文件硬编码数据库密码**：`url` 与 `username` 均采用 `${ENV:...}` 占位符，而第 7 行 `spring.datasource.password` 为明文字面量（违反 `AGENTS.md:12`）。 | 改为 `${SPRING_DATASOURCE_PASSWORD:}` 并通过环境变量或未跟踪本地配置注入，同时轮换凭据。 |
| **P1-7** | `gak-permission-management` / `gak-attachment` | `SystemAppIconService.java:42-48, 122-126`、`SystemAppController.java:107-113`、`AttachmentService.java:334-336, 354`、`AttachmentController.java:91-101` | **SVG 上传未清洗且 `inline` 直出导致同源存储型 XSS**：应用图标上传仅信请求头 `Content-Type` 且免鉴权直接返回 `image/svg+xml`；旧附件对所有 `image/*`（含 SVG）返回 `Content-Disposition: inline`，未加 `CSP: sandbox` 与 `nosniff`，同源打开即可执行脚本窃取 Token。 | 禁止上传 SVG 或对 SVG 强制 `attachment` 下载，并统一附加 `Content-Security-Policy: default-src 'none'; sandbox` 与 `X-Content-Type-Options: nosniff`。 |
| **P1-8** | `gak-attachment` | `AttachmentFileProcessor.java:157-163`、`AttachmentService.java:75` | **IMAGE/ICON 上传跳过扩展名一致性校验**：`validateAllowedType` 在图片/图标分支仅校验魔数 MIME 即 `return`，跳过 `extensionMatches`，可上传伪装图片头的 `.html`/`.exe` 并按原后缀落盘。 | 所有用途统一执行扩展名与真实 MIME 匹配校验，且落盘 `objectKey` 强制使用规范推导扩展名而非用户原始后缀。 |
| **P1-9** | `gak-data-migration` | `DataMigrationArchiveService.java:49, 58-63, 98-123, 156-179, 221-228`、`DataMigrationService.java:596-602`、`LocalDataMigrationPackageStorageService.java:83-86`、`AttachmentMigrationSupport.java:53` | **ZIP 炸弹 + 大文件 OOM + 路径遍历边界不足**：`unzip()` 无总字节/单文件/条目上限（配合 200MB 上传易打满磁盘）；`updateDigest` 用 `Files.readAllBytes` 易 OOM；`extractPackage` 异常/多层目录时外层 `workspace` 残留；`normalizePackageName` 未过滤 `..`；`LocalDataMigrationPackageStorageService.resolve` 与 `buildExportPackage` 未校验路径位于根目录内。 | 解压改为定长 buffer 拷贝并限制总大小与条目数；SHA-256 改为流式计算；记录并在 `finally` 清理 `workspaceDir`；所有路径拼接严格校验 `normalized.startsWith(root)` 且禁止 `..`。 |
| **P1-10** | `gak-password-memo` | `PasswordMemoService.java:250-259`、`PasswordMemoController.java:127-133` | **二次鉴权 `verifyAccess` 缺少防爆破限流，且无条件信任 `X-Forwarded-For`**：窃取 Token 后可无限试探登录密码解出全部备忘录明文；攻击者可伪造审计日志 IP。 | 按 `userId` 增加失败计数与冷却锁定；仅在受信任反代环境下解析 `X-Forwarded-For`。 |
| **P1-11** | `gak-requirement-board` | `RequirementBoardService.java:198-220` | **进度流转接口存在越权篡改且无状态机约束**：`updateProgress()` 未校验操作者是管理员还是创建者，任意登录用户可随意改写他人需求状态，且允许任意状态互跳。 | 明确并校验角色权限（如仅管理员推进/驳回、创建者仅允许取消），并在 `RequirementStatus` 中定义合法流转白名单。 |
| **P1-12** | `gak-worklog` | `WorkLogController.java:59-65`、`WorkLogService.java:159-166` | **任意登录用户可直接污染全局公共数据字典**：`POST /work-logs/projects` 无应用权限/角色/频控限制，直接向全局数据字典新增启用项。 | 补齐 `APP_WORK_LOG` 权限与频控，或改为按用户隔离的项目配置。 |
| **P1-13** | `gak-data-dictionary` / `gak-data-migration` | `DataDictionaryService.java:487-496`、`DataMigrationAdminGuard.java:23-32` | **管理员鉴权未校验账号启用状态**：仅检查 `roleCode == ADMIN`，已停用管理员持有效 Token 仍可导出全库或修改数据字典。 | 统一校验 `enabled == true && "ENABLED".equals(status)`。 |

### 3.2 性能、资源与稳定性

| 编号 | 模块 | 代码位置 | 风险现象 | 修复建议 |
| --- | --- | --- | --- | --- |
| **P1-14** | `gak-ai-subscription` | `AiSyncWorker.java:71-84`、`AiUsageService.java:30-43, 49-52` | **官方同步循环内反复全量 `reconcile` 触发万级 DB UPDATE**：持有 `owner` 行锁期间，每 upsert 一条官方费用就全量加载历史用量、执行 $O(N^2)$ 分类并对全部记录无差别逐条 `updateById`。500 条记录×35 天将触发约 18,000 次 UPDATE，极易长事务阻塞并导致租约超时。 | 改为批量 `replaceOfficialCostWindow` 完成后仅调用 1 次 `reconcile(owner)`；且仅对 `status/reason` 实际发生变化的记录执行更新。 |
| **P1-15** | `gak-ai-subscription` | `OpenAiCostsClient.java:32-35, 56, 84-87`、`GrokCostsClient.java:26-27, 45, 56, 69` | **外部 HTTP 客户端无响应体上限、OpenAI 金额浮点精度丢失、时间解析异常误重试**：两处均用 `BodyHandlers.ofString()` 无大小上限；`OpenAiCostsClient` 未开启 `USE_BIG_DECIMAL_FOR_FLOATS` 且未校验负金额；`GrokCostsClient` `Instant.parse` 异常未被捕获而被误判为可重试错误。 | 使用限流流式读取限制最大响应体（如 2MB）；`OpenAiCostsClient` 开启 `USE_BIG_DECIMAL_FOR_FLOATS` 并校验非负；`GrokCostsClient` 捕获 `DateTimeParseException` 转为不可重试失败。 |
| **P1-16** | `gak-fuel-stats` | `FuelRecordService.java:84, 153, 171, 212, 224-248, 323-324` | **全量 N+1 查附件**：`page`/`getSummary`/`getReports`/`getById`/`create`/`update` 全部先 `buildDerivedRecords(loadAllRecords)`，并在循环中对每条历史记录查附件。500 条记录的 `summary` 或 `getById` 会触发 501 次 SQL。 | 剥离 `toRecordVO` 中的附件查询；`summary`/`reports` 不查附件，`page` 仅对当前页记录批量查附件。 |
| **P1-17** | `gak-wow-character` | `WowCharacterService.java:199-213, 386-405, 418-459, 546-562, 1253-1401` | **严重 N+1 与 `overview()` 无效全量加载**：列表渲染对每个角色重复查字典与附件；`overview()` 拉取全部角色的 4 张子表却只取前 4 个主角色；周重置与赛季归档在循环中逐条查改库。 | 渲染前批量预加载字典与附件；`overview()` 仅按最多 4 个主角色 ID 查询子表；重置/归档改为批量操作。 |
| **P1-18** | `gak-knowledge-base` | `KnowledgeBaseController.java:97-100`、`KnowledgeBaseService.java:57-71, 166-179, 202-233, 310-318` | **匿名接口 `/public-highlights` 全表扫描 + 丢弃式 N+1 查附件（DoS 风险）**：免登录接口每次拉全表（含 2000 字正文）内存洗牌，并对每条记录查完附件后再调用 `withoutAttachments` 全部丢弃。 | `publicHighlights()` 独立构建不查附件的 VO，改为数据库抽样 + 短期本地缓存；`page()` 改为 DB 分页 + 批量查附件。 |
| **P1-19** | `gak-instrument-practice` | `InstrumentPracticeTakeService.java:63-81, 115-123` | **`list()` 与 `create()` 全量加载超大 JSON 演奏事件**：单条录音允许 20,000 事件（可达数 MB）；`create()` 仅为了计数删最旧记录却全量拉出 30 条大 JSON；`list()` 一次返回最多 150 段完整事件 JSON，响应体可达数十 MB，且并发创建可突破 30 条上限。 | `create()` 仅投影 `id` 列并清理全部超出上限的旧记录；`list()` 拆分为元数据列表 + 按 `takeId` 加载事件详情。 |

### 3.3 并发安全与数据/缓存一致性

| 编号 | 模块 | 代码位置 | 风险现象 | 修复建议 |
| --- | --- | --- | --- | --- |
| **P1-20** | `gak-data-dictionary` / `gak-data-migration` | `DataDictionaryService.java:137, 155, 178, 199, 242, 267, 282, 307`、`DataDictionaryUsageSupportImpl.java:102`、`SystemDictionariesMigrationHandler.java:89-210`、`LocalDataDictionaryCacheSupport.java:16-27` | **缓存脏读永久驻留 + 迁移导入绕过清缓存 + 缓存可变对象暴露**：写事务在提交前清缓存，并发读易回填提交前旧值到无 TTL 的 `ConcurrentHashMap`；迁移导入字典完全未清缓存导致重启前不生效；`getBinding` 返回可变 VO 实例可被调用方污染。 | 所有写操作与迁移导入统一通过 `afterCommit` 清缓存；本地缓存引入短 TTL（Caffeine）；返回防御性拷贝或不可变对象。 |
| **P1-21** | `gak-attachment` | `AttachmentCleanupService.java:49-61` | **定时物理清理与并发绑定竞态误删已激活文件**：先删磁盘文件再 `updateById` 覆写状态，临近 24h 的 `PENDING` 附件若刚好被绑定为 `ACTIVE` 将被误删；另单次 `LIMIT 200` 无循环分批会导致堆积。 | 先条件更新（`WHERE id = ? AND status = 'PENDING' AND created_at < ?`）原子占位成功后再删物理文件；定时任务改为有上限的循环分批处理。 |
| **P1-22** | `gak-message` | `MessageStreamService.java:30-35, 69-90, 105-115` | **SSE 并发写/关闭线程安全与心跳任务异常静默终止**：虚拟线程 `emitter.send()` 与调度线程超时 `close()->complete()` 并发操作易抛 `IllegalStateException`；`close()` 未捕获异常导致 `shutdown()` 可能提前中断且非守护线程阻塞停机；`scheduleAtFixedRate` 无 `try-catch` 一旦异常将永久停止心跳。 | `close()` 包裹 `try-catch`；心跳回调最外层加 `try-catch` 记日志；自定义命名线程工厂；`shutdown()` 使用 `try-finally` 确保两个线程池必然关闭。 |
| **P1-23** | `gak-travel-record` | `TravelBillSyncService.java:113, 118`、`PersonalBill.java:29` | **行程清空承运商无法同步清空账单商户名**：`updateById` 默认 `NOT_NULL` 策略忽略 `merchantName = null`，导致跨模块数据不一致（同仓 `FuelBillSyncService`/`HealthVisitBillSyncService` 已用 `UpdateWrapper.set` 规避）。 | 改用 `UpdateWrapper` 显式 `.set("merchant_name", ...)` 或在字段上标注 `FieldStrategy.ALWAYS`。 |
| **P1-24** | `gak-wow-character` | `WowCharacterService.java:231-238, 253-260, 1382-1399` | **`create()`/`update()` 返回的低保 `id` 为 `null` 且丢失刚绑定的附件**：`syncWeeklyVaults()` 落库后未返回持久化实体，响应重新用入参构建导致新建低保 `id == null`、附件查询为空。 | 让 `syncWeeklyVaults()` 返回落库后的实体列表并直接用于组装 VO。 |
| **P1-25** | `gak-permission-management` | `PermissionManagementController.java:104-114`、`SystemAppController.java:115-125`、`PermissionManagementService.java:222-240, 578-581`、`schema.sql:551-574` | **超长 `User-Agent`/`X-Forwarded-For` 触发 500 阻断首页 + 高频读接口写审计表**：未按库表列长截断 `ip(64)` 和 `user_agent(255)`，超长请求头直接触发 PostgreSQL 截断异常导致首页加载或权限保存回滚；`GET /current-user/apps` 每次读取都落库整份目录 JSON。 | 统一安全截断 `ip`/`userAgent`；移除或降采样高频读接口 `QUERY_CURRENT_USER_APPS` 的审计写库。 |

---

## 四、P2 中等问题（按共性专题归类）

### 4.1 全表加载与内存分页泛滥（跨 12 个模块的系统性瓶颈）

系统未全局启用 MyBatis-Plus `PaginationInnerInterceptor`，导致绝大多数分页和统计接口采用“全量 `selectList` 拉入 JVM 内存 → `subList` 分页或内存聚合”的模式，且多处 `pageNo`/`pageSize` 缺少 `@NotNull` / `@Max` 上限，存在 OOM 与 `(pageNo - 1) * pageSize` 强转 `int` 溢出风险：

- **分页接口内存化**：`SystemUserService.page`（`gak-user:72-83`）、`PermissionManagementService.pageUsers`（`128-142`）、`SystemAppService.page`（`114-128`）、`PersonalBillService.page`（`83-101`）、`HealthRecordService.pageRecords/pageReports/pageVisits`（`107-192`）、`PasswordMemoService.page`（`94-106`，还查出全部密文列且缺 `@Max`）、`DataDictionaryService.page`（`100-114`）、`DataMigrationService.pageTasks`（`122-133`）、`RequirementBoardService.page`（`100-112`）、`WowCharacterService.page`（`183-198`）、`TodoItemService.page`（`96-104`，且全量 `ids` 拼 `IN` 存在 32767 参数上限风险）、`WorkLogService.list`（`272-299`，无日期上限且内存过滤 `typeCode`）。
- **统计聚合全量拉表**：`PersonalBillService.getSummary`（`159-170`）、`HealthRecordService.getSummary` / `countReportsByVisit`（`231-243, 522-554`）、`TravelRecordService.summary`（`177-195`）、`RequirementBoardService.buildStatusCounts`（`249-275` 循环 `COUNT(*)`）。
- **整改建议**：在 `gak-framework` 统一注册 MyBatis-Plus 分页插件（`DbType.POSTGRE_SQL`），公共分页基类强制 `@NotNull`、`@Min(1)`、`@Max(100)`；汇总接口下推 SQL `GROUP BY`/`COUNT`/`MAX`；附件服务补充批量查询接口 `listBusinessAttachments(type, ids, usage)` 彻底消除各模块的页内 N+1。

### 4.2 全局异常处理与可观测性缺口

- **`GlobalExceptionHandler` 未记录未处理异常堆栈**：`gak-framework/src/main/java/com/gak/framework/exception/GlobalExceptionHandler.java:80-106` 中 `handleOther(Exception ex)` 无 `Logger` 输出。由于 `gak-system-log` 有意剥离异常消息且有 20,000 行上限，线上 500 故障在应用日志中几乎不可诊断；同时 `HttpMessageNotReadableException`、`MethodArgumentTypeMismatchException`、`MissingServletRequestParameterException`、`HttpRequestMethodNotSupportedException`、`MaxUploadSizeExceededException` 等客户端错误均落入 `handleOther` 返回 500；`handleConstraintViolation` 直接暴露 `ex.getMessage()` 内部参数名。
- **业务层静默吞异常**：`AiSyncWorker.java:45, 59` 捕获 `RuntimeException` 无日志；`PermissionManagementService.java:445` 吞掉原始异常无日志；`TodoItemService.java:534-544` 与 `WowCharacterService.java:1620-1630` 吞掉字典底层 `BusinessException` 根因。

### 4.3 MyBatis-Plus `NOT_NULL` 更新策略导致可空字段无法清空

- `HealthReport.visitId`（`HealthReport.java:18`、`HealthRecordService.java:216, 366`）：编辑健康报告传入 `visitId = null` 无法解除就诊关联。
- `Requirement.description`（`RequirementBoardService.java:277-290`）：编辑需求传入空描述无法清空数据库旧值。
- 建议对支持置空的业务字段统一改用 `UpdateWrapper.set(...)` 或明确标注 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`。

### 4.4 数据库 Schema、并发竞态与事务细节

- **索引与外键缺口（`gak-start/src/main/resources/schema.sql`）**：
  - `gak_user_app_permission`（538-549 行）缺 `(app_code, granted)` 索引，导致 `SystemAppService.loadGrantCountMap` 全表扫描；且缺指向 `gak_user(id)` 的外键或删除用户时的级联清理，易残留孤立授权记录。
  - `gak_user`（1-16 行）缺少 `(created_at DESC, id DESC)` 列表索引。
- **并发竞态与锁设计缺陷**：
  - `gak-ai-subscription`：`AiSyncWorker.fail()`（85-86 行）未先锁 `owner` 直接锁连接行，与 `claim()`/`complete()`/`revoke()` 加锁顺序相反，存在死锁风险；`@EnableScheduling` 共用单线程调度器，长同步会阻塞全站定时任务。
  - `gak-wow-character`：`validateFeaturedCharacterLimit()`（1457-1467 行）无锁校验可并发突破 4 个主角色上限；`resetWeeklyProgressIfNeeded()`（355-368 行）多标签并发打开同一角色会撞唯一键报 500。
  - `gak-worklog`：`delete()`（174-183 行）漏加用户锁；`pg_advisory_xact_lock(#{userId})` 占用全局 64 位锁空间，易与其他模块冲突。
  - `gak-todo-list`：`clearCompleted()`（181-201 行）先按 `ids` 删子步骤、后按 `status = COMPLETED` 删主表，并发完成任务会产生孤儿子步骤。
  - `gak-knowledge-base`：`publish()`/`reject()`（133-153 行）缺条件状态更新，并发审核可覆盖终态；`update()`（112 行）管理员代编辑他人条目时以 `current.getOwnerUserId()` 校验附件归属会引发逻辑错乱。
  - `gak-user`：`/auth/register`（`AuthService.java:50-80`）无开关控制且未捕获并发同名注册的 `DuplicateKeyException`（返回 500）；RSA 填充使用较旧的 `RSA/ECB/PKCS1Padding`（`PasswordCryptoService.java:44`）。
  - `gak-data-migration`：`generateTaskNo()`（630-633 行）毫秒时间戳并发易撞号；`continueOnError=false` 中途失败但前序资源已提交时仍标 `FAILED` 而非 `PARTIAL_SUCCESS`；`imports/` 归档永不清理，导出异常时临时 zip 未在 `finally` 清理。

### 4.5 输入校验、精度与工程 hygiene

- **资源与文件处理**：
  - `AttachmentFileProcessor.java:63, 177-200`：50MB 文件整包 `getBytes()` 进堆、`ImageIO.read` 缺像素尺寸防炸弹校验、`Graphics2D.dispose()` 未放 `finally`。
  - `DataMigrationController.java:76-84`：下载响应头 `ContentDisposition.filename(...)` 未传 `StandardCharsets.UTF_8`（中文包名直接抛异常），且缺 `Cache-Control: no-store` 与 `nosniff`。
- **数据校验与空值/精度防御**：
  - `gak-wow-character`：`SaveWowCharacterRequest` 子集合缺 `@Size`、宏/键位内容无长度上限、列表传 `[null]` 直接 NPE。
  - `gak-knowledge-base`：`tags` 缺数量/长度/逗号校验，拼接超 255 字符直接 500；管理员判定大小写不一致（`equals` vs `equalsIgnoreCase`）。
  - `gak-todo-list`：`buildUpcoming()`（325-327 行）排序未用 `Comparator.nullsLast` 存在 NPE 隐患；`steps` 缺 `@Size` 且未防 `null` 元素。
  - `gak-instrument-practice`：`validateTakeRequest` 未判空 `tuningId`/`meter` 与负数时长；`InstrumentPracticeTakeVO:17` 直接复用入参 DTO `PerformanceEventRequest`（违反 `AGENTS.md:99` DTO/VO 不混用）。
  - `gak-personal-bills` / `gak-travel-record`：`PersonalCreditService:45` `setScale(4)` 缺 `RoundingMode`；`PersonalBillService:439, 466-467` 聚合缺空值过滤；`PersonalBillService:579` 非法月份如 `2026-99` 抛 500；`TravelRecordService:192` 净额累加未显式 `setScale(2, RoundingMode.HALF_UP)`。
- **其他模块隐患**：
  - `gak-ai-subscription`：`AiAccountService.list()`（39-42 行）与 `AiBillLinkService.candidates()`（50-58 行）存在 N+1 查询；`AiSummaryService` 汇总拉取不需要的备忘录信息。
  - `gak-workbench` / `gak-requirement-board`：`ensureCurrentUserExists()` 仅判存在未判启用态。
  - `gak-user`：`GakUserApplication.java:6-13` 业务库残留 `@SpringBootApplication` 启动类及多余 `mvnw`/`application.properties`，会被 `gak-start` 的 `scanBasePackages = "com.gak"` 重复扫描。

---

## 五、P3 代码规范、架构债与测试覆盖缺口

### 5.1 巨型类（上帝类）治理清单

以下核心 Service 严重超过常规可维护阈值，承担过多职责，建议按领域子能力拆分：

| 排名 | 文件 | 行数 | 主要问题 |
| --- | --- | --- | --- |
| 1 | `gak-modules/gak-wow-character/.../WowCharacterService.java` | **1,872** | 11 个依赖 + 10 个内部 record，耦合 CRUD、字典校验、概览聚合、周重置、赛季归档；硬编码赛季文案与混用异常体系 |
| 2 | `gak-modules/gak-worklog/.../WorkLogService.java` | **1,012** | 耦合日志 CRUD、条目合并、加班/出差补助、同日同步、VO 拼装等 7 类职责 |
| 3 | `gak-modules/gak-health-record/.../HealthRecordService.java` | **692** | 耦合指标记录、体检报告、就诊记录、旧文件上传下载与账单联动 |
| 4 | `gak-modules/gak-data-migration/.../DataMigrationService.java` | **664** | 耦合资源目录、范围解析、任务编排、文件下载与历史清理 |
| 5 | `gak-modules/gak-data-dictionary/.../DataDictionaryService.java` | **649** | 字典 CRUD、字典项 CRUD、缓存维护混杂 |
| 6 | `gak-modules/gak-permission-management/.../PermissionManagementService.java` & `SystemAppService.java` | **629 / 595** | 两类大段重复校验、图标处理、JSON 序列化与管理员校验逻辑 |

### 5.2 违反 `AGENTS.md` / `codex.md` Java 类设计规范（内部类违规）

规范要求：**DTO、VO、BO、Query、Command、Context、Result 等数据对象必须创建独立 Java 类**，仅允许完全局部使用的 `private static` 算法辅助类。当前主源码共检出 **63 处**内部类/嵌套 record，其中多处为公开暴露或被 Jackson/跨类引用的违规类型：

- `gak-data-migration`（重灾区）：`DataMigrationArchiveService` 内 6 个 `public record`（237-288 行）、`MigrationResourceHandler` 内 5 个嵌套类型（33-156 行）、11 个 `*MigrationHandler` 中的 `public static class Payload`、`DataMigrationResourcesVO` / `DataMigrationTaskVO` 中的 `public static class *VO`、`DataMigrationService.DownloadFile`（656 行）、`AttachmentMigrationSupport.ExportBundle/TransferItem`（90-124 行）。
- `gak-attachment`：`AttachmentService.ResourceFile`（362 行）、`AttachmentFileProcessor.ProcessedFile`（248 行）。
- `gak-permission-management`：`SystemAppIconService.IconResource`（213 行）。
- `gak-health-record`：`HealthRecordService.ReportFileResource`（691 行）。
- `gak-password-memo`：`PasswordMemoCryptoService.EncryptedPayload`（69 行）。
- `gak-user`：`UserValidationSupport.StatusEnabledPair`（48 行，包级跨类使用）。

### 5.3 其他规范微瑕疵

- `gak-start/.../HelloController.java:9-16`：未鉴权、不遵循 `ApiResponse` 契约的演示接口，建议删除或限定 `dev` profile。
- `gak-permission-management/.../PermissionManagementService.java:4`：存在通配符导入 `import com.gak.framework.message.*;`。
- `gak-workbench/.../WorkbenchPayloadNormalizer.java:24, 112`：`CARD_IDS` 已有 7 项，但错误提示仍硬编码“最多 6 张”。
- `gak-ai-subscription` 多类与 `PasswordMemoReferenceService.java`：代码排版过度压缩（多语句挤在同一行或 1 空格缩进），降低可读性与 diff 评审效率。
- `gak-health-record/.../HealthRecordService.java:68` 未使用常量 `ZERO_DECIMAL`；`SaveCreditAccountRequest.java:4` 未使用导入。

### 5.4 测试覆盖缺口

当前 467 个用例全部通过，但存在 **45 个跳过用例**（主要为依赖本地 PostgreSQL 开关的集成测试，其中 `gak-message` 11/11 全跳过、`gak-start` 24/25 跳过），且以下核心路径缺少自动化回归保护：

- **安全与权限负例测试严重不足**：全仓普遍缺少 Controller 层 `@WebMvcTest`（未登录 401、无应用权限 403、非管理员 403、禁用用户 Token 失效、参数越界 400）。
- **核心 Service 单测薄弱模块**：
  - `gak-travel-record`：`TravelRecordService`、`TravelBillSyncService`、`TravelGroupService` 无单测；
  - `gak-attachment`：`AttachmentService`、`AttachmentCleanupService` 无单测；
  - `gak-message`：`MessageStreamService` 并发推送/超时/停机无单测；
  - `gak-data-migration`：`DataMigrationTaskExecutionService`、核心系统 Handler 及 ZIP Bomb 安全负例无单测；
  - `gak-ai-subscription`：`AiBillLinkService`、`AiBudgetService`、`AiQuotaSnapshotService`、`AiSummaryService.insights` 无单测；
  - `gak-user`（仅 4 个测试）、`gak-instrument-practice`（仅 3 个测试）、`gak-todo-list`（仅 5 个测试）、`gak-health-record`（缺 `DentalCleaningService`）、`gak-personal-bills`（缺 `PersonalBankCardService`）、`gak-fuel-stats`（缺 `FuelVehicleService`）。

---

## 六、各模块健康度矩阵与标杆实践

### 6.1 模块健康度总览

> 评级说明：`A` 标杆 / `B` 良好 / `C` 需改进 / `D` 存在高危或严重缺陷

| 模块 | 主文件/测试数 | 安全与权限 | 性能与并发 | 规范与测试 | 关键摘要 |
| --- | --- | :---: | :---: | :---: | --- |
| `gak-system-log` | 20 / 12 | **A** | **A** | **A** | 全仓标杆：权限最严、脱敏彻底、异步隔离、咨询锁清理 |
| `gak-workbench` | 14 / 7 | **A-** | **A** | **A** | 标杆：分块大包防 DoS、JSONB 清洗、乐观锁 + Postgres 集成测试 |
| `gak-growth-record` | 21 / 2 | **A** | **B+** | **B+** | 应用权限齐全、宝宝行锁串行化边界严谨 |
| `gak-message` | 14 / 1 | **A** | **C+** | **B** | 咨询锁幂等发送与归属隔离极佳；需修 SSE 并发/停机缺陷与单测跳过 |
| `gak-framework` | 14 / 3 | **B+** | **A** | **B** | 契约清晰；`GlobalExceptionHandler` 需补 500 日志与 4xx 细分 |
| `gak-travel-record` | 22 / 2 | **A-** | **B** | **C+** | 幂等/乐观锁/CSV 防注入严谨；需修清空商户名同步与补核心单测 |
| `gak-ai-subscription` | 72 / 7 | **A-** | **C** | **C+** | 加密 AAD/防 SSRF 优秀；需修同步万级 UPDATE、HTTP 响应上限与代码排版 |
| `gak-personal-bills` | 31 / 2 | **C** | **C** | **B-** | 账单来源保护好；缺应用权限校验、内存分页与全量汇总 |
| `gak-fuel-stats` | 26 / 4 | **C** | **D** | **B** | 油价客户端降级好；缺应用/登录校验、全量 N+1 查附件 |
| `gak-todo-list` | 18 / 1 | **C** | **C** | **C+** | 缺应用权限、`clearCompleted` 竞态、内存分页 |
| `gak-worklog` | 18 / 2 | **C** | **C** | **C** | 缺应用权限、任意用户写全局字典、1012 行上帝类 |
| `gak-requirement-board` | 20 / 1 | **C-** | **C** | **B-** | 进度流转越权且无状态机、内存分页、`description` 无法置空 |
| `gak-knowledge-base` | 11 / 1 | **C** | **D** | **C+** | 匿名 `/public-highlights` 全表扫描+丢弃式 N+1、标签超长 500 |
| `gak-instrument-practice` | 8 / 1 | **C** | **D** | **C** | 缺应用权限、大 JSON 全量加载、DTO/VO 混用 |
| `gak-data-dictionary` | 24 / 2 | **C** | **C** | **B-** | 事务内提前清缓存致脏读、管理员未查启用态、内存分页 |
| `gak-attachment` | 15 / 3 | **C-** | **C+** | **C+** | 魔数校验与原子落盘好；需修旧 SVG XSS、扩展名绕过与清理竞态 |
| `gak-permission-management` | 28 / 5 | **C-** | **C** | **C** | SVG 图标 XSS、审计日志超长 500 与高频写库、两 Service 大量重复 |
| `gak-data-migration` | 40 / 6 | **C-** | **C** | **C** | 缺 ZIP 炸弹/路径边界保护、导入未清字典缓存、大量违规嵌套类 |
| `gak-health-record` | 36 / 3 | **D** | **C** | **C** | **P0 医疗报告免鉴权下载**、缺应用权限、内存分页与 `visitId` 无法置空 |
| `gak-password-memo` | 20 / 2 | **D** | **B-** | **B-** | **P0 硬编码默认 AES 密钥**、二次鉴权无防爆破；其余归属隔离与 GCM 选型良好 |
| `gak-wow-character` | 43 / 3 | **D** | **D** | **C-** | **P0 共享指南无管理员鉴权**、低保新建回参 Bug、严重 N+1、1872 行上帝类 |
| `gak-user` / `gak-start` | 32 / 8 | **D** | **C+** | **C** | **P0 后台用户管理无管理员鉴权 + Token 无过期/无吊销**、改密/验证码安全短板、配置硬编码密码 |

### 6.2 值得全仓推广的标杆工程实践

1. **`gak-system-log`**：`requireReader` 四重校验（存在 + 启用 + 管理员 + 应用授权）、异常正文脱敏、路由模板采样、独立守护线程 + 有界队列 + `REQUIRES_NEW` 事务隔离、PostgreSQL `pg_try_advisory_xact_lock` + `statement_timeout` 分批清理。
2. **`gak-workbench`**：`WorkbenchPreferenceBodyAdvice` 在反序列化前流式阻断超大请求体、白名单清洗 + PostgreSQL JSONB 非法字符过滤、`ON CONFLICT DO NOTHING` + 乐观锁并发控制。
3. **`gak-message`**：PostgreSQL 事务级咨询锁 + 请求指纹幂等发送、全 SQL 强绑定 `user_id`、`afterCommit` 触发最小载荷 SSE 通知。
4. **`gak-ai-subscription`**：AES-256-GCM + `owner:account` AAD 绑定的凭据加密、`WRITE_ONLY` + 自定义 `toString` 防密钥泄露、固定官方域名 + `Redirect.NEVER` 防 SSRF、数据库租约 + 版本号防同步脑裂。
5. **`gak-growth-record` & `gak-travel-record`**：行锁串行化关联业务边界校验、`@Transactional(propagation = Propagation.MANDATORY)` 强制跨模块账单同事务提交、`ensureBillIsEditable()` 防来源账单双向失真、CSV 公式注入转义。

---

## 七、分阶段整改路线图

### 第一阶段：P0 紧急止血（已于 2026-11-27 完成主体修复）
1. **[x] 权限补洞**：已为 `SystemUserService` 全部 6 个管理接口与 `WowSpecializationGuideService.update` 补齐管理员角色 + 启用状态校验；已为 `/health-records/report-files/{fileName}` 补齐登录态、`APP_HEALTH_RECORD` 权限与归属校验。
2. **[x] 备忘录密钥清理 / [ ] 保留项 P1-6**：已移除 `PasswordMemoCryptoService` 硬编码默认密钥（改为强制环境变量 + 启动 fail-fast + AAD 绑定）；`application.properties:7` 数据库密码按 P1-6 与本地开发约定保留现状，待环境统一切换为 `${SPRING_DATASOURCE_PASSWORD:}` 并轮换凭据。
3. **[x] Token 生命周期改造**：已为 `TokenService` 增加绝对/滑动 TTL 过期、容量上限淘汰、登出/按用户吊销（禁用/删除/改角色/改密/重置密码联动吊销）与账号启用态校验。

> **注**：生产环境上线前需配置 `GAK_PASSWORD_MEMO_AES_KEY` 并完成历史密文重加密轮换。

### 第二阶段：P1 安全加固与核心性能修复（1–2 周）
1. **统一鉴权架构**：在 `gak-framework` 落地全局认证与 `@RequireAppPermission` 拦截器，一次性收口 10+ 模块的应用权限校验、`forceChangePassword` 强制拦截、`/auth/change-password` 鉴权与登录/二次鉴权限流、图形验证码与用户名枚举修复。
2. **文件与迁移安全**：封堵 SVG 同源 XSS（`gak-permission-management` + `gak-attachment`）、补齐附件扩展名一致性校验、为 `gak-data-migration` 补齐 ZIP 炸弹限制/流式 SHA-256/路径根目录校验/UTF-8 下载头。
3. **高频性能与一致性止血**：修复 `AiSyncWorker` 同步循环重复 `reconcile`、油耗/WoW/知识库 N+1 与 `public-highlights` 全表扫描、乐器录音元数据与事件分离、字典 `afterCommit` 清缓存与迁移清缓存、附件清理原子占位、SSE 并发关闭与停机防护、`TravelBillSyncService` 置空同步、WoW 低保回参 Bug、权限审计日志截断与降噪。

### 第三阶段：P2/P3 架构与规范治理（持续迭代）
1. **数据库分页与批量附件**：启用 MyBatis-Plus 分页插件，逐步将 12 个模块的内存分页与内存聚合下推 SQL，补齐 `schema.sql` 缺失索引与用户删除级联。
2. **可观测性完善**：为 `GlobalExceptionHandler` 补齐 500 堆栈日志与 4xx 细分映射，消除业务层静默吞异常。
3. **上帝类拆分与类设计合规**：优先拆分 `WowCharacterService`（1872 行）、`WorkLogService`（1012 行）及去重 `PermissionManagementService`/`SystemAppService`；逐步将 `gak-data-migration` 等模块的公开嵌套类迁出为独立顶层 Java 类；移除 `gak-user` 冗余 `GakUserApplication` 与 `HelloController`。
4. **补齐安全回归测试**：为所有 P0/P1 修复补充 `@WebMvcTest` 越权负例测试与核心 Service 并发/边界单测，评估 CI 中开启 PostgreSQL 集成测试避免 45 个用例长期跳过。
