# 系统日志后端契约

状态：与当前工作分支 `cursor/system-exception-log-0942` 的已实现代码一致，尚未验收，也未部署。本文 JSON 示例全部是合成数据，不是线上日志。

历史提示词曾建议复用 `gak_permission_audit_log` 或字典变更日志；本次没有复用，因为那些表会保存 IP、User-Agent 或业务变更内容。评审已指出不能靠 `requestURI` 加正则去掉短口令，当前实现改为只保存 Spring 匹配到的路由模板。若评审稿使用不同字段名，需要先对齐再改前端。

## 应用入口

| 项 | 值 |
| --- | --- |
| appCode | `APP_SYSTEM_LOG` |
| 名称 | 系统日志 |
| 路由 | `/system-log` |
| 分类 | 系统管理 |
| 数据来源 | `REAL` |
| 图标 | 文字图标，文字为「日志」 |
| 排序 | 150 |

种子在 `schema.sql` 与 `docs/sql/20261107-system-exception-log.sql`。后一份只供隔离库手工执行，本任务不在生产库执行。脚本可重复执行，不会删除已有日志；管理员若已有该应用的授权行（包括已取消），不会被重新授予。

## 权限

后端没有 Spring Security 过滤链。`UserSecurityConfig` 只提供密码编码器。接口自行读取 `Authorization: Bearer <token>`，由 `TokenService` 在进程内内存核对，不是 JWT。

读取列表和详情必须同时满足：

1. token 有效。
2. 用户存在、`enabled = true` 且 `status = ENABLED`。
3. `roleCode` 为 `ADMIN`（忽略大小写）。普通用户即使被授予该应用也不能读取。
4. 应用 `APP_SYSTEM_LOG` 存在、启用，且 `gak_user_app_permission` 中该用户的 `granted = true`。

没有删除、导出或写入接口。

| 场景 | HTTP | code | message |
| --- | --- | --- | --- |
| 未登录或 token 无效 | 401 | 401 | 未登录或登录已过期 |
| 用户不存在、停用或禁用 | 401 | 401 | 用户已失效，请重新登录 |
| 非管理员 | 403 | 403 | 只有管理员可以查看系统日志 |
| 管理员未被授予或应用已停用 | 403 | 403 | 当前管理员未获系统日志应用授权或应用已停用 |
| 记录不存在 | 404 | 404 | 系统日志不存在 |
| 开始时间晚于结束时间、分页不合法 | 400 | SYSTEM_LOG_QUERY_INVALID | 开始时间不能晚于结束时间 / 分页参数不合法 |
| 时间格式错误 | 400 | 400 | 系统日志查询参数格式错误 |
| Bean 校验失败 | 400 | 400 | 对应字段的中文提示 |

成功响应仍是 `{"code":"0","message":"success","data":...}`。`Long` 在实际运行中序列化为字符串。

## 列表

`GET /system/logs`

浏览器经现有代理调用 `/api/system/logs`，代理去掉 `/api`。

查询参数：

| 参数 | 规则 |
| --- | --- |
| pageNo | 默认 1，最小 1 |
| pageSize | 默认 20，最大 100 |
| occurredFrom | 可选，`yyyy-MM-dd'T'HH:mm:ss`，含边界 |
| occurredTo | 可选，同一格式，含边界 |
| exceptionType | 可选，异常类名精确匹配 |
| traceId | 可选，追踪编号精确匹配 |
| keyword | 可选，最长 80。只对已落库的 `summary` 做不区分大小写匹配，`%` 和 `_` 按字面量转义 |

排序固定为 `occurred_at DESC, id DESC`。列表不返回堆栈。

列表项字段：`id`、`occurredAt`、`traceId`、`source`、`exceptionType`、`summary`、`httpMethod`、`requestPath`。

`source` 为 `HTTP`、`ASYNC_TASK` 或 `SCHEDULER`。后两者的 `httpMethod` 和 `requestPath` 为 null。`id` 和 `source` 是页面跳转和解释空请求字段所需要的，评审稿若未列这两项，以本契约为准。

`requestPath` 只保存 Spring MVC 已匹配的路由模板，例如 `/password-memos/{id}`，不含路径变量的实际值，也不含查询串。没有成对的匹配模板和模板变量表时为 null，包括未匹配到 handler 的异常。后端不读取 `requestURI` 作为回退，也不再用正则把具体路径段替换成占位符。

