# SL-BUG-01 定位、修复与验证

本次只修复系统日志表的数据库对象权限。**修复脚本已完成，真实 PostgreSQL 权限和 SQL 验证通过；运行中的 gak_dev 尚未应用，真实 JDBC / MyBatis 映射及 HTTP 复验尚未完成。**

## 来源与根因

- 已核对 `docs/user-board-requirements.md`：2026-11-04 快照没有 SL-BUG-01。本次依据用户明确任务及 `/workspace/gak/work/syslog-test-report.md` 中的缺陷记录，未改写快照或看板状态。
- 已核对环境说明、API 契约、设计、测试报告及冒烟 JSON。管理员合法查询 500、匿名 401 / USER 403 与代码执行路径一致：只有管理员继续访问日志表。
- 应用日志 `/workspace/gak/logs/backend-system-log-v3.log` 只记录写入器失败类型，不能从 `BadSqlGrammarException` 推断表或列缺失。
- 数据库日志 `/workspace/gak/logs/postgres.log:48` 起明确记录 `permission denied for table gak_system_exception_log`，对应 COUNT、详情及过滤 SQL，而不是语法或列名错误。
- 在数据库物理快照的临时副本查询 `pg_class` / `information_schema`：日志表及全部 11 列存在，匹配当前实体和 Mapper。日志表 owner 为 `box`，`gak_dev` 的 SELECT、INSERT 均为 false；应用表和授权表 owner 则为 `gak_dev`。
- 在同一临时副本 `SET ROLE gak_dev` 后，COUNT 和未知数字 ID 查询均复现 SQLSTATE **42501（insufficient_privilege）**。没有查询账号密码字段或登录 token。
- 用当前依赖的 `SQLStateSQLExceptionTranslator` 单独验证：`SQLException` 的 SQLSTATE 42501 被转换为 `org.springframework.jdbc.BadSqlGrammarException`。它属于 SQLSTATE 的 42 类，异常包装名称不足以定位根因。

根因：新增表由与后端运行角色不同的数据库角色创建，升级脚本没有配套授予运行角色对象权限。数据库所有权不自动赋予其所有表的访问权；`CREATE TABLE IF NOT EXISTS` 也不会修正已有表权限。查询失败后的采集器在同一张表上做 COUNT，再次因权限不足失败，造成应用日志中只看到采集降级告警。

## 最小改动

- 新增 `docs/sql/20261107-system-exception-log-privileges.sql`，只对目标日志表授予运行角色 SELECT、INSERT。psql 标识符参数 `app_role` 避免硬编码环境角色。
- 原升级 SQL 增加执行角色和补授权说明。表 owner 或具备授权能力的角色可执行补授权脚本；重复执行安全。
- 不改 Controller / Service / Mapper / 实体 / schema，不重建表、不转移 owner、不授予 UPDATE / DELETE，也不更改既有应用授权或记录。

在已确认目标库和 search_path、以表 owner 或具备授权能力的角色连接后，执行：

```bash
psql -X -v ON_ERROR_STOP=1 -v app_role=gak_dev \
  -f docs/sql/20261107-system-exception-log-privileges.sql
```

连接目标应为隔离开发库 `127.0.0.1:55432/gak_dev`，schema 为 `public`；连接配置由执行环境提供，命令不包含密码。只补数据库权限即可生效，无须重启 8080。

## 已执行验证

