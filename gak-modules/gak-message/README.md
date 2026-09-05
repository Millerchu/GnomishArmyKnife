# 站内消息

提供持久化收件箱、管理员发布、按用户已读状态和单实例 SSE 通知。前端消息中心位于 `/messages`，管理员通过桌面系统菜单的“消息管理”进入 `/system/messages`。消息中心不要求应用授权。

## 发布与事务

业务模块注入 `gak-framework` 的 `MessagePublisher`，调用 `publish(PublishMessageCommand)`。命令包含来源、稳定业务幂等键、发送人、接收用户、分类、重要级别、纯文本标题/正文和可选 `HOME` 跳转目标。实际业务示例见权限管理模块。

- 发布加入调用方事务；授权变更、审计和收件记录一起提交，提交后才通知在线用户。
- 来源与幂等键唯一；接收人去重排序后参与内容摘要。同一键同内容返回原消息，不同内容返回 409。
- 管理员来源由服务端设置为 `ADMIN:<用户ID>`，调用方不能伪造发送者。
- `ALL` 使用发送时有效用户快照。重复请求不会把后来注册的用户加入旧公告。
- “全部已读”使用单条数据库 UPDATE 的语句快照，之后提交的新消息仍为未读。
- 消息内容和收件记录持续保留；无撤回、自动过期或物理删除接口。

## HTTP

外部路径前缀为 `/api/messages`，Nginx/Vite 去除 `/api` 后进入 Controller。HTTP 请求和 SSE 都使用 `Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/inbox` | 当前用户分页，支持 category、unread、pageNo、pageSize |
| GET | `/summary` | 未读总数、分类数量、最近 10 条、权限版本 |
| GET | `/inbox/{id}` | 查看收件记录；查询本身不修改已读状态 |
| PUT | `/inbox/{id}/read` | 幂等标记已读 |
| PUT | `/inbox/read-all` | 全部已读 |
| GET | `/stream` | SSE：ready、message-created、read-changed、heartbeat |
| POST | `/admin/send` | 管理员发送；请求须含 idempotencyKey |
| GET | `/admin/sent` | 发送记录、接收人数、已读人数 |
| GET | `/admin/sent/{id}/recipients` | 接收人及阅读时间 |
| GET | `/admin/recipient-options` | 分页检索有效用户，仅返回 ID、用户名和显示名 |

收件详情与已读接口的 ID 为收件记录 ID，发送记录使用消息 ID。用户选项的 ID 按字符串返回，避免 JavaScript 丢失 BIGINT 精度。

## 连接与部署

- SSE 仅用于通知刷新；数据库是消息唯一来源。客户端每 30 秒补查，页面重新可见、重连、网络恢复时也补查。
- 20 秒心跳；客户端 45 秒无事件中止连接，按 1～30 秒退避重连。页面换路由复用连接，退出或换账号清空状态并终止旧请求。
- 连接总上限 1000、每用户 12；每连接最多一个发送任务，写入超过 5 秒关闭连接，连接最长 30 分钟后重连。
- 后端每分钟记录连接数和推送失败数。开启 `logging.level.com.gak.message=DEBUG` 可查看摘要查询耗时。
- 前端 Nginx 的 `/api/messages/stream` 已关闭缓冲和缓存，读取超时 90 秒。NAS 若还有上游反向代理，也需关闭该路径缓冲，并保证读取超时长于心跳间隔。
- 两张表和索引随 `gak-start` 的 `schema.sql` 初始化，脚本可重复执行。若生产关闭自动初始化，应在发布前执行新增消息 DDL。
- 当前 Token 保存在单实例内存中，重启后需要重新登录。多实例需要先改造共享会话与跨实例通知，本模块未引入 Redis/MQ。

## 验证

在后端仓库根目录执行（测试使用随机隔离 schema，完成后删除）：

```sh
GAK_MESSAGE_TEST_URL=jdbc:postgresql://localhost:5432/postgres \
mvn -pl gak-modules/gak-message,gak-modules/gak-permission-management -am \
  -Dtest=MessageIntegrationTest,PermissionManagementServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test-compile org.apache.maven.plugins:maven-surefire-plugin:3.5.2:test
mvn -pl gak-start -am -DskipTests package
```

数据库用户名默认为当前系统用户，可用 `GAK_MESSAGE_TEST_USER`、`GAK_MESSAGE_TEST_PASSWORD` 覆盖；测试账号需有创建 schema 权限。未设置测试 URL 时 PostgreSQL 集成测试会显式跳过。

测试覆盖真实 HTTP 鉴权、SSE 2 秒内送达、并发幂等、事务回滚、批量插入失败、离线补查、越权访问、群发快照和已读统计。前端新增 SSE 分块解析、会话隔离、汇总去重、详情阅读和发送重试测试，随 `npm test` 执行。