合成示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "list": [
      {
        "id": "880000000000000001",
        "occurredAt": "2026-11-07T21:30:00",
        "traceId": "0123456789abcdef0123456789abcdef",
        "source": "HTTP",
        "exceptionType": "java.lang.IllegalStateException",
        "summary": "java.lang.IllegalStateException at com.example.demo.DemoService.load(DemoService.java:18)",
        "httpMethod": "GET",
        "requestPath": "/demo/items/{id}"
      },
      {
        "id": "880000000000000002",
        "occurredAt": "2026-11-07T21:31:00",
        "traceId": "fedcba9876543210fedcba9876543210",
        "source": "SCHEDULER",
        "exceptionType": "java.lang.IllegalStateException",
        "summary": "java.lang.IllegalStateException at com.example.demo.Cleanup.run(Cleanup.java:9)",
        "httpMethod": null,
        "requestPath": null
      }
    ],
    "total": 2
  }
}
```

## 详情

`GET /system/logs/{id}`

在列表字段之外增加 `stackTrace` 和 `stackTruncated`。堆栈只含异常类名和调用位置，不含异常消息。超过 30 帧或 4000 字符时截断，`stackTruncated` 为 true，且 `stackTrace` 以 `...[truncated]` 结尾。未截断时该标记不出现。

合成示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": "880000000000000001",
    "occurredAt": "2026-11-07T21:30:00",
    "traceId": "0123456789abcdef0123456789abcdef",
    "source": "HTTP",
    "exceptionType": "java.lang.IllegalStateException",
    "summary": "java.lang.IllegalStateException at com.example.demo.DemoService.load(DemoService.java:18)",
    "httpMethod": "GET",
    "requestPath": "/demo/items/{id}",
    "stackTrace": "java.lang.IllegalStateException\n\tat com.example.demo.DemoService.load(DemoService.java:18)\nCaused by: java.sql.SQLException\n\tat com.example.demo.DemoRepository.find(DemoRepository.java:7)",
    "stackTruncated": false
  }
}
```

失败的业务接口仍返回原来的错误 JSON，不增加 `traceId` 字段。仅在实际进入系统异常采集的失败响应上返回 `X-Trace-Id`（未处理异常及 HTTP status >= 500 的 `ResponseStatusException` 等）；编号由服务端生成，为 32 位小写十六进制，不读取或采信请求头。业务/校验类 4xx、成功响应和跳过采集的错误不加该头。SSE 等响应已提交时无法补头，沿用现有处理行为。异步/定时任务只在表中保留编号。

**有头不保证当时已入库或最终写入成功**：写入失败、队列满或等待超时仍交回已生成编号；可在日志页按 `traceId` 精确查询。

`occurredAt`、`occurredFrom`、`occurredTo` 都是没有偏移的本地时间，格式 `yyyy-MM-dd'T'HH:mm:ss`，按 Asia/Shanghai 墙上时间理解和填写。`occurredAt` / `createdAt` 写入与清理共用显式 `Clock.system(ZoneId.of("Asia/Shanghai"))` Bean，不再依赖 JVM 默认时区。数据库列为 `TIMESTAMP WITHOUT TIME ZONE`，查询按字面比较，不按会话时区换算。

## 采集与脱敏

会尝试入库的只有未处理系统异常：

- 全局异常处理中的非业务、非校验、非 4xx、非客户端断开异常。部分入参错误今天仍返回 500，但不会入库；这次不修改那些状态码。
- Spring Boot 自动配置的 `applicationTaskExecutor` 上未被捕获的异常，来源 `ASYNC_TASK`。
- Spring Boot 自动配置的 `taskScheduler` 上未被捕获的异常，来源 `SCHEDULER`。

不入库：`BusinessException`、参数校验、未登录、无权限、找不到接口、上传过大、客户端断开。后台只覆盖上面两个已接入组件里的未捕获异常。数据迁移任务内部捕获的异常仍只留在任务状态里。附件清理内部捕获的异常仍只进应用日志。消息 SSE 的虚拟线程和其他自建线程池没有接入。请求字段为 null 只说明来源不是一次带匹配结果的 HTTP 请求，不能据此认为所有后台任务都被覆盖。

落库前丢弃异常消息正文。摘要和堆栈只保留异常类与调用位置。HTTP 路径只保留 Spring 已匹配的路由模板；短口令、邮箱、UUID 和其它动态段的实值不入库。未匹配、匹配结果不完整，或模板位置上已经是具体路径时，`requestPath` 为 null。不保存用户名、用户 ID、IP、请求头、请求体、口令、token 或密码备忘录原文。