| 验证 | 结果 | 证据范围 |
| --- | --- | --- |
| PostgreSQL 16.15，原库临时副本，运行角色复现 | 通过 | 非 owner 的 COUNT / 未知数字 ID 查询报 42501，表及列完整 |
| 同一副本连续执行授权脚本两次 | 通过 | owner 保持 box；gak_dev 的 SELECT / INSERT 为 true，UPDATE / DELETE 为 false |
| 同一副本补授权后的查询与合成写入 | 通过 | 空列表 COUNT=0；未知 ID 返回零行；合成日志插入、大小写忽略筛选、排序及详情 SQL 均成功 |
| 全新临时 PostgreSQL 的 4 项自动回归 | **4/4 通过** | 全部为 postgres --single 真实引擎执行，无数据库 mock |
| framework + system-log 的 JUnit 测试 | **16/16 通过** | 采集、装配、权限和查询单测；这些不充当 PostgreSQL 映射集成证据 |
| Maven 相关模块 test-compile | 通过 | Java 21，包含新增 JDBC 集成测试的编译 |
| 真实 JDBC / MyBatis 集成测试 | **未执行** | 6 个用例随测试类条件跳过，不能记为通过 |
| 当前 8080 HTTP 冒烟 | **未执行** | 未改动或重启服务，也未读取登录凭据 |

PostgreSQL 自动回归覆盖：授权前读写 42501、重复建表不修权限、补授权幂等且不扩大权限、合成写入、COUNT、未知 ID 空结果、pageSize=100 的 SQL、同时间 ID 倒序、类型 / traceId / 时间边界、ILIKE 的大小写及 `%` / `_` / 反斜杠字面量。

可重复执行，无须启动监听服务，不访问原数据库：

```bash
python3 gak-modules/gak-system-log/src/test/python/test_system_log_privileges.py
./mvnw -B -o -pl gak-modules/gak-system-log -am test-compile
```

Java 单测执行补充：本机 Maven 缓存缺少 `surefire-junit-platform:3.2.5`，普通离线 `mvn test` 在测试提供器解析阶段失败，未视为测试失败或成功。实际 16 项通过来自缓存中的 JUnit Jupiter 5.11.4 引擎直接发现 / 执行（Spring 和 Mockito 扩展照常执行），使用预加载的 Byte Buddy agent；输出在 `/tmp/gak-sl-bug-01-java-tests.log`。未修改项目依赖或测试插件配置。

## JDBC / 映射复验入口与当前限制

新增 `SystemExceptionLogPostgresIntegrationTest` 使用真实 PostgreSQL JDBC、真实 MyBatis Mapper 和 Service，不 mock 数据库。它会创建随机 schema 和独立 NOLOGIN 运行角色，刻意确保建表角色与运行角色不同，执行仓库原升级脚本及同一补授权脚本，最终只清理本测试创建的对象。

6 个用例覆盖：授权前 SQLSTATE 42501、补授权幂等和最小权限、默认 20 条 / 上限 100 条分页及排序、关键字转义、类型与 traceId 精确筛选、时间边界、所有列表字段、详情堆栈 / Boolean / LocalDateTime / 后台 null 字段映射、未知 ID 的 404/code404，以及普通用户、撤权、禁用应用的 403。

在允许本地套接字的环境准备独立 PostgreSQL 16 临时 trust 实例，连接用户需具备创建 schema / 角色及 SET ROLE 的能力（可用临时实例初始化的超级用户），运行：

```bash
GAK_SYSTEM_LOG_TEST_URL=jdbc:postgresql://127.0.0.1:55433/postgres \
GAK_SYSTEM_LOG_TEST_USER=sl_ddl_owner \
./mvnw -B -pl gak-modules/gak-system-log -am test \
  -Dtest=SystemExceptionLogPostgresIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

当前沙箱对 TCP / Unix-domain 套接字均返回 `Operation not permitted`，也无法连接交接文档中的 55432；没有申请或尝试绕过限制。真实 PostgreSQL 单用户 SQL 验证不能替代 JDBC 返回值映射和 HTTP 验证。因此不能宣称当前运行实例已修复，或 SL-BUG-01 已完成端到端验收。

## 范围记录

保留全部既有未提交改动；没有提交、推送、重启 8080、读取或打印密码 / token。非法分页 HTTP400/code400 与契约中 SYSTEM_LOG_QUERY_INVALID 的差异仅记录（SL-BUG-02），本次未修改。前端、30 天清理和 X-Trace-Id 均未实施。
