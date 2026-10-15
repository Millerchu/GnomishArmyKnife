# 后端架构与模块

核对日期：2026-10-15。依据父 POM、模块聚合/启动 POM、Controller 与服务实现。

## 运行结构

`gak-start` 直接依赖全部 18 个业务模块，入口为 `com.gak.gakstart.GnomishArmyKnifeApplication`，在一个 Spring Boot 进程内共享 PostgreSQL 和本地存储。当前不是独立部署的微服务。

典型链路为 Controller → Service → Mapper → PostgreSQL。Controller 接收参数和当前用户，Service 处理业务规则、权限及事务；DTO/VO 表达入参与出参，Domain 映射持久化字段。具体事务范围以服务方法为准。

`gak-framework` 提供响应、分页、异常处理、Long 序列化、字典接口、消息发布契约。`gak-common` 当前没有源码目录，不应将框架能力归入该模块。

## 模块清单

以下模块均位于 `gak-modules/`，HTTP 前缀不含代理的 `/api`。

| 模块 | 职责 | HTTP 前缀 |
| --- | --- | --- |
| `gak-user` | 登录注册、验证码、密码、用户、Token | `/auth`、`/system/users` |
| `gak-permission-management` | 应用、图标、授权 | `/system/apps`、`/system/permissions` |
| `gak-data-dictionary` | 字典、选项、用途绑定 | `/system/dictionaries` |
| `gak-data-migration` | 资源、导入导出、归档和任务 | `/system/data-migrations` |
| `gak-attachment` | 统一附件上传、绑定与读取 | `/attachments` |
| `gak-message` | 收件箱、已读、管理员发布、SSE | `/messages` |
| `gak-growth-record` | 芽记：宝宝、生长、接种、里程碑 | `/growth-records` |
| `gak-travel-record` | 行笺：行程、分组、关联账单 | `/travel-records`、`/travel-groups` |
| `gak-worklog` | 日志、项目、未完成事项、周报 | `/work-logs` |
| `gak-password-memo` | 密码备忘、访问校验 | `/password-memos` |
| `gak-wow-character` | 角色、周常、赛季、专精指南 | `/wow-characters`、`/wow-specialization-guides` |
| `gak-todo-list` | 待办与状态 | `/todo-items` |
| `gak-fuel-stats` | 车辆、加油与统计 | `/fuel-vehicles`、`/fuel-records` |
| `gak-personal-bills` | 收支、预算、信用账户 | `/personal-bills`、`/personal-bills/credit-accounts` |
| `gak-health-record` | 体征、报告、就诊与费用 | `/health-records` |
| `gak-knowledge-base` | 经验条目、审核、公开精选 | `/knowledge-base` |
| `gak-instrument-practice` | 演奏作品存储 | `/instrument-practice/takes` |
| `gak-requirement-board` | 桌面需求看板与进度 | `/requirement-items` |

计算器、星空交互没有对应独立业务模块。软件仓库已有前端与 `/software-packages` 请求封装，但当前后端没有对应 Controller，不能记为已完成的前后端闭环。

## 接口与认证

普通成功响应为 `{"code":"0","message":"success","data":...}`，分页为 `data.list`、`data.total`。下载和 SSE 使用各自媒体类型。`Long`/`long` 全局序列化为字符串，前端 ID 保持字符串以避免精度丢失。

`TokenService` 生成 UUID Token，在进程内 `ConcurrentHashMap` 保存用户映射；受保护接口读取 `Authorization: Bearer <token>`。当前不是 JWT，也没有共享会话存储，重启需重新登录，多实例前需解决共享认证。

登录校验与业务授权不同，应用权限、管理员身份和数据归属由服务检查，不能依靠前端守卫替代。密码编码器使用 BCrypt。应用目录的 `securityLevel`、`encryptionMode` 是配置元数据，不能仅凭值宣称整个业务已具备对应加密能力。

## 跨模块协作

- 消息契约位于 `gak-framework/message`，实现位于 `gak-message`；业务可在事务中发布，提交后发 SSE 通知。数据库是消息来源，SSE 为单实例刷新通道，详见[消息说明](../gak-modules/gak-message/README.md)。
- 行程、健康就诊与账单有关联，修改同步、删除和迁移时一并检查来源字段及关联关系。
- 附件由 `gak-attachment` 提供公共能力，图标与健康历史文件还有专门路径；迁移模块含历史附件迁移支持。数据库回滚不能自动回滚文件操作。
- 迁移通过 `MigrationResourceHandler` 注册资源，任务提交后交给 `TaskExecutor`。应用目录项不等于可备份资源，各资源导入也不是整个 ZIP 的统一事务。

## 数据与部署

结构和种子入口为 `gak-start/src/main/resources/schema.sql`，补充 SQL 位于 `docs/sql/`。当前使用 Spring SQL 初始化，不能假定存在 Flyway/Liquibase 自动版本迁移。

NAS Compose 运行 Nginx、单个后端、PostgreSQL；Redis 为可选 profile，当前 Token/SSE 不依赖它。文件挂载 `/app/data`，数据库独立持久化。业务导出包不能替代全库与文件备份。

新增模块需同步聚合 POM、启动依赖、DDL、权限、API 和测试；新增前端入口同步路由、目录、图标和授权。需要备份的模块还必须接入 Handler。新增 Java 数据对象和业务枚举使用独立类，不为减少文件数量新增内部类。