摘要或类名被截短时以 `...[truncated]` 结尾。超过 255 字符的模板视为不可靠，路径记 null，不截成半截路径。关键字只能搜到已经脱敏的摘要。

写入放在单独线程上，调用方用 `Future.get` 最多等待 2 秒。超时只停止等待，不取消已提交或正在执行的任务，所以不保证数据库写入在两秒内结束；超时之后记录仍可能入库，数据库锁也可能继续等待。队列最多 32 条，写满时新记录直接放弃。原请求仍按原来的错误返回。达到 `GAK_SYSTEM_LOG_MAX_ROWS`（默认 20000）后停止新写入，不删除旧记录。

`SystemExceptionLogWriter.insert` 上标了 `@Transactional(propagation = REQUIRES_NEW, timeout = 2)`，意图是不和业务事务同一次提交，并给出 2 秒事务超时。这只在 Spring 代理调用时生效。直接 `new` 和 mock 的单测不能证明独立事务、回滚隔离或锁等待。临时 PG16 写入/清理集成测试通过显式系统属性开启，执行结果见 [本轮验证记录](../verification/sl-tr-be1.md)。

## 保留期限

`GAK_SYSTEM_LOG_RETENTION_DAYS` 默认 30，开启清理时必须 >=30。清理默认关闭：需表属主或超级用户按 [清理函数脚本](../sql/20261111-system-exception-log-retention.sql) 创建函数并授权 EXECUTE，再显式设置 `GAK_SYSTEM_LOG_CLEANUP_ENABLED=true`。应用角色保持 SELECT/INSERT，无表级 DELETE/UPDATE。函数和授权不进入启动 `schema.sql`。

SECURITY DEFINER 清理函数固定 `search_path = pg_catalog, pg_temp`，不搜索可能被有 CREATE 权限的角色写入的 public；pg_temp 显式放最后，防止临时 schema 抢先解析。表引用限定为 `public.gak_system_exception_log`，时钟函数使用 `pg_catalog.now()`。

安装默认模式 A 由表属主持有函数；推荐增加 `-v cleanup_owner=gak_log_cleaner` 使用模式 B，由独立非超级用户 NOLOGIN 角色持有函数，仅授予 public USAGE 和日志表 SELECT/DELETE。两种模式都固化安装时的表属主角色 OID；函数第一条语句锁表后核验普通表、固定属主、无触发器（包括外键内部 RI）、规则、RLS/强制 RLS 和继承子表；A 另要求表属主与 definer 相同，失败报 `42501`。这是为了防止数据库 owner 经 `pg_database_owner` 删除 public 内的表并挂触发器提权；日志表也不允许被外键引用。

安装者必须是表属主或超级用户；B 还需角色创建/授权/函数属主转移权限。应用角色不能是表属主、cleanup_owner 或它们的成员；cleanup_owner 不能是表属主且不得有超级用户、登录、BYPASSRLS、CREATEROLE、CREATEDB 属性。同签名函数属主异常时拒绝替换，前置检查失败非零退出并回滚。A -> B 与同模式重跑幂等；B -> A 先显式恢复函数 OWNER 为表属主。执行后核查函数属主、prosecdef、proconfig、EXECUTE 授权、cleanup_owner 仅 SELECT/DELETE + USAGE 和应用无表级 DELETE。两种命令、详细快照要求和 DROP FUNCTION / B 另 REVOKE 与 DROP ROLE 的回滚步骤见脚本头部及 [模块说明](../../gak-modules/gak-system-log/README.md)。守卫拒绝仅记 WARN、不外抛、不采集，前端无需区分数据库消息。

开启后默认每天 04:10 Asia/Shanghai，按 `occurred_at < 上海当前时间 - retention-days` 分批清理，等于 cutoff 的行保留；函数再强制至少保留 30 天。每批 500、每轮最多 40 批、批间 100ms，可配置。每批独立事务，删除语句的锁等待固定 2s，由函数 SET lock_timeout 控制；外层事务的 statement-timeout 默认 10s，可配置。未获 advisory 锁则跳过；函数缺失、无权限、锁/语句超时等只记应用日志，不递归采集或影响业务。达到行数上限仍停止新写入，时间清理释放容量后可恢复；30 天内行不会因容量上限被删。
