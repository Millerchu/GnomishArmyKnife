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

失败的业务接口本身仍返回原来的错误 JSON，不增加 `traceId` 字段，也不设置 `X-Trace-Id` 响应头。追踪编号只出现在系统日志里，由服务端生成 32 位小写十六进制，不使用调用方传入的值。因此失败请求目前无法靠响应关联到日志。响应头方案留待后续，这次不实现。

`occurredAt`、`occurredFrom`、`occurredTo` 都是没有偏移的本地时间，格式 `yyyy-MM-dd'T'HH:mm:ss`。业务上按 Asia/Shanghai 的墙上时间理解和填写。实现使用 `LocalDateTime.now()`，取的是进程 JVM 的默认时区，仓库和 `application.properties` 都没有把时区设成 Asia/Shanghai。数据库列是 `TIMESTAMP WITHOUT TIME ZONE`，查询按字面比较，不会按会话时区再换算。要让入库数字和上海时间一致，启动后端进程时需要外部配置 `TZ=Asia/Shanghai` 或 `-Duser.timezone=Asia/Shanghai`。本云端 VM 实测为 UTC，且 `TZ` 未设置；本任务没有改共享云电脑的时区，不能把它当成已经配好。

## 采集与脱敏

会尝试入库的只有未处理系统异常：

- 全局异常处理中的非业务、非校验、非 4xx、非客户端断开异常。部分入参错误今天仍返回 500，但不会入库；这次不修改那些状态码。
- Spring Boot 自动配置的 `applicationTaskExecutor` 上未被捕获的异常，来源 `ASYNC_TASK`。
- Spring Boot 自动配置的 `taskScheduler` 上未被捕获的异常，来源 `SCHEDULER`。

不入库：`BusinessException`、参数校验、未登录、无权限、找不到接口、上传过大、客户端断开。后台只覆盖上面两个已接入组件里的未捕获异常。数据迁移任务内部捕获的异常仍只留在任务状态里。附件清理内部捕获的异常仍只进应用日志。消息 SSE 的虚拟线程和其他自建线程池没有接入。请求字段为 null 只说明来源不是一次带匹配结果的 HTTP 请求，不能据此认为所有后台任务都被覆盖。

落库前丢弃异常消息正文。摘要和堆栈只保留异常类与调用位置。HTTP 路径只保留 Spring 已匹配的路由模板；短口令、邮箱、UUID 和其它动态段的实值不入库。未匹配、匹配结果不完整，或模板位置上已经是具体路径时，`requestPath` 为 null。不保存用户名、用户 ID、IP、请求头、请求体、口令、token 或密码备忘录原文。

摘要或类名被截短时以 `...[truncated]` 结尾。超过 255 字符的模板视为不可靠，路径记 null，不截成半截路径。关键字只能搜到已经脱敏的摘要。

写入放在单独线程上，调用方用 `Future.get` 最多等待 2 秒。超时只停止等待，不取消已提交或正在执行的任务，所以不保证数据库写入在两秒内结束；超时之后记录仍可能入库，数据库锁也可能继续等待。队列最多 32 条，写满时新记录直接放弃。原请求仍按原来的错误返回。达到 `GAK_SYSTEM_LOG_MAX_ROWS`（默认 20000）后停止新写入，不删除旧记录。

`SystemExceptionLogWriter.insert` 上标了 `@Transactional(propagation = REQUIRES_NEW, timeout = 2)`，意图是不和业务事务同一次提交，并给出 2 秒事务超时。这只在 Spring 代理调用时生效。现有测试通过 `new` 和 mock 调用写入器，不能证明独立事务、回滚隔离或锁等待。真实 PostgreSQL 事务和锁等待验证交给 Vera，本次没有执行。

## 保留期限（未实现）

`GAK_SYSTEM_LOG_RETENTION_DAYS` 默认 30，只保存团队暂定假设。按天数清理历史日志 **未实现**。当前没有清理任务，达到条数上限时也不删除旧记录。本次不增加自动删除。
