# 系统异常日志

只记录后端未处理异常，供管理员分页查询。不是访问日志，也没有删除、导出或远程采集。

查询字段、权限和示例见 [后端契约](../../docs/frontend/system-log-backend-contract.md)。契约里的 JSON 都是合成示例。

## 采集边界

会尝试入库的只有：

- 进入 `GlobalExceptionHandler`、且不属于业务校验、入参错误、4xx 或客户端断开的异常。
- Spring Boot 自动配置的 `applicationTaskExecutor` 上未被捕获的异常，来源记为 `ASYNC_TASK`。请求方法和路径为 null。
- Spring Boot 自动配置的 `taskScheduler` 上未被捕获的异常，来源记为 `SCHEDULER`。请求方法和路径为 null。`@Scheduled` 是否真正使用这个调度器，取决于运行中的 Spring 上下文只有这一套自动配置调度器。

不会入库的包括：业务异常、参数校验、认证拒绝、数据迁移任务里已经捕获并写在任务状态上的异常、附件清理里已经捕获的异常、消息 SSE 虚拟线程，以及其他自建线程池。因此不能把它理解成全部后台任务的异常都已覆盖。

异常消息正文在落库前丢弃，只留异常类和调用位置。HTTP 路径只留 Spring 已匹配的路由模板；没有可靠模板时为 null，不回退到 requestURI。堆栈超过 30 帧或 4000 字时以 `...[truncated]` 结尾，并把 `stackTruncated` 记为 true。不记录用户名、IP、请求头、请求体、口令、token 或密码备忘录原文。

调用方最多等待写入 2 秒。超时不取消任务，不保证数据库操作两秒内结束。`REQUIRES_NEW` 只有 Spring 代理调用才生效；现有单测没有覆盖真实 PostgreSQL 事务或锁等待。`occurredAt` 使用 JVM 默认时区的无偏移本地时间，仓库未固定 Asia/Shanghai。失败响应不带回追踪编号，也没有 `X-Trace-Id`。

写入失败、数据库不可用或队列已满时只放弃等待或放弃这条日志，不改变原来的业务结果，也不删除历史。`gak.system-log.retention-days` 默认 30，按天清理 **未实现**。达到 `gak.system-log.max-rows` 后停止新写入。
