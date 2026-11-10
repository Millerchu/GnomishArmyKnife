# SL-TR-BE1 实现与验证记录

依据：用户已批准的 `/workspace/gak/work/syslog-retention-traceid-plan.md` 第 4 节最终决策，及本轮明确的实现、测试和操作边界。Forge 补充稿仅作参考。本记录描述仓库实现和实际验证结果，不表示已经部署或启用清理。

## 改动文件清单

路径均相对仓库根目录；跨主题文件在主要主题下列出。

### X-Trace-Id

- `gak-framework/src/main/java/com/gak/framework/exception/SystemExceptionCapture.java`
- `gak-framework/src/main/java/com/gak/framework/exception/GlobalExceptionHandler.java`
- `gak-modules/gak-system-log/src/main/java/com/gak/systemlog/service/PersistentSystemExceptionCapture.java`（同时接入 Clock）

### 时钟

- `gak-modules/gak-system-log/src/main/java/com/gak/systemlog/config/SystemLogClockConfiguration.java`（新增）

### 清理

- `docs/sql/20261111-system-exception-log-retention.sql`（新增）
- `gak-modules/gak-system-log/src/main/java/com/gak/systemlog/config/SystemLogRetentionProperties.java`（新增）
- `gak-modules/gak-system-log/src/main/java/com/gak/systemlog/config/SystemLogRetentionConfiguration.java`（新增）
- `gak-modules/gak-system-log/src/main/java/com/gak/systemlog/service/SystemExceptionLogRetentionJob.java`（新增）
- `gak-modules/gak-system-log/src/main/java/com/gak/systemlog/service/SystemExceptionLogPurger.java`（新增）
- `gak-modules/gak-system-log/src/main/java/com/gak/systemlog/mapper/SystemExceptionLogMapper.java`
- `gak-modules/gak-system-log/src/main/java/com/gak/systemlog/service/SystemExceptionLogWriter.java`
- `gak-start/src/main/resources/application.properties`

### 测试

- `gak-framework/src/test/java/com/gak/framework/exception/GlobalExceptionHandlerTest.java`
- `gak-modules/gak-system-log/src/test/java/com/gak/systemlog/config/SystemLogSpringContextTest.java`
- `gak-modules/gak-system-log/src/test/java/com/gak/systemlog/config/SystemLogRetentionConfigurationTest.java`（新增）
- `gak-modules/gak-system-log/src/test/java/com/gak/systemlog/service/SystemExceptionLogRetentionJobTest.java`（新增）
- `gak-modules/gak-system-log/src/test/java/com/gak/systemlog/service/SystemExceptionLogRetentionIntegrationTest.java`（新增）
- `gak-modules/gak-system-log/src/test/java/com/gak/systemlog/service/SystemExceptionLogWriteIntegrationTest.java`
- `gak-modules/gak-system-log/src/test/java/com/gak/systemlog/service/SystemExceptionCaptureAndQueryTest.java`
- `gak-modules/gak-system-log/src/test/java/com/gak/systemlog/service/SystemExceptionSanitizerTest.java`
- `gak-modules/gak-system-log/src/test/java/com/gak/systemlog/service/MatchedRouteTemplateTest.java`
- `gak-modules/gak-system-log/src/test/python/test_system_log_privileges.py`
- `gak-modules/gak-system-log/src/main/java/com/gak/systemlog/service/SystemExceptionSanitizer.java`（边界测试发现并修正提前截断）

### 文档

- `docs/frontend/system-log-backend-contract.md`
- `gak-modules/gak-system-log/README.md`
- `docs/development.md`
- `docs/verification/sl-tr-be1-plan.md`（新增实施清单）
- `docs/verification/sl-tr-be1.md`（本记录）

## 实现说明

### 编号与上海时钟

`capture` 返回服务端生成的 32 位小写 hex；在准备快照及提交写入之前生成，不读取请求头。正常写入、写入失败、等待超时或队列满均返回同一个编号；跳过采集返回 null。后台采集调用方忽略返回值，行为保持原样。

`GlobalExceptionHandler` 只为实际进入采集且返回编号的失败响应接 `X-Trace-Id`。包括未处理异常及 status >=500 的 `ResponseStatusException`。业务/校验类 4xx、跳过采集的错误和成功响应不加头；错误 JSON 字段、状态与文案保持原样。SSE 等响应已经 committed 时不补头。**有头只说明生成了采集编号，不保证当时或最终入库成功。**

共享的具名 Bean `systemLogClock` 使用 `Clock.system(ZoneId.of("Asia/Shanghai"))`，写入的 occurredAt/createdAt 与清理 cutoff 均通过该 Clock 计算。固定 Clock 单测验证上海跨日时间与两个写入时间字段一致；测试也验证写失败和超时返回的编号与尝试写入快照一致。

