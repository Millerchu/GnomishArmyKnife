# 系统异常日志

只记录后端未处理异常，供管理员分页查询。不是访问日志，不提供手动删除、导出或远程采集接口。

查询字段、权限和示例见 [后端契约](../../docs/frontend/system-log-backend-contract.md)。契约里的 JSON 都是合成示例。

## 采集边界

会尝试入库的只有：

- 进入 `GlobalExceptionHandler`、且不属于业务校验、入参错误、4xx 或客户端断开的异常。
- Spring Boot 自动配置的 `applicationTaskExecutor` 上未被捕获的异常，来源记为 `ASYNC_TASK`。请求方法和路径为 null。
- Spring Boot 自动配置的 `taskScheduler` 上未被捕获的异常，来源记为 `SCHEDULER`。请求方法和路径为 null。`@Scheduled` 是否真正使用这个调度器，取决于运行中的 Spring 上下文只有这一套自动配置调度器。

不会入库的包括：业务异常、参数校验、认证拒绝、数据迁移任务里已经捕获并写在任务状态上的异常、附件清理里已经捕获的异常、消息 SSE 虚拟线程，以及其他自建线程池。因此不能把它理解成全部后台任务的异常都已覆盖。

异常消息正文在落库前丢弃，只留异常类和调用位置。HTTP 路径只留 Spring 已匹配的路由模板；没有可靠模板时为 null，不回退到 requestURI。堆栈超过 30 帧或 4000 字时以 `...[truncated]` 结尾，并把 `stackTruncated` 记为 true。不记录用户名、IP、请求头、请求体、口令、token 或密码备忘录原文。

调用方最多等待写入 2 秒。超时不取消任务，不保证数据库操作两秒内结束。`REQUIRES_NEW` 只有 Spring 代理调用才生效；直接 new 的单测不证明数据库事务行为；真实事务由显式开启的临时 PG16 集成测试覆盖。`occurredAt` / `createdAt` 与清理共用显式 Asia/Shanghai Clock Bean，不依赖 JVM 默认时区。

仅已进入采集的失败响应返回 `X-Trace-Id`，值为服务端生成的 32 位小写 hex，与尝试入库的 `trace_id` 相同，不读取请求头。写入失败、超时或队列满仍返回编号，有头不保证当时已入库或最终写入成功；错误 JSON 保持原样。业务/校验类 4xx 和成功响应不加头，已提交的 SSE 响应不补头；后台任务只在表中保留编号。

写入失败、数据库不可用或队列已满时只放弃等待或放弃这条日志，不改变原来的业务结果，也不删除历史。达到 `gak.system-log.max-rows` 后停止新写入；成功恢复写入后重置容量告警标志。

## 自动清理

清理默认关闭。需表属主或超级用户执行 [清理函数及授权脚本](../../docs/sql/20261111-system-exception-log-retention.sql)，再显式设置 `GAK_SYSTEM_LOG_CLEANUP_ENABLED=true`。脚本可重复执行，只创建函数/授权，不执行清理，不改表属主；应用只获函数 EXECUTE，仍无表级 DELETE/UPDATE。函数不进入 schema.sql。

清理函数使用 SECURITY DEFINER，固定 `search_path = pg_catalog, pg_temp`。不信任可能被有 CREATE 权限的角色写入的 public；pg_temp 显式放最后，防止临时 schema 抢先解析。表引用限定为 `public.gak_system_exception_log`，时钟函数使用 `pg_catalog.now()`。

安装支持两种模式，推荐 B，命令中的应用角色按实际替换：

```bash
# A：函数由安装时的表属主持有
psql -X -v ON_ERROR_STOP=1 -v app_role=gak_dev -f docs/sql/20261111-system-exception-log-retention.sql
# B：函数由独立非超级用户 NOLOGIN 清理角色持有
psql -X -v ON_ERROR_STOP=1 -v app_role=gak_dev -v cleanup_owner=gak_log_cleaner -f docs/sql/20261111-system-exception-log-retention.sql
```

两种模式均在安装时通过 `pg_class.relowner` 读取并固化表属主 OID；角色改名不影响守卫，合法变更表属主后须重新安装。函数第一条语句取 ROW EXCLUSIVE 表锁，随后验证普通表、固化属主、无任何触发器（含内部 RI）、规则、RLS/强制 RLS 或继承子表；A 还要求表属主就是当前 definer。违反约束报 `42501`。PG15 起数据库 owner 通过 `pg_database_owner` 可 DROP public 内他人的表，因此即使 search_path 固定，也必须防止替换表后的触发器提权。日志表不应被外键引用；内部 RI 触发器也会执行代码或级联删除，必须一并拒绝。

