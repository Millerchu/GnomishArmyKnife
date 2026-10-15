# GnomishArmyKnife 后端

个人工具集合的后端 API，采用 Java 21、Spring Boot 3.4.1、PostgreSQL 和 Maven 多模块。全部业务在 `gak-start` 中装配为一个应用运行。本文于 2026-10-15 按当前源码核对。

## 文档入口

- [架构与模块](docs/architecture.md)：18 个业务模块、接口入口、认证、附件和消息边界。
- [开发与配置](docs/development.md)：环境、启动、数据库与测试。
- [文档索引](docs/README.md)：现行说明与历史资料。
- [NAS 部署](deploy/nas/README.md)、[站内消息](gak-modules/gak-message/README.md)、[行程备份](gak-modules/gak-data-migration/docs/travel-record-backup.md)。

## 技术与目录

| 项目 | 当前配置 |
| --- | --- |
| Java / Spring Boot | 21 / 3.4.1 |
| Maven Wrapper | Maven 3.9.12 |
| MyBatis Plus / MyBatis Spring | 3.5.9 / 3.0.3 |
| 数据库 | PostgreSQL；NAS 镜像为 16-alpine |
| `gak-start` | 入口、配置、schema.sql、可执行 JAR |
| `gak-framework` | 响应、异常、Long 序列化、字典和消息契约 |
| `gak-common` | 预留公共模块，当前没有 src 实现目录 |
| `gak-modules` | 18 个业务模块 |

## 快速启动

以下命令在**后端仓库根目录**执行。需要 JDK 21 和预先创建的 PostgreSQL 数据库，凭据通过当前终端注入，不提交本地密码。

```powershell
$env:JAVA_HOME = 'C:\Path\To\JDK21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/gak'
$env:SPRING_DATASOURCE_USERNAME = 'gak'
$env:SPRING_DATASOURCE_PASSWORD = '<本地数据库密码>'
.\mvnw.cmd -version
.\mvnw.cmd -pl gak-start -am package -DskipTests
java -jar .\gak-start\target\gak-start-1.0.0-SNAPSHOT.jar
```

默认端口 8080。`SPRING_SQL_INIT_MODE` 默认 `always`，启动执行 `schema.sql`，先确认目标数据库。`-DskipTests` 不代表测试通过。

完整 GAK 工作区可在**工作区根目录**执行 `.\scripts\backend-dev.ps1`，使用 `tools/jdk-21` 先打包再启动；携带 Maven 参数时只执行对应命令。无需更改系统 JDK。

浏览器 `/api/auth/login` 经前端代理去掉 `/api`，到后端 `/auth/login`。

## 验证

```powershell
.\mvnw.cmd test
.\mvnw.cmd -pl gak-modules/gak-growth-record -am test
.\mvnw.cmd -pl gak-start -am -DskipTests compile
```

检查实际执行数与跳过数；数据库集成测试另有环境要求，见[开发说明](docs/development.md)。源码核对不等于已部署或通过端到端验收。