### 清理及权限

清理开关默认 false；条件配置关闭时无 Job/Purger Bean。开启时校验 retention-days >=30、batch-size 1–1000、max-batches-per-run >=1、batch-interval-ms >=0、合法 cron，并拒绝空或不足 1ms 的超时。关闭时兼容已有 retention-days=0；Writer 仍拒绝负数。

默认每日 04:10 Asia/Shanghai，固定本轮 cutoff，最多 40 批，每批 500，批间 100ms。满批继续；不足一批、未获锁或达到批数上限即结束。每批独立 `REQUIRES_NEW`（15 秒），先设事务本地 lock_timeout=2s / statement_timeout=10s，再抢锁、调用函数。函数调用标记为 MyBatis affectData 并关闭查询缓存。任务自行处理失败和中断，仅写 SLF4J 开始/结束/失败或跳过日志，带 SQLSTATE、类型及计数；不打印行内容，不调用 SystemExceptionCapture，不向调度器传播。中断时恢复中断标记。

SQL 脚本由表属主执行，显式使用 public 表、SECURITY DEFINER、固定 search_path、函数 lock_timeout=2s。非法/NULL 参数先抛 22023，不删数据；cutoff 与数据库上海时钟的 30 天线取 LEAST，按 occurred_at,id 升序选 id，只删严格小于的行。函数创建、PUBLIC 撤权及应用 EXECUTE 授权在同一事务内提交。脚本可重复执行，本身不调用函数、不删除日志、不改表属主；不授表级 DELETE。头部包含 psql 执行方式、属主前置条件和 DROP FUNCTION 回滚语句。

Writer 达容量仍停止新写入，成功插入后复位 capacityWarned；不会按容量删最近记录。

### advisory 锁键选择

本轮代码检索确认：WorkLogMapper 使用单 bigint 的 userId；MessageMapper 使用单 bigint 的 hashtextextended；新增前仓库没有双 int4 advisory 锁用法。

Purger 使用双 int4 的 `pg_try_advisory_xact_lock`，私有常量 `LOCK_NAMESPACE=0x47414B`、`LOCK_RESOURCE=0x534C`，SQL 显式 CAST 为 int4。PostgreSQL 的双 int4 与单 bigint 键空间互不相交，因此无需依赖 userId 范围或概率避开消息散列。事务结束自动释放，不使用会话锁或 FOR UPDATE SKIP LOCKED。代码注释说明隔离原因；新增集成测试的另一会话持有同一组合时，本批返回未获锁且不删数据，释放后可继续清理。

### 截断边界

确认现有限制：堆栈 30 帧/4000 字符、摘要 500、Sanitizer 路径 255。新增测试覆盖 30/31 帧、4000/4001 字符及截断标记、摘要/路径长度。

发现原实现为每一行预留截断标记，导致恰好 4000 字符也误报截断；现只在真实溢出时为标记腾出空间，仍保留不超过 4000 字符的约束。实际 HTTP 采集先经过 MatchedRouteTemplate：超过 255 的模板会被判为不可靠并丢弃；该既有行为保持不变并补测。直接调用 Sanitizer 的超长路径仍按 255 截断，两层语义分别验证。

## 已执行命令和结果

均在仓库根目录执行；未启动/停止/连接 8080 后端，未连接 55432，未连接任何现有数据库。真实数据库验证只使用测试自行创建的临时空 PG16 集群及合成数据。没有修改测试基础设施的跳过或套接字行为。

### 最终通过

1. `./mvnw -B -o -pl gak-modules/gak-system-log -am test-compile`：通过，包含新增 JDBC 集成测试的编译。
2. `./mvnw -B -o -pl gak-modules/gak-system-log -am -DargLine=-javaagent:/home/box/.m2/repository/org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar test`：通过。framework 5 项、user 4 项通过；system-log 共 63 项，55 项执行通过、8 项按条件跳过（写入/清理各 1 项临时 PG JDBC 测试未开开关，原有外部 PG 查询集成测试 6 项未提供环境条件）。不能把跳过项算作通过。
3. `./mvnw -B -o -pl gak-start -am -DskipTests compile`：整个后端 reactor 编译通过；只编译，不启动应用。
4. `python3 gak-modules/gak-system-log/src/test/python/test_system_log_privileges.py`：真实 PG16 单用户模式，最终 7 项通过。覆盖重复脚本、表 SELECT/INSERT 最小权限、无 UPDATE/DELETE、函数 EXECUTE、PUBLIC 无 EXECUTE、严格等值边界、非法参数零删除、2500 行每批 500、按 id 顺序删除、30 天硬下限、无过期行返回零；EXPLAIN 在诊断性关闭顺序扫描后确认既有时间索引可用，小样本实际可能选择 Bitmap Index Scan + Sort，不强制某个计划形态。
5. `git diff --check`：通过。`git status --short` 及 advisory 锁检索用于范围核对；无 deploy、schema.sql、原权限 SQL、权限模块或 TaskBoundaryConfiguration 的改动。

