# SL-W1 分页修复与独立写入验证

A 已修复，新增分页回归测试通过。B 的独立 PostgreSQL 集成测试已实现并编译通过，但本沙箱禁止创建套接字，运行在分配随机端口时失败，尚未取得真实 JDBC 落库通过证据。

报告日期：2026-11-08。任务依据：用户本次明确要求、`/workspace/gak/work/system-log-api-contract.md` 第 41 行及 `/workspace/gak/work/syslog-design.md`。已核对 `docs/user-board-requirements.md`，该快照没有本次系统日志缺陷条目，未修改快照或看板状态。

## 本次改动文件

| 文件 | 改动 |
| --- | --- |
| `gak-modules/gak-system-log/src/main/java/com/gak/systemlog/controller/SystemExceptionLogExceptionHandler.java` | 仅系统日志查询的分页范围校验转换为契约响应 |
| `gak-modules/gak-system-log/src/test/java/com/gak/systemlog/controller/SystemExceptionLogPaginationTest.java` | 新增真实 MVC 绑定、校验和响应回归 |
| `gak-modules/gak-system-log/src/test/java/com/gak/systemlog/service/SystemExceptionLogWriteIntegrationTest.java` | 新增显式系统属性启用的真实 Spring / JDBC / MyBatis 写入集成测试 |
| `gak-modules/gak-system-log/src/test/java/com/gak/systemlog/support/TemporaryPostgres16.java` | 新增测试自有 PG16 生命周期、随机端口、失败诊断及清理 |
| `docs/superpowers/plans/2026-11-07-sl-w1-fix.md` | 实施与检查记录 |
| `docs/verification/sl-w1-fix.md` | 本报告 |

仓库开始时已有大量未提交改动；本次保留这些内容，只在上述现有处理器中增量修改，其余为新增文件。未修改全局处理器、DTO、Service、生产配置、原 SQL、依赖或接口路径。生成的编译产物、日志和修复前处理器副本位于模块 `target/`。

## A：修复说明

根因：`@Valid SystemExceptionLogQuery` 先触发 Bean Validation，非法分页未进入 Service 的 `SYSTEM_LOG_QUERY_INVALID` 分支，而是由全局校验处理器返回 code `400`。

系统日志专用 advice 新增 `MethodArgumentNotValidException` 分支，只识别 `pageNo` / `pageSize` 的 `Min` / `Max` 范围约束，返回 HTTP 400、code `SYSTEM_LOG_QUERY_INVALID`、message `分页参数不合法`。其他字段校验和格式绑定错误保留原 code 与提示生成方式；现有详情类型错误分支保持原样，其他控制器不受该 advice 影响。

用户描述中的页码参数为 pageNum；实际 DTO 与 API 契约均使用 `pageNo`，本次沿用契约名称，不新增别名。页码约束沿用原 DTO 的 1 到 1000000，每页条数沿用 1 到 100。

新增测试覆盖：两个分页参数的零、负数、超上限，合法上下界，关键字长度校验，非数字分页与错误时间的绑定，以及详情 ID 格式错误。非法校验同时断言未调用查询 Service。

## B：实现说明与证据边界

