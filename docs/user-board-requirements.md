# 用户看板需求记录（后端）

## 用途与当前状态

本文件记录系统“用户看板”中各应用的需求和 Bug，供 AI 编程前理解目标、定位代码和核对验收条件；范围不限于看板功能本身。

- 建立日期：2026-10-31。
- 当前仅建立记录结构，尚未导入真实看板条目。下方空表不代表系统看板没有需求。
- 内容如何从看板进入文档、更新频率以及两端如何同步，后续由用户确定；当前没有自动同步或回写机制。
- 看板原始内容、人工补充的开发分析、实际验证结果分开记录。文档中的开发备注不自动修改看板状态。

## AI 编程时的阅读方式

1. 先读本仓库 `AGENTS.md`，再按用户指定的需求 ID 或标题定位条目；文档不代表授权自动执行全部需求。
2. 核对条目的来源时间、看板状态、原始描述及验收条件。信息缺失或与当前用户指令不一致时，先记录待确认点，不自行补成已确认需求。
3. 根据所属应用定位业务代码；除非条目明确涉及看板本身，不要默认修改看板模块。
4. 跨端需求用同一看板 ID 关联两份文档。核对两端共有的原始描述和契约，各端实现分析分别维护；发现差异时保留来源并确认，不静默覆盖。
5. 实施后记录修改范围、执行的验证和未解决问题。代码完成、测试通过、用户验收与看板“已完成”分别注明，不能相互替代。

## 需求索引

尚未录入真实条目。后续每条记录在索引中占一行，并在“需求详情”中保留完整内容。

| 看板 ID | 标题 | 所属应用 | 类型 | 优先级 | 看板状态 | 本端涉及情况 | 来源更新时间 | 详情 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

字段约定：

- `id`：保留看板原始 ID；Long ID 按字符串记录，避免精度丢失。尚未取得 ID 时明确写“未取得”，不要伪造系统 ID。
- `appCode` / `appName`：保留所属应用编码和名称；`type` 为 `REQUIREMENT`（需求）或 `BUG`。
- `priority`：`HIGH`（高）、`MEDIUM`（中）、`LOW`（低）。
- `status`：`PENDING_REVIEW`（待评估）、`PLANNED`（已规划）、`IN_PROGRESS`（进行中）、`COMPLETED`（已完成）、`DECLINED`（不采纳）。枚举是可选值说明，不表示严格的流转顺序。
- `version`、`updatedAt`：取得时一并保留，用于识别条目是否已变更；文档整理时间单独记录。
- 本端涉及情况：待分析、涉及、不涉及；不涉及时注明原因，跨端条目仍保留同一 ID。

## 需求详情

尚未录入。以下是新增条目时复制的模板，不是一条真实需求。

```markdown
### 看板 ID：<原始 ID 或“未取得”> — <原始标题>

#### 来源与原始内容

- 来源环境 / 看板位置：
- 所属应用（appCode / appName）：
- 类型（type）：
- 优先级（priority）：
- 看板状态（status）：
- 数据版本（version）：
- 看板创建 / 更新时间（createdAt / updatedAt，注明时区）：
- 本次记录时间：
- 原始描述（description，保留原意）：
- 相关进度记录（progressLogs，注明时间、状态和备注）：
- 复现步骤、附件或截图引用（如有，排除敏感凭据）：

#### 开发分析（人工或 AI 补充）

- 用户场景、当前表现与预期结果：
- 本端是否涉及及原因：
- 实施范围与本次不做的内容：
- 涉及模块、文件和接口：
- 前后端契约（方法、路径、字段、校验、权限、错误语义）：
- 配套仓库中的同 ID 记录 / 依赖：
- 待确认问题与已确认结论（注明确认时间）：

#### 验收条件

- [ ] <可观察或可复现的结果>

#### 实施与验证记录

- 本端开发进展（独立于看板状态）：
- 实际修改：
- 验证命令 / 操作及结果：
- 未验证事项 / 遗留问题：
- 用户验收结论与时间：
```

## 后端定位与补充重点

需求所属业务模块由条目的 `appCode` 和实际代码确定。后端分析重点记录接口契约、业务规则、权限与归属、事务、数据结构和兼容性；涉及数据库时注明 schema / 升级 SQL 及验证方式。

看板数据本身的源码入口：

- [Controller](../gak-modules/gak-requirement-board/src/main/java/com/gak/requirementboard/controller/RequirementBoardController.java)：接口前缀 `/requirement-items`。
- [Service](../gak-modules/gak-requirement-board/src/main/java/com/gak/requirementboard/service/RequirementBoardService.java)：查询、编辑权限、进度记录与版本冲突处理。
- [DTO](../gak-modules/gak-requirement-board/src/main/java/com/gak/requirementboard/dto)、[VO](../gak-modules/gak-requirement-board/src/main/java/com/gak/requirementboard/vo)、[枚举](../gak-modules/gak-requirement-board/src/main/java/com/gak/requirementboard/enums)：字段和取值以当前源码为准。
- [schema.sql](../gak-start/src/main/resources/schema.sql)：`gak_requirement`、`gak_requirement_progress_log`。
- [服务测试](../gak-modules/gak-requirement-board/src/test/java/com/gak/requirementboard/service/RequirementBoardServiceTest.java)：看板业务规则的既有测试入口。

现有列表 `GET /requirement-items` 为分页数据，详情 `GET /requirement-items/{id}` 包含进度记录；浏览器请求另带 `/api` 代理前缀。这些是当前数据入口说明，尚未确定将来使用哪种方式同步到文档。