单测覆盖固定上海 cutoff、满批/不足一批/零行/最大批次、未获锁不调用函数、42501/42883/55P03/57014 不外抛，Spring 上下文中的 capture 零调用、开关关闭无 Bean、开启非法配置失败、异常响应接头、4xx 不接头、committed SSE、固定写入时间、超时仍返回编号及容量告警复位。

### 已运行但失败/受限

- 首次 `test-compile` 因新配置类误用了模块未引入的 Lombok 访问器而失败；已换成显式访问器，未新增依赖，后续编译通过。
- 不带 javaagent 的普通 Maven `test` 在 framework 的 Mockito 初始化时失败，原因是沙箱禁止动态附加 agent。使用上述显式 javaagent 命令后通过，没有修改仓库测试基础设施。
- 第一次带 agent 的回归发现新增容量断言错误地把 AtomicBoolean 转成 Boolean；已修正，后续回归通过。
- 新增 EXPLAIN 测试最初强求 Backward 计划而失败，实际使用了正确的时间索引但采用 Bitmap Index Scan + Sort；已改为验证索引可用，不绑定小样本下优化器的具体选择。最终 PG 测试通过。
- 以下临时 PG JDBC 命令已运行，两个测试均在 `TemporaryPostgres16.availablePort` 申请测试套接字时报 `java.net.SocketException: Operation not permitted`，未进入建库或 JDBC 验证；不是测试跳过，也不是功能通过：

```bash
./mvnw -B -o -pl gak-modules/gak-system-log -am \
  -DargLine=-javaagent:/home/box/.m2/repository/org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar \
  -Dgak.system-log.write-it=true -Dgak.system-log.retention-it=true \
  -Dtest=SystemExceptionLogWriteIntegrationTest,SystemExceptionLogRetentionIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

执行输出保存在被 Git 忽略的 `gak-modules/gak-system-log/target/sl-tr-verification/`，最终文件为 `final-unit.log`、`final-compile.log`、`final-start-compile.log`、`final-python-pass.log`；受限 JDBC 输出为 `sl-tr-jdbc.log`。

## 沙箱外需执行的确切命令

前提：Java 21、Maven 所需依赖已缓存、`/usr/lib/postgresql/16/bin` 下有 PG16 二进制、当前用户可执行 initdb 且可开本地测试套接字。测试仍只接受自行创建的临时库，不配置任何外部数据库地址。

```bash
cd /workspace/gak/GnomishArmyKnife
./mvnw -B -o -pl gak-modules/gak-system-log -am test-compile
./mvnw -B -o -pl gak-modules/gak-system-log -am \
  -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar" \
  -Dgak.system-log.write-it=true -Dgak.system-log.retention-it=true \
  -Dtest=SystemExceptionLogWriteIntegrationTest,SystemExceptionLogRetentionIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 gak-modules/gak-system-log/src/test/python/test_system_log_privileges.py