- 默认跳过；仅在 `-Dgak.system-log.write-it=true` 时运行。不读取现有数据库 URL、账号配置或凭据。
- 固定使用 `/usr/lib/postgresql/16/bin` 的 `initdb` / `pg_ctl`，初始化空 trust 集群，临时目录在模块 `target/system-log-pg16-*`。先申请 loopback 随机空闲端口，排除 8080 和 55432；数据库只监听该端口，关闭 Unix socket。
- 缺少所需 PG 二进制时通过 JUnit assumption 跳过并给出路径说明；其他启动失败明确失败，不伪装成跳过。启动失败和测试结束均清理自有集群；若停库失败，保留目录并报告异常，避免删除正在运行的集群文件。
- 从 `schema.sql` 提取日志表及升级脚本所依赖的用户、应用、授权表 DDL 和两个原唯一索引；避免执行账号凭据种子。再执行原日志升级 SQL 及替换 `app_role` 标识符后的原授权 SQL。
- 建表 owner 与应用 LOGIN 角色不同；应用角色无超级用户、建库、建角色、复制或绕过 RLS 权限。JDBC 直接以应用角色连接，断言日志表只有 SELECT / INSERT，没有 UPDATE / DELETE / TRUNCATE / REFERENCES / TRIGGER。
- 使用真实 `AnnotationConfigApplicationContext`，导入生产 `GlobalExceptionHandler`、`PersistentSystemExceptionCapture`、`SystemExceptionLogWriter`，装配真实 MyBatis Mapper 和 `DataSourceTransactionManager`。启用事务代理并断言 Writer 是 AOP 代理，没有 mock 数据库或直接 new Writer。
- 在合成请求上下文和外层事务中调用容器的现有全局处理器，固定异常类、异常消息、cause 和调用位置。处理器调用真实捕获组件，捕获组件经代理 Writer 写入；随后外层事务回滚，再用独立连接检查唯一日志行。
- 断言异常类型、脱敏摘要、堆栈类名和位置、cause、无异常消息、无动态路径与查询串、32 位小写十六进制 traceId、HTTP 来源和方法、路由模板、时间与 ID。原响应仍为 HTTP 500 / code `500` / `服务器内部错误`。
- 没有注册故障 HTTP 端点或任何生产可达触发入口；测试直接调用现有处理器 Bean，不启动 HTTP 服务。

上述为测试代码的实现与断言范围。当前环境在随机端口申请阶段失败，未执行建表、Spring + JDBC 写入、权限断言、事务回滚隔离和落库字段断言；不能将这些记为运行通过，也未验证 JDBC 锁等待或两秒超时。

## 实际验证命令与结果

以下命令从仓库根目录执行，除单独说明外无需运行后端。

### 已运行通过

```bash
./mvnw -B -o -pl gak-modules/gak-system-log -am test-compile
git diff --check
```

相关模块生产代码和全部测试源码编译成功；差异空白检查通过。编译日志：`gak-modules/gak-system-log/target/sl-w1-test-compile.log`。

由于缺少离线 Surefire provider，实际单测执行复用了本环境先前已有的缓存 classpath 和 JUnit 引擎启动器，预加载 Byte Buddy agent 以避开沙箱的动态 attach 限制。没有修改 Maven / Mockito 配置。执行命令：

```bash
java \
  -javaagent:/home/box/.m2/repository/net/bytebuddy/byte-buddy-agent/1.15.11/byte-buddy-agent-1.15.11.jar \
  -cp "$(cat /tmp/gak-sl-bug-01-classpath):gak-common/target/classes:gak-modules/gak-system-log/target/test-classes:gak-framework/target/test-classes" \
  /tmp/GakJUnitEngineRunner.java \
  com.gak.systemlog.controller.SystemExceptionLogPaginationTest \
  com.gak.systemlog.service.SystemExceptionCaptureAndQueryTest \
  com.gak.systemlog.service.SystemExceptionSanitizerTest \
  com.gak.systemlog.config.SystemExceptionTaskBoundaryConfigurationTest \
  com.gak.systemlog.config.SystemLogSpringContextTest \
  com.gak.framework.exception.GlobalExceptionHandlerTest
```

结果：27 项通过、没有失败。其中新增分页测试 13 项。日志：`gak-modules/gak-system-log/target/sl-w1-unit-tests.log`。该命令依赖本环境已有 `/tmp` 文件，外部复验请使用下方 Maven 命令。

默认开关验证命令：

```bash
java \
  -cp "$(cat /tmp/gak-sl-bug-01-classpath):gak-common/target/classes:gak-modules/gak-system-log/target/test-classes" \
  /tmp/GakJUnitEngineRunner.java \
  com.gak.systemlog.service.SystemExceptionLogWriteIntegrationTest
```

结果：测试类因未设置系统属性明确跳过，无数据库启动尝试；这只验证默认开关，不算集成测试通过。日志：`target/sl-w1-write-it-default.log`（相对于系统日志模块）。

### 已运行失败

