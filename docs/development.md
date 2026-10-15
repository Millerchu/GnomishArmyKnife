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

上传配置当前为单文件/单请求各 200MB，还需考虑代理和业务限制。图标、归档、健康文件公开路径前缀默认带 `/api`，网关路径改变时核对相应 `GAK_*_PUBLIC_URL_PREFIX`。

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