```

若 Maven 缓存在其他位置，只需把 javaagent 参数换成该缓存中 Mockito 5.14.2 JAR 的实际绝对路径。

新增 JDBC 测试预置的场景：非属主应用角色仅 SELECT/INSERT+EXECUTE；边界前/等值/后；2500 行 500 一批及任务批数上限；非法参数；函数硬下限；另一会话持有双 int4 锁时跳过、释放后恢复；ACCESS EXCLUSIVE 持锁时约 2 秒锁超时，以及缩短 statement_timeout 时先触发语句超时；无 EXECUTE/函数缺失不新增 SCHEDULER；后续插入可用；实际事务代理与索引 EXPLAIN。写入测试验证响应头格式、不同于来访头且与提交的 trace_id 一致。这些 JDBC 场景目前只有编译通过，必须在沙箱外执行后才能认定通过。

## 与方案的差异和遗留风险

- 无产品行为取舍偏差。锁按本轮用户明确要求采用双 int4，取代方案较早段落及 Forge 草案中的固定 bigint 建议。
- 增加脚本原子授权事务、超时正值启动校验，以及恰好 4000 字符误截断修复；没有新增依赖、索引或表结构。
- SQL 的严格边界、批删除和硬下限使用真实 PG 单用户测试验证；任务编排用 mock/固定 Clock。单用户 PG 测试不替代 JDBC、Spring 事务和并发验证。
- 未修改仓库外契约和前端，遵守仅本仓库范围；未修改权限模块、deploy、schema.sql、原 SELECT/INSERT 授权、Filter/MDC/logging.pattern 或 TaskDecorator/ErrorHandler 组合。未执行 git commit/push 或配置修改，未进行部署。
- JDBC 事务、真实锁超时/语句超时、竞争锁、写入与响应编号关联仍需上述沙箱外命令确认。缺二进制会按既有设施跳过，需检查 Surefire 的实际执行数。
- 目标环境的表/函数属主和实际授权未连接核实；执行脚本必须满足头部表属主前置条件，清理仍默认关闭，需另行部署并显式开启。
- 历史日志的时间不会因本次显式 Clock 自动改写；若历史行来自非上海默认时区，其时间解释仍需另行评估，不在本轮迁移。
- 写入失败或超时可造成有响应编号而查不到记录；30 天内记录已达容量上限时，时间清理不会释放这些行。这两点是约定行为。

## Forge 审查 L1–L3 修复

2026-11-10，在进场已有未提交改动基础上修复 `/workspace/gak/work/syslog-tr-review.md` 的 L1、L2、L3。本节更新前文的截断与超时实现说明；前文历史验证结果保留，不代表本轮新增测试已经通过。

### 改动文件及行为

- L1：`gak-modules/gak-system-log/src/main/java/com/gak/systemlog/service/SystemExceptionSanitizer.java`、`gak-modules/gak-system-log/src/test/java/com/gak/systemlog/service/SystemExceptionSanitizerTest.java`。仅在实际溢出时腾标记空间，优先回退到可容纳的最后一个完整行；首行都放不下时才截短该行，并避开 UTF-16 代理对中间。结果仍不超过 4000 字符，以 `...[truncated]` 推断截断；恰好 4000 的完整堆栈保持不截断。新增测试覆盖标记前的完整原始帧、原截断点落在 emoji 代理对中间、首行 3990/4001/5000 字符的安全兜底。实际异常头受 255 字符上限约束，首行超长测试直接调用私有行拼接方法，不扩大生产接口。现有 30/31 帧和 4000/4001 字符测试通过。
- L2 生产代码：`gak-modules/gak-system-log/src/main/java/com/gak/systemlog/config/SystemLogRetentionProperties.java`、`gak-modules/gak-system-log/src/main/java/com/gak/systemlog/service/SystemExceptionLogPurger.java`、`gak-modules/gak-system-log/src/main/java/com/gak/systemlog/mapper/SystemExceptionLogMapper.java`、`gak-start/src/main/resources/application.properties`。删除应用侧 lock-timeout 字段、访问器、校验、环境变量映射和 set_config 调用；保留外层事务本地 statement-timeout（默认 10s）。删除语句的锁等待固定 2s，由函数 SET lock_timeout 控制。
- L2/L3 测试：`gak-modules/gak-system-log/src/test/java/com/gak/systemlog/config/SystemLogRetentionConfigurationTest.java`、`gak-modules/gak-system-log/src/test/java/com/gak/systemlog/service/SystemExceptionLogRetentionJobTest.java`、`gak-modules/gak-system-log/src/test/java/com/gak/systemlog/service/SystemExceptionLogRetentionIntegrationTest.java`。配置边界改为验证 statement-timeout；Purger 顺序验证改为只设置语句超时。集成测试继续在 ACCESS EXCLUSIVE 下断言约 2s、不向调度器外抛，并新增日志 SQLSTATE=55P03 断言；缩短语句超时的场景断言 57014。JDBC 安装脚本只取 BEGIN 起的事务正文，注释说明不解析 psql 元命令，真实入口另由 Python 测试覆盖。
- L3：`docs/sql/20261111-system-exception-log-retention.sql`、`gak-modules/gak-system-log/src/test/python/test_system_log_privileges.py`。脚本顶部自行开启 ON_ERROR_STOP，用 `\if :{?app_role}` 检查缺参并输出需 `-v app_role=<应用角色>` 的错误。采用兼容 psql 16 的必失败 SQL（`SELECT 1 / 0`）保证非零退出，注释说明不依赖 `\quit` 的退出码参数；空角色在事务内 GRANT 阶段失败，连接结束回滚。头部仍推荐 `-v ON_ERROR_STOP=1`，要求执行后检查函数 EXECUTE。单用户测试同样只执行事务正文；新增真实 `psql -f` 测试验证缺参非零且函数不存在、空参非零且函数回滚、正确角色成功且有 EXECUTE（并重复执行验证幂等）。该测试只自起临时 PG，使用独立 Unix socket、禁用 TCP，不连接既有数据库。
- 文档：`gak-modules/gak-system-log/README.md`、`docs/frontend/system-log-backend-contract.md`、`docs/development.md` 同步固定 2s 锁等待与可配置外层语句超时；`docs/verification/sl-tr-be1-plan.md` 追加本轮实施清单；`docs/verification/sl-tr-be1.md` 追加本节。

### 实际执行命令和结果

均在本仓库根目录运行。日志位于被 Git 忽略的 `gak-modules/gak-system-log/target/sl-tr-verification/`。

1. **通过**：以下指定 Maven 命令最终 exit=0、BUILD SUCCESS，包含所有本轮修改的主代码和 JDBC 测试源码编译。framework 5 项、user 4 项通过；system-log 65 项中 57 项实际执行通过，8 项按原条件跳过（写入/清理 JDBC 各 1 项开关未启用、原外部 PG 测试 6 项缺环境条件）。Sanitizer 6 项全部通过。日志 `forge-unit-final.log`。

   ```bash
   ./mvnw -B -o -pl gak-modules/gak-system-log -am -DargLine=-javaagent:/home/box/.m2/repository/org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar test
   ```

   **先前失败，已修正**：同一命令首次执行 exit=1，新增测试构造 emoji 附近长帧时误算填充长度，`String.repeat` 收到 -3，尚未进入行为断言。已将该合成完整堆栈长度改为 4000，最终回归通过。首次日志 `forge-unit.log`。

2. **命令成功，部分未执行**：`python3 gak-modules/gak-system-log/src/test/python/test_system_log_privileges.py`，exit=0。单用户真实 PG 的 7 项全部通过；新增 RetentionPsqlScriptTest 在 setUpClass 启动临时 PG 时因 `could not bind Unix address ... Operation not permitted` 跳过整类（unittest 输出 skipped=1，指 1 个类初始化跳过）。该类的 3 个 psql 用例均**未执行，不能记为通过**，需在沙箱外用同一命令补跑。日志 `forge-python.log`。

3. **失败／沙箱受限**：以下 JDBC 命令 exit=1。两项测试均在 `TemporaryPostgres16.availablePort` 申请本地测试套接字时报 `java.net.SocketException: Operation not permitted`，未进入自起 PG 或 JDBC 行为验证；不是跳过或通过。新增的 55P03/57014 日志断言已编译，但真实锁超时与失败封闭仍需沙箱外用同一命令验证。日志 `forge-jdbc.log`。

   ```bash
   ./mvnw -B -o -pl gak-modules/gak-system-log -am \
     -DargLine=-javaagent:/home/box/.m2/repository/org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar \
     -Dgak.system-log.write-it=true -Dgak.system-log.retention-it=true \
     -Dtest=SystemExceptionLogWriteIntegrationTest,SystemExceptionLogRetentionIntegrationTest \
     -Dsurefire.failIfNoSpecifiedTests=false test
   ```

4. **通过**：`git diff --check`。另对进场快照中的 52 个受保护源码／脚本文件执行 SHA-256 比对，内容全部保持一致（权限模块、deploy、schema.sql、docs/sql/20261107-*；不包含构建产物）。

本轮未修改 `gak-modules/gak-permission-management` 的任何文件，保留其进场已有改动，resolveTraceId 修复继续独立。未执行 git commit/push 或 Git 配置修改，未操作或连接 8080 后端、55432/gak_dev 或任何非测试自起的数据库；未写凭据、未部署。真实 psql 入口的 3 个用例和两项 JDBC 集成行为尚待沙箱外补验。

## search_path 加固

2026-11-10，依据 Forge 复审及用户本轮要求，在现有未提交改动上仅加固清理函数的名称解析，不改变保留期、严格边界、批次排序、授权或超时逻辑。

- `docs/sql/20261111-system-exception-log-retention.sql`：函数固定 `SET search_path = pg_catalog, pg_temp`，排除可由拥有 CREATE 权限的角色写入的 public，显式把 pg_temp 放最后，防止临时 schema 抢先解析。两处表引用继续使用 `public.gak_system_exception_log`，时钟函数改为 `pg_catalog.now()`；LEAST 保留语法结构写法，内置类型字面量不变。头部注释说明信任边界。
- Python 权限测试、真实 psql 正常授权用例及 `SystemExceptionLogRetentionIntegrationTest` 均新增 `pg_proc.proconfig` 包含 `search_path=pg_catalog, pg_temp` 的断言。Python 新增劫持回归：非超级用户、非函数属主的应用角色获 public CREATE 后，自行创建带标记的 public.now() 和同名临时日志表。先以 public 优先的调用方 search_path 执行未限定 now()，证明劫持函数确实写入标记；清空标记后调用清理函数，验证只删除 30 天下限前 1 微秒的行，等值及后 1 微秒行保留，返回值依次为 1、0，标记表为空且临时表哨兵行保留。所有攻击对象和临时授权随测试事务回滚。
- `gak-modules/gak-system-log/README.md`、`docs/frontend/system-log-backend-contract.md`、`docs/development.md` 同步上述 search_path、限定引用及安全原因；实施清单同步完成状态。

实际运行结果（本仓库根目录；日志在被 Git 忽略的 `gak-modules/gak-system-log/target/sl-tr-verification/search-path-*.log`）：

1. `python3 gak-modules/gak-system-log/src/test/python/test_system_log_privileges.py`：exit=0。真实 PG16 单用户测试 **8 项全部通过**，包含 proconfig 断言和新增劫持回归。`RetentionPsqlScriptTest` 因沙箱禁止创建 Unix socket（`Operation not permitted`）在 setUpClass 跳过，输出 `OK (skipped=1)`；该类 3 个 psql 用例及其中新增断言未执行，不能算作通过。最终日志 `search-path-python.log`。
2. `./mvnw -B -o -pl gak-modules/gak-system-log -am -DargLine=-javaagent:/home/box/.m2/repository/org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar test`：exit=0、**BUILD SUCCESS**。修改后的 JDBC 测试源码编译通过；framework 5 项、user 4 项通过，system-log 65 项中 57 项执行通过、8 项按原条件跳过（写入/清理 JDBC 各 1 项未开开关，原外部 PG 查询测试 6 项缺环境条件）。日志 `search-path-maven.log`。
3. 额外开启清理 JDBC 集成测试：以下命令 exit=1，唯一测试在 `TemporaryPostgres16.availablePort` 申请测试套接字时发生 `java.net.SocketException: Operation not permitted`，未启动临时 PG、未进入 JDBC 验证；新增 proconfig 断言已编译，但 JDBC 行为仍需沙箱外执行。日志 `search-path-jdbc.log`。

   ```bash
   ./mvnw -B -o -pl gak-modules/gak-system-log -am \
     -DargLine=-javaagent:/home/box/.m2/repository/org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar \
     -Dgak.system-log.retention-it=true \
     -Dtest=SystemExceptionLogRetentionIntegrationTest \
     -Dsurefire.failIfNoSpecifiedTests=false test
   ```

4. `git diff --check`：通过。进场快照中的 52 个受保护文件 SHA-256 全部一致，覆盖权限模块、deploy、schema.sql、docs/sql/20261107-*，保留这些路径原有未提交内容。

本轮未执行 git commit/push 或 Git 配置修改，未启动、停止或连接 8080，未连接 127.0.0.1:55432，SQL 仅在测试自行初始化的临时 PG 集群中执行；未写凭据。清理脚本未部署到目标库。沙箱外需补跑同一 Python 命令及上述 JDBC 命令，确认真实 psql 和 JDBC 用例执行通过。

## B1 表替换提权修复

2026-11-10，依据 `/workspace/gak/work/syslog-tr-review.md` 的“search_path 加固复核”B1、I-S3，在进场已有未提交实现上修复。参考守卫仅用于设计参考，未修改仓库外参考文件，也未执行目标库部署。

### 实现和信任边界

- `docs/sql/20261111-system-exception-log-retention.sql`：安装事务先做只读的表存在/执行者属主检查，再锁表；锁后完整检查执行者为表属主或超级用户、app_role 存在且不是表属主/清理角色或它们的成员、清理角色属性及既有同签名函数属主。所有目录和系统函数显式限定 `pg_catalog`；psql 参数作为 `:'var'` 值或 `:"var"` 标识符引用，DO/函数体经 `pg_catalog.format` 和 `\gexec` 生成，避免在 dollar-quoted 字符串内直接插入变量。
- **期望属主采用安装时固化的角色 OID**：在安装锁内从 `pg_class.relowner` 读取，使用 psql `\gset` 保存，再嵌入函数体数字常量。两种模式运行时均比较 `relowner` 与此 OID；A 另比较表属主与 `current_user` 的角色 OID。OID 不随角色改名改变，也不依赖调用者可修改的 GUC 或可替换的元数据表；应用 DROP 后重建的表属主是应用角色，不能通过守卫。合法变更表属主须重新安装。
- 函数第一条执行语句为 `LOCK TABLE public.gak_system_exception_log IN ROW EXCLUSIVE MODE`；随后在 DELETE 前核验普通表、固定属主、`pg_trigger` 中无任何触发器（含 internal）、`pg_rewrite` 无规则、RLS 与强制 RLS 都关闭、`pg_inherits` 无继承子表，违反约束抛出明确消息和 `42501`。直接检查全部触发器，不依赖 relhastriggers：外键 ON DELETE 的内部 RI 触发器也能执行代码或级联到其他表，本日志表不应有触发器或被外键引用。
- 模式 A 不传 cleanup_owner，显式将函数 OWNER 设为表属主（执行者是另一超级用户时也如此）；模式 B 推荐 `-v cleanup_owner=gak_log_cleaner`，不存在时事务内创建具有全部指定限制的专用 NOLOGIN 角色，已有角色若有超级用户/登录/BYPASSRLS/CREATEROLE/CREATEDB 则拒绝。只给 public USAGE 和目标表 SELECT/DELETE，转移函数属主；PUBLIC 撤权、app_role 的 EXECUTE、固定路径 `pg_catalog, pg_temp` 和 2s 锁超时保留。
- I-S3：A 只接受已由表属主持有的同签名函数；B 接受表属主（A -> B 迁移）或指定 cleanup_owner，其余拒绝，不能静默替换。两种模式可各自重复安装；B -> A 按 I-S3 要求先由管理员显式恢复函数 OWNER 为表属主再执行 A。头部与三份文档均说明命令、前置条件、权限快照和回滚（DROP FUNCTION；B 再 REVOKE、DROP ROLE）。
- Python 真实 psql 用例分 A/B 两类（分别 11/14 项）：正常边界、30 天下限、参数、分批排序、幂等与权限快照、数据库 owner 替换表攻击、普通/内部 RI 触发器、规则、RLS/强制 RLS、继承子表、search_path/临时表劫持、应用属主/成员拒绝、其他角色抢占函数、非属主安装拒绝、超级用户代非超级用户表属主安装；B 另覆盖危险角色属性/成员关系、模式迁移、包含引号的角色名。攻击真实使用 `ALTER DATABASE postgres OWNER TO sl_runtime` 和 `public OWNER pg_database_owner`，没有用单独 GRANT CREATE 代替。
- 触发器攻击先做可执行正对照，再用序列的 is_called 和标记表共同断言 0 次调用；序列变化不受异常子事务回滚影响，避免“执行后回滚标记”导致假阴性。另在单用户 PG 中复用同一攻击及六种结构变体，覆盖 A/B，不需要 socket。
- JDBC 清理集成测试改为通过 `TemporaryPostgres16.executeScript` 调真实 psql 安装完整脚本，移除截取 BEGIN 后 SQL 的旧辅助逻辑；只使用测试自起实例的端口和身份。应用 Purger/Job 无需改动，现有 SQLSTATE=42501 用例验证仅 WARN、不外抛、不采集；保留期、批次、超时等其他逻辑不变。

### 实际验证

日志均位于被 Git 忽略的 `gak-modules/gak-system-log/target/sl-tr-verification/b1-*.log`。

1. `python3 gak-modules/gak-system-log/src/test/python/test_system_log_privileges.py -v`：**exit=0，11 项实际执行通过，2 个 psql 类初始化跳过**。单用户真实 PG16 已验证两模式的库 owner 表替换攻击均报 42501、触发器 0 次、过期行保留；两模式的普通/内部触发器、规则、RLS/强制 RLS、继承子表共 12 个结构子场景均拒绝；B 正常清理/30 天下限和既有单用户权限、边界、分批、劫持回归通过。两个真实 psql 类因 `could not bind Unix address ... Operation not permitted` 跳过，**25 项真实 psql 入口用例未执行，不计为通过**，需沙箱外用同一命令补跑。SQL-only 用例执行脚本的同一前置检查与函数格式模板，但不能替代 psql 元命令/变量引用、退出码和完整授权流程验证。日志 `b1-python.log`。
2. 指定命令 **exit=0、BUILD SUCCESS**，JDBC 测试源码编译通过。framework 5 项、user 4 项通过；system-log 65 项，57 项执行通过、8 项按既有条件跳过（写入/清理 JDBC 开关未开启各 1，外部查询 PG 缺条件 6）。日志 `b1-maven.log`。

   ```bash
   ./mvnw -B -o -pl gak-modules/gak-system-log -am -DargLine=-javaagent:/home/box/.m2/repository/org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar test
   ```

3. 额外开启清理 JDBC：**exit=1、1 error**，在 `TemporaryPostgres16.availablePort` 创建本地测试套接字时发生 `java.net.SocketException: Operation not permitted`，未启动临时库、未进入 psql/JDBC 验证；不是功能通过。需沙箱外补跑。日志 `b1-jdbc.log`。

   ```bash
   ./mvnw -B -o -pl gak-modules/gak-system-log -am \
     -DargLine=-javaagent:/home/box/.m2/repository/org/mockito/mockito-core/5.14.2/mockito-core-5.14.2.jar \
     -Dgak.system-log.retention-it=true -Dtest=SystemExceptionLogRetentionIntegrationTest \
     -Dsurefire.failIfNoSpecifiedTests=false test
   ```

4. `git diff --check`：通过。对进场记录的 52 个受保护文件进行 SHA-256 比对，全部一致，覆盖权限模块、deploy、schema.sql、docs/sql/20261107-*，保留其进场改动。未执行 commit/push、未改 Git 配置；未启动/停止/连接 8080，未连接 127.0.0.1:55432，SQL 只在测试自行创建的临时集群中执行，未写凭据。完整 psql/JDBC 流程仍需沙箱外补验，清理脚本未部署。

### B1 沙箱外回归失败修复（2026-11-10）

用户反馈沙箱外 Python 回归共 36 项，32 项通过、4 项失败：A/B 两模式各有一次非属主安装错误消息不明确、一次分批测试读取旧快照。以下为本轮修复及本轮实际验证，不替代后续沙箱外复跑。

- 锁前增加只读 DO 检查，按名称联查 `pg_catalog.pg_class` / `pg_catalog.pg_namespace`；表不存在报 `42P01`，非属主且非超级用户报 `installer must be table owner or superuser` / `42501`。本地 PG16.15 实测，带 schema 名的 `to_regclass('public.表名')` 也会检查 schema USAGE，因此此处使用目录联查，避免消息被 schema 权限错误遮盖。锁后原完整前置检查保留，防止 TOCTOU。
- Python 单用户安装辅助函数复用新增 DO；非属主 psql 用例同时覆盖无/有 public USAGE，均保留指定消息断言。修正同文件 5 处清理调用与状态查询混在一个表达式中的写法：先赋值返回行数，再查删除后的状态；分批分别断言返回值、顺序和剩余数，边界/独立角色/劫持用例补充过期行已删除及保留行/临时表未受影响的断言。
- `PYTHONDONTWRITEBYTECODE=1 python3 gak-modules/gak-system-log/src/test/python/test_system_log_privileges.py -v`：exit=0，**11 项实际执行通过**；两个 psql 类因 Unix socket `Operation not permitted` 在初始化时跳过，**25 项 psql 入口用例未执行，不计通过**。
- 另在测试自建临时 PG16 单用户模式定向验证 8 个场景，全部通过：无/有 schema USAGE 的非属主均获得预期消息和 `42501`、缺表获得预期消息和 `42P01`、非超级用户表属主通过锁前检查；提取 A/B 两模式的分批与劫持用例 SQL，复用其原断言执行，共 4 个场景通过。该定向验证不经过 psql 元命令或完整脚本入口，不能替代沙箱外复跑。
- `./mvnw -B -o -pl gak-modules/gak-system-log -am -DskipTests compile`：exit=0，BUILD SUCCESS（现有类已为最新）；`git diff --check` 通过。本轮只修改清理 SQL、Python 回归文件及本记录，未 commit/push，未连接 8080/55432，未修改 gak-permission-management。

沙箱外复跑：`python3 gak-modules/gak-system-log/src/test/python/test_system_log_privileges.py -v`，应实际执行全部 36 项。

### L-B1/L-B2 文档补充

2026-11-11，依据 Forge“B1 修复复核”的 L-B1、L-B2 及 I-B5，仅补充清理脚本头部整行注释、模块 README 和 development.md：B -> A 恢复 OWNER 后还须 REVOKE 表/schema 权限或确认无依赖后 DROP ROLE；说明数据库 owner 替换函数导致清理静默停止及安装器重跑拒绝的边界；同步部署前/日常巡检的函数与角色只读快照、预期结果，以及模式 B 在 gak_dev/生产使用需用户另行批准。

- 指定自查 `diff <(grep -v '^--' /tmp/sl-tr/retention-22c60ca.sql) <(grep -v '^--' docs/sql/20261111-system-exception-log-retention.sql)`：exit=0、无输出，非注释行逐字节一致；另与进场备份比对，首条 psql 元命令至文件末尾完全一致，所有 dollar-quote 块均未改动。
- `git diff --check` 通过；工作区文件 SHA-256 与进场快照比对，仅本轮指定的四个文件变化，其余文件（含 gak-permission-management）未改动。本轮为注释/文档修改，未运行 Maven 或数据库测试，未执行上述快照查询。
- 未 commit/push，未连接 8080/55432，未部署清理脚本或创建角色、增加授权。