```bash
./mvnw -B -o -pl gak-modules/gak-system-log -am test \
  -Dtest=SystemExceptionLogPaginationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

在 Surefire 解析阶段失败：本地缓存没有 `surefire-junit-platform:3.2.5`，离线模式无法下载；该命令没有执行 JUnit 测试。日志：`target/sl-w1-maven-test.log`。

显式启用集成测试的实际命令：

```bash
java -Dgak.system-log.write-it=true \
  -cp "$(cat /tmp/gak-sl-bug-01-classpath):gak-common/target/classes:gak-modules/gak-system-log/target/test-classes" \
  /tmp/GakJUnitEngineRunner.java \
  com.gak.systemlog.service.SystemExceptionLogWriteIntegrationTest
```

结果：`java.net.SocketException: Operation not permitted`，失败点为 `TemporaryPostgres16.availablePort` 的 `ServerSocket`。PG 二进制存在，但还没有创建集群或连接数据库，落库断言未运行。日志：`target/sl-w1-write-it.log`。

修复前回归对照：在模块 `target/sl-w1-baseline-src` 生成去掉新增校验分支的处理器副本，单独编译后将该目录置于 classpath 首位执行分页测试。Python subprocess 实际执行的编译与测试命令等价于：

```bash
javac \
  -cp "$(cat /tmp/gak-sl-bug-01-classpath):gak-common/target/classes:gak-modules/gak-system-log/target/test-classes:gak-framework/target/test-classes" \
  -d gak-modules/gak-system-log/target/sl-w1-baseline-classes \
  gak-modules/gak-system-log/target/sl-w1-baseline-src/com/gak/systemlog/controller/SystemExceptionLogExceptionHandler.java

java \
  -javaagent:/home/box/.m2/repository/net/bytebuddy/byte-buddy-agent/1.15.11/byte-buddy-agent-1.15.11.jar \
  -cp "gak-modules/gak-system-log/target/sl-w1-baseline-classes:$(cat /tmp/gak-sl-bug-01-classpath):gak-common/target/classes:gak-modules/gak-system-log/target/test-classes:gak-framework/target/test-classes" \
  /tmp/GakJUnitEngineRunner.java \
  com.gak.systemlog.controller.SystemExceptionLogPaginationTest
```

未回写生产源文件。结果为分页范围 6 项失败、其余 7 项通过，断言得到 code `400` 而期望 `SYSTEM_LOG_QUERY_INVALID`；修复后分页 13 项通过。日志：`target/sl-w1-pagination-regression.log`（相对于系统日志模块）。

调试期间还遇到旧 `/tmp` 测试 classpath 指向已不存在的 JAR，以及 Mockito 动态 attach 被禁止；改用真实缓存 classpath 和预加载 agent 后通过。这些早期失败没有记为产品回归或通过。

### 未运行

- 新集成测试的真实 PostgreSQL JDBC 落库与代理事务断言未完成。
- 缺失 PG 二进制分支未在本机实际触发，因为本机二进制存在。
- 8080 HTTP 冒烟、55432/gak_dev 查询或授权均未执行。
- 未运行全仓库测试、前端构建或部署；本次没有对应改动。

## 外部需执行的确切 Maven 命令

在允许 loopback 套接字、有 PG16 二进制的环境，以普通用户从仓库根目录执行，允许 Maven 解析正常测试依赖。无需预先创建数据库、填写数据库 URL 或提供密码。

```bash
./mvnw -B -pl gak-modules/gak-system-log -am test \
  -Dtest=SystemExceptionLogPaginationTest \
  -Dsurefire.failIfNoSpecifiedTests=false

./mvnw -B -pl gak-modules/gak-system-log -am test \
  -Dgak.system-log.write-it=true \
  -Dtest=SystemExceptionLogWriteIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

第二条仅选择新建的自启动集成测试，不选择旧的环境变量数据库集成测试。应确认 Surefire 显示该用例通过且未跳过；缺少 PG 二进制时的跳过不算写入证明。

## 遗留问题及交付路径

- B 尚需在允许套接字的环境运行上方第二条命令，取得真实落库证明。当前只可声明测试实现与编译完成。
- 遵守只修改 `/workspace/gak/GnomishArmyKnife` 文件的限制和本次写入权限，未写 `/workspace/gak/work/sl-w1-fix.md`。完整报告保存在仓库的 `docs/verification/sl-w1-fix.md`，需由用户在仓库外复制至指定路径。
- 保留所有现有未提交改动；未 git commit / push，未改 Git 配置，未停止或重启 8080，未连接或修改 55432/gak_dev，未写入任何凭据。
