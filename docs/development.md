# 后端开发与配置

核对日期：2026-10-15。命令默认在后端仓库根目录执行，完整启动见 [README](../README.md)。

## 环境

需要 JDK 21、Maven Wrapper 下载能力、可用的 PostgreSQL 数据库。完整 GAK 工作区可在工作区根目录使用 `scripts/backend-dev.ps1` 临时选择内置 JDK，不影响系统 JDK。

运行目录决定 `./data` 等相对路径，保持一致以免附件路径变化。Windows 下 JAR 正在运行时可能不能重新打包替换，先核对占用进程属于本项目，再停止相应实例。

## 配置

入口为 `gak-start/src/main/resources/application.properties`，本地实际凭据不写入文档或提交。

| 环境变量 | 用途 / 默认行为 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC 地址 |
| `SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` | 数据库凭据 |
| `SERVER_PORT` | 服务端口，默认 8080 |
| `SPRING_SQL_INIT_MODE` | 默认 always；never 时需自行安排升级 SQL |
| `GAK_ATTACHMENT_STORAGE_ROOT` | 统一附件根目录，默认 ./data |
| `GAK_ATTACHMENT_CLEANUP_CRON` | 默认 0 30 3 * * * |
| `GAK_ATTACHMENT_LEGACY_MIGRATION_ENABLED` | 历史附件迁移，默认 true |
| `GAK_APP_ICON_LOCAL_DIR` | 默认 ./data/app-icons |
| `GAK_DATA_MIGRATION_STORAGE_DIR` | 默认 ./data/data-migrations |
| `GAK_HEALTH_FILE_LOCAL_DIR` | 默认 ./data/health-records |
| `GAK_FUEL_PRICE_JUHE_API_KEY` | 在线油价接口凭据 |
| `GAK_FUEL_PRICE_CACHE_MINUTES` | 油价缓存，默认 30 分钟 |
| `GAK_SYSTEM_LOG_MAX_ROWS` | 系统异常日志入库上限，默认 20000；达到后停止新写入，不删除历史 |
| `GAK_SYSTEM_LOG_RETENTION_DAYS` | 保留天数默认 30；开启清理时必须 >=30，严格早于截止时间才删除 |
| `GAK_SYSTEM_LOG_CLEANUP_ENABLED` | 默认 false；需表属主或超级用户执行清理函数脚本后显式开启 |
| `GAK_SYSTEM_LOG_CLEANUP_CRON` | 默认 `0 10 4 * * *`，固定 Asia/Shanghai 调度 |
| `GAK_SYSTEM_LOG_CLEANUP_BATCH_SIZE` | 默认 500，范围 1–1000 |
| `GAK_SYSTEM_LOG_CLEANUP_MAX_BATCHES_PER_RUN` | 默认 40，至少 1 |
| `GAK_SYSTEM_LOG_CLEANUP_BATCH_INTERVAL_MS` | 默认 100，至少 0 |
| `GAK_SYSTEM_LOG_CLEANUP_STATEMENT_TIMEOUT` | 默认 10s，外层事务本地配置 |
| `TZ` 或 `-Duser.timezone` | 系统异常日志写入与清理已共用显式 Asia/Shanghai Clock，不依赖进程默认时区；其他业务仍需核对自身时间语义 |

上传配置当前为单文件/单请求各 200MB，还需考虑代理和业务限制。图标、归档、健康文件公开路径前缀默认带 `/api`，网关路径改变时核对相应 `GAK_*_PUBLIC_URL_PREFIX`。

系统异常日志仅为已进入采集的失败响应返回服务端生成的 `X-Trace-Id`，不采信请求头；成功响应、业务/校验类 4xx 不加头，错误 JSON 不变。写入失败/超时仍返回编号，有头不保证当时已入库；已提交 SSE 响应不补头。

系统日志清理默认关闭。启用前需表属主或超级用户执行 [清理函数及授权脚本](sql/20261111-system-exception-log-retention.sql)，应用角色仍只有表级 SELECT/INSERT，另获函数 EXECUTE。删除语句的锁等待固定 2s，由函数 SET lock_timeout 控制，不再暴露应用侧 lock-timeout 配置。函数硬性保留最近 30 天；任务失败仅进应用日志，不再采集到系统日志。脚本不进入 schema.sql，不能通过自动初始化替代手工授权。本轮实现与验证状态见 [验证记录](verification/sl-tr-be1.md)。

SECURITY DEFINER 清理函数固定 `search_path = pg_catalog, pg_temp`：public 可能被有 CREATE 权限的角色写入，不纳入可信搜索路径；pg_temp 显式放最后，防止临时 schema 抢先解析。表引用限定为 `public.gak_system_exception_log`，时钟函数使用 `pg_catalog.now()`。