应用角色必须已存在，不能是表属主或 cleanup_owner，也不能是两者的成员。B 的 cleanup_owner 必须不同于表属主；角色不存在时在同一事务创建为 NOLOGIN、NOSUPERUSER、NOCREATEDB、NOCREATEROLE、NOINHERIT、NOREPLICATION、NOBYPASSRLS；已有角色若可登录、有超级用户/BYPASSRLS/CREATEROLE/CREATEDB 则拒绝。请使用无其他授权和成员关系的专用角色；脚本只授予 public USAGE 与目标表 SELECT/DELETE。B 还要求执行者有角色创建、授权和转移函数属主权限，推荐由超级用户安装。模式 B 会新建集群级角色并增加表授权，在 gak_dev/生产使用前需用户另行批准；部署前须人工确认应用角色 `rolcreaterole = f`，不是清理角色的成员，也不持有其 ADMIN。所有前置检查失败均非零退出、事务回滚；同签名函数只允许表属主（A/B）或指定 cleanup_owner（B）持有，否则拒绝替换。A -> B 及同模式重复执行幂等；B -> A 须先由管理员显式把函数 OWNER 恢复为表属主，再重跑 A，并执行脚本头部回滚段的 REVOKE（表 SELECT/DELETE、schema USAGE），或确认无其他依赖后 `DROP ROLE gak_log_cleaner`，否则残留表授权，快照与全新模式 A 不一致。

数据库 owner 角色（PG15+ 经 `pg_database_owner` 拥有 public）可以 DROP 清理函数并用同名函数替换。替换后的函数只能以该角色自身身份执行，不能提权，但伪函数返回 0 可使清理静默停止、Job 不报错；安装器重跑会因 `existing purge function has unexpected owner` 失败。部署前与日常巡检应使用以下只读快照核对函数属主与配置，安装后也应复核；角色占位符按实际替换：

```sql
SELECT p.oid::regprocedure AS fn, pg_catalog.pg_get_userbyid(p.proowner) AS owner, p.prosecdef, p.proconfig, p.proacl FROM pg_catalog.pg_proc p WHERE p.proname = 'gak_purge_system_exception_log' AND p.pronamespace = 'public'::regnamespace;
SELECT rolcreaterole FROM pg_catalog.pg_roles WHERE rolname = '<app_role>';
SELECT m.roleid, m.member, m.admin_option FROM pg_catalog.pg_auth_members m JOIN pg_catalog.pg_roles a ON a.oid = m.member JOIN pg_catalog.pg_roles c ON c.oid = m.roleid WHERE a.rolname = '<app_role>' AND c.rolname = '<cleanup_owner>';
```

函数查询预期恰好 1 行：owner 为模式 A 的表属主或模式 B 的 cleanup_owner，`prosecdef = t`，proconfig 含 `search_path=pg_catalog, pg_temp` 与 `lock_timeout=2s`，proacl 中无 PUBLIC、app_role 有 X。模式 B 的角色属性查询预期 1 行且 `rolcreaterole = f`；成员查询（member=应用角色、roleid=清理角色，含 admin_option）预期 0 行。

执行后核查 `pg_proc.proowner`、`prosecdef=true`、`proconfig`（`search_path=pg_catalog, pg_temp`、`lock_timeout=2s`）、PUBLIC 无 EXECUTE、应用角色有 EXECUTE 且无表级 DELETE/UPDATE。B 还须核查 cleanup_owner 非超级用户/不可登录、有效权限仅目标表 SELECT/DELETE 与 public USAGE，无额外角色成员关系。回滚由管理员 `DROP FUNCTION public.gak_purge_system_exception_log(timestamp,integer)`；B 再撤销 cleanup_owner 的表 SELECT/DELETE 和 schema USAGE，确认专用角色无其他依赖后 `DROP ROLE`，具体 SQL 见脚本头部。回滚不会恢复已清理数据。守卫报 `42501` 沿用任务处理：仅记 WARN、不外抛、不递归采集。

`gak.system-log.retention-days` 默认 30，开启清理时至少 30。默认每天 04:10 Asia/Shanghai，每批 500、最多 40 批、批间 100ms。仅删除严格早于 cutoff 的行；函数与数据库上海时钟的 30 天线取较早者，保护最近 30 天。单批 REQUIRES_NEW（15s），删除语句的锁等待固定 2s，由函数 SET lock_timeout 控制；外层事务的 statement_timeout 默认 10s，可配置；双 int4 事务 advisory 锁与已有 bigint 锁空间隔离。未获锁、函数缺失、权限不足或超时只写应用日志，不进入系统异常采集。清理释放容量后可继续写入，仍在保留期内的行不会因容量而删除。

验证命令、沙箱限制和未验证项见 [验证记录](../../docs/verification/sl-tr-be1.md)。临时 PG 测试只操作测试自行创建的空集群，不接受外部数据库配置。
