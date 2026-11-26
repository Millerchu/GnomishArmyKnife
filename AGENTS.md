# GnomishArmyKnife 后端 Agent 指南

本文件适用于 GnomishArmyKnife 后端仓库。技术栈为 Java 21、Spring Boot 3.4.x、Maven 多模块、MyBatis Plus 和 PostgreSQL，由 `gak-start` 装配业务模块运行。

## 仓库边界与启动清单

- 本仓库维护后端 API、领域逻辑、数据库结构与部署文件，Git 命令在本仓库根目录执行。
- 完整 GAK 工作区中，本仓库位于 `repos/backend`，配套前端位于 `../frontend`，工作区跨端文档位于 `../../docs`；独立克隆时先读本仓库 `README.md`、`docs/architecture.md`、`docs/development.md` 和 `docs/README.md`。
- 修改前执行 `git status --short --branch`，读取目标目录中更具体的 `AGENTS.md`，保留用户已有改动。
- 先明确业务场景、影响模块、接口契约和验收方式，优先完成后端契约、前端接入与验证的小切片。
- 除非用户明确要求，否则不要提交或推送 Git；提交时只暂存本次任务文件。
- 不提交数据库密码、NAS 部署 Token、私钥等秘密；保留本地 `gak-start/src/main/resources/application.properties` 配置，避免输出其中的凭据。

## 目录职责

| 路径 | 职责 |
| --- | --- |
| `pom.xml` | 聚合模块与统一依赖、插件版本 |
| `gak-start` | 应用入口、配置、启动依赖与可执行 JAR |
| `gak-start/src/main/resources/schema.sql` | 数据库结构与初始化数据 |
| `gak-framework` | 响应、异常、序列化、字典与消息公共契约 |
| `gak-common` | 预留公共模块，是否已有实现以当前源码为准 |
| `gak-modules/*` | 按业务划分的 Controller、Service、Mapper、Domain、DTO/VO 与测试 |
| `docs`、`docs/sql` | 当前架构、开发说明与补充 SQL |
| `doc` | 历史设计与提示词资料，使用前核对现行实现 |
| `deploy/nas` | NAS Compose、部署脚本与部署说明 |

## 接口与跨端协作

- API 变更同时检查前端调用，以 Controller、DTO/VO、Service 的当前实现核对字段、状态、默认值、校验与错误语义。
- 普通成功码为字符串 `"0"`，分页通常返回 `list` / `total`；Long ID 全局序列化为字符串，下载和 SSE 单独处理，避免破坏前端约定。
- 浏览器请求通常带 `/api`，由代理去掉该前缀后进入 Controller；新增接口沿用现有映射方式。
- 登录校验、应用权限、管理员身份及数据归属在后端验证，不能依赖前端可见性或导航守卫。
- 新增模块时检查根/业务聚合 POM、`gak-start/pom.xml`、DDL、授权与测试，再同步前端页面、路由、API wrapper、应用目录和图标。
- 新增需要导入导出的资源时检查数据迁移 Handler 注册；资源导出不等于全库及附件备份。
- 修改行程、健康与账单关联时检查来源字段、同步、删除和迁移；附件文件与数据库事务的回滚边界分别处理。
- 消息变更沿用 `gak-framework` 消息契约及提交后通知机制，核对 SSE 与轮询回退。
- 跨端说明在完整工作区的 `../../docs` 维护，后端实现细节在本仓库 `docs` 维护。

## 快速新增同步规则

- 每次修改应用功能或接口时，必须评估主界面“快速新增”的同步影响；新增应用或业务数据类型时，检查是否需要对应快捷入口。
- 新增接口的字段、默认值、选项、校验、权限、依赖数据或业务副作用变化时，同时检查应用页面和快捷表单，不能只更新后端契约。
- 完整工作区中重点检查 `../frontend/src/constants/quickCreateRegistry.js`、`../frontend/src/components/QuickCreateDialog.vue` 和 `../frontend/src/views/Home.vue`；这些类型独立维护，应用目录更新不会自动同步。
- 快捷入口必须具备可用接口和对应权限；宝宝、车辆等关联对象及字典选项需提供正确的数据来源、默认值和错误语义。
- 优先复用业务规则与编辑组件，防止快捷入口绕过业务约束；前端同步验证桌面端、移动端、权限、初始化、校验及提交，并更新相关测试。
- 单独维护后端而无法修改配套前端时，在交付说明中列明待同步字段和行为；每次交付注明已同步内容、不适用原因或未完成缺口。

## 环境、运行与验证

需要 JDK 21 和 PostgreSQL。以下命令在本仓库根目录执行：

```powershell
.\mvnw.cmd -version
.\mvnw.cmd test
.\mvnw.cmd -pl gak-modules/<module-name> -am test
.\mvnw.cmd -pl gak-start -am -DskipTests compile
```