清理脚本默认模式 A（函数属主为表属主），推荐模式 B：在 `-v app_role=<应用角色>` 之外传入 `-v cleanup_owner=gak_log_cleaner`，使用独立非超级用户 NOLOGIN 角色，只授予目标表 SELECT/DELETE 与 public USAGE。B 需执行者具备创建角色、授权及转移函数属主权限，推荐超级用户安装。应用角色必须存在，不能是表属主、cleanup_owner 或两者的成员；cleanup_owner 不能是表属主，已有角色不得有超级用户、登录、BYPASSRLS、CREATEROLE、CREATEDB 属性。专用角色应无其他授权/成员关系。模式 B 会新建集群级角色并增加表授权，在 gak_dev/生产使用前需用户另行批准；部署前须人工确认应用角色 `rolcreaterole = f`，不是清理角色的成员，也不持有其 ADMIN。同签名函数属主须为表属主，B 额外允许指定 cleanup_owner，否则拒绝替换；前置检查失败非零退出、整事务回滚。A -> B 与同模式重跑幂等，B -> A 先由管理员显式恢复函数 OWNER 为表属主，再执行 A，并执行脚本头部回滚段的 REVOKE（表 SELECT/DELETE、schema USAGE），或确认无其他依赖后 `DROP ROLE gak_log_cleaner`，否则残留表授权，快照与全新模式 A 不一致。

函数固化安装时表属主 OID，首条语句取 ROW EXCLUSIVE 表锁，再验证普通表、固定属主、无触发器（含内部 RI）、规则、RLS/强制 RLS、继承子表；A 另验证属主即 definer，失败为 `42501`，任务仅记 WARN、不外抛、不采集。数据库 owner 可经 `pg_database_owner` DROP public 内他人表，故固定 search_path 后仍需此守卫；日志表也不得被外键引用。角色改名不影响 OID，合法转移表属主须重新安装。

数据库 owner 角色（PG15+ 经 `pg_database_owner` 拥有 public）还可以 DROP 清理函数并用同名函数替换。替换后的函数只能以该角色自身身份执行，不能提权，但伪函数返回 0 可使清理静默停止、Job 不报错；安装器重跑会因 `existing purge function has unexpected owner` 失败。部署前与日常巡检应使用以下只读快照核对函数属主与配置，安装后也应复核；角色占位符按实际替换：

```sql
SELECT p.oid::regprocedure AS fn, pg_catalog.pg_get_userbyid(p.proowner) AS owner, p.prosecdef, p.proconfig, p.proacl FROM pg_catalog.pg_proc p WHERE p.proname = 'gak_purge_system_exception_log' AND p.pronamespace = 'public'::regnamespace;
SELECT rolcreaterole FROM pg_catalog.pg_roles WHERE rolname = '<app_role>';
SELECT m.roleid, m.member, m.admin_option FROM pg_catalog.pg_auth_members m JOIN pg_catalog.pg_roles a ON a.oid = m.member JOIN pg_catalog.pg_roles c ON c.oid = m.roleid WHERE a.rolname = '<app_role>' AND c.rolname = '<cleanup_owner>';
```

函数查询预期恰好 1 行：owner 为模式 A 的表属主或模式 B 的 cleanup_owner，`prosecdef = t`，proconfig 含 `search_path=pg_catalog, pg_temp` 与 `lock_timeout=2s`，proacl 中无 PUBLIC、app_role 有 X。模式 B 的角色属性查询预期 1 行且 `rolcreaterole = f`；成员查询（member=应用角色、roleid=清理角色，含 admin_option）预期 0 行。

执行后检查函数属主、prosecdef、proconfig（路径与 2s 锁超时）、PUBLIC 撤权、应用 EXECUTE 且无表级 DELETE，B 的 cleanup_owner 有效权限仅 SELECT/DELETE + USAGE。回滚为 DROP FUNCTION；B 再 REVOKE 表/schema 权限并 DROP ROLE，不恢复已删数据。完整两模式命令、快照检查项和回滚 SQL 见 [脚本头部](sql/20261111-system-exception-log-retention.sql) 与 [模块说明](../gak-modules/gak-system-log/README.md)。

## 数据库变更

修改实体与服务时同步 `schema.sql`，需要时补 `docs/sql/` 升级脚本。区分新库初始化和存量升级，核对索引、约束、历史数据及种子幂等性。自动初始化关闭时仅升级 JAR 不会更新表结构。先在可恢复的测试库验证，再安排目标环境升级；备份应覆盖数据库与文件。

## 验证

```powershell
.\mvnw.cmd test
.\mvnw.cmd -pl gak-modules/gak-growth-record -am test
.\mvnw.cmd -pl gak-start -am -DskipTests compile
```

以 `target/surefire-reports` 的实际执行、跳过数为准，区分测试成功、未发现测试和仅编译。消息 PostgreSQL 集成测试需要 `GAK_MESSAGE_TEST_URL`，可覆盖 `GAK_MESSAGE_TEST_USER`、`GAK_MESSAGE_TEST_PASSWORD`，账号需创建 schema 权限；不设置 URL 会跳过。显式 Surefire 3.5.2 命令见[消息验证](../gak-modules/gak-message/README.md#验证)。其他集成测试按源码条件准备，不能假定普通 test 已覆盖真实数据库和 HTTP。

## 排错

| 现象 | 首先核对 |
| --- | --- |
| 404 | 代理是否去掉 /api、Controller 是否存在 |
| 重启后 401 | 内存 Token 已失效，重新登录 |
| 登录成功却无应用 | 应用状态、授权、目录和路由 |
| 消息不实时 | SSE 缓冲、超时、连接及轮询补查 |
| 附件失败 | 工作目录、存储根、绑定归属、挂载 |
| 新表不存在 | 目标库、初始化开关、升级 DDL |

部署操作见 [NAS 手册](../deploy/nas/README.md)，示例用户和路径需按实际环境替换。静态源码核对不能证明运行环境已生效。