默认运行受影响模块测试，后端代码改动至少完成最小 Maven 编译验证。检查实际执行与跳过数；`-DskipTests` 不能表述为测试通过。数据库集成测试所需配置见 `docs/development.md` 和相关模块说明。

完整工作区中优先在工作区根目录运行：

```powershell
.\scripts\use-jdk21.ps1
.\scripts\backend-dev.ps1 -version
.\scripts\backend-dev.ps1
```

脚本使用工作区的 `tools/jdk-21`，无需修改系统 JDK。独立克隆时自行配置 JDK 21，按 `README.md` 设置数据库环境变量并启动。

- 启动前核对目标数据库与 SQL 初始化配置；保持工作目录一致，避免相对附件路径变化。
- 仅修改文档时检查内容、路径与 `git diff --check`，无需运行应用测试或编译。
- 交付时说明文件变化、契约影响、快速新增同步情况、验证命令和真实限制；区分编译、测试、联调及部署状态。
- 可复用的后端规则补充到本文件或本仓库 `docs`，跨端规则同时检查工作区指南。

## Code Standard

本仓库默认遵循《阿里巴巴 Java 开发手册》中的通用约束，并结合当前项目分层落地。后续新增或修改代码时，优先遵守以下规则。

### 用户看板需求入口

- 处理用户看板中的需求或 Bug 前，先读 [用户看板需求记录](docs/user-board-requirements.md)，按用户指定的看板 ID 或标题定位条目。
- 该文档记录各应用的需求；根据所属应用定位业务模块，跨端需求以相同看板 ID 关联。原始需求、开发分析和验收结果分别记录。
- 已整理 2026-11-04 NAS 看板的全部 39 条记录；该文档是一次性快照，后续变更需核对来源时间，长期同步方式待定，文档不授权自动执行全部条目。

### 通用原则

- 代码先清晰，再追求技巧；避免炫技式写法。
- 命名必须表达业务语义，禁止使用 `tmp`、`data`、`obj` 这类弱语义名称作为长期变量名。
- 一个方法只做一件事；超过 50-80 行的方法优先考虑拆分。
- 删除无用代码、无用常量、无用注释，避免“先留着以后可能用”。
- 复杂逻辑必须补充解释“为什么这样做”的注释，不写翻译代码字面的注释。

### Java Backend

- Controller 只做参数接收、鉴权透传和响应包装，不承载业务编排。
- Service 负责业务规则、数据聚合和事务边界；事务方法必须标注 `@Transactional`。
- DTO 用于入参，VO 用于出参，Domain/Entity 只映射数据库字段，不混用。
- 所有入参校验优先放在 DTO 上，业务合法性校验放在 Service 中。
- 禁止 magic number 和 magic string，重复使用的业务值必须抽成 `private static final` 常量。
- `if/else` 嵌套超过 3 层时必须重构，优先使用卫语句提前返回。
- `BigDecimal` 涉及金额、单价、里程/油量精度时必须显式指定 `scale` 和 `RoundingMode`。
- 查询条件、排序条件必须显式书写，不依赖数据库默认顺序。
- 抛出异常时信息要可读、可定位，优先使用明确的业务提示。
- 新增表结构时必须同步：
  - `schema.sql`
  - 对应 domain/entity
  - mapper / service / controller
- 默认补充类注释和关键方法注释；私有方法在逻辑不自解释时也应补短注释。

### Java 类设计

- 原则上不要新增内部类（Inner Class / Nested Class），不要为了减少文件数量而使用内部类。
- DTO、VO、BO、Query、Command、Context、Result 等数据对象创建独立 Java 类，业务枚举创建独立 Enum 类。
- 只有同时满足以下条件时，才允许使用 `private static` 内部类：仅当前类内部使用、不属于业务领域模型、不需要序列化、不被 Spring/MyBatis/Jackson 等框架扫描或引用，且仅作为算法或局部处理的辅助数据结构。

### SQL / Schema

- 表名、字段名统一使用小写下划线风格。
- 建表时同时补索引，索引名表达用途，避免无意义缩写。
- 初始化数据必须可重复执行；种子数据优先使用 `ON CONFLICT` 或幂等写法。
- 删除或更新初始化数据时，先考虑幂等性和历史兼容性。

### Comment

- 注释解释业务约束、边界条件、兼容原因，不解释显而易见的赋值动作。
- TODO 必须带明确意图，禁止只写 `TODO`。
- 面向接口、聚合计算、兼容逻辑、精度处理的代码必须有简洁注释。

### Validation

- 后端代码提交前至少保证相关模块可编译，默认执行最小 Maven 编译验证。
- 跨端任务若修改前端代码，按前端仓库 `AGENTS.md` 执行测试与构建。
