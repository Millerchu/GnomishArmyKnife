# 数据字典同步改造后端提示词

本文档用于把现有已经落库的数据字典种子，真正接入到各业务模块里，逐步替代后端当前仍写死在代码中的枚举和校验逻辑。  
本次不是重新设计字典模型，而是基于已经入库的 `gak_data_dictionary / gak_data_dictionary_item / gak_data_dictionary_usage` 做同步改造。

补充说明：

- 我已经把一批字典和使用绑定写入本地数据库
- 种子 SQL 位于：`docs/sql/data-dictionary-seed.sql`
- 已落库的字典共 `24` 个，其中包含当前可立即接入的共享选项和部分“先预留后接入”的候选项

## 1. 已落库的字典清单

### 1.1 当前优先接入的字典

| dictCode | 用途 |
| --- | --- |
| `USER_ROLE_TYPE` | 用户角色 |
| `USER_STATUS` | 用户状态 |
| `WORK_LOG_TYPE` | 工作日志类型 |
| `WORK_LOG_PROJECT` | 工作日志项目，当前先作为选项来源 |
| `TODO_LIST_CODE` | 待办清单类型 |
| `TODO_STATUS` | 待办状态 |
| `TODO_IMPORTANCE` | 待办优先级 |
| `APP_SECURITY_LEVEL` | 应用密级 |
| `APP_ENCRYPTION_MODE` | 应用加密方式 |
| `APP_DATA_SOURCE_MODE` | 应用数据来源 |
| `APP_STATUS` | 应用状态 |
| `APP_ICON_TYPE` | 应用图标类型 |
| `APP_CATEGORY` | 应用分类，当前先作为选项来源 |
| `KNOWLEDGE_CATEGORY` | 经验库分类 |
| `WOW_FACTION` | WoW 阵营 |
| `WOW_CLASS_NAME` | WoW 职业 |
| `WOW_MYTHIC_DUNGEON` | WoW 大秘境副本 |

### 1.2 已预留、后续模块开发时直接复用的字典

| dictCode | 用途 |
| --- | --- |
| `FUEL_TYPE` | 油品类型 |
| `FUEL_FILL_TYPE` | 加油方式 |
| `SOFTWARE_PLATFORM` | 软件平台 |
| `SOFTWARE_RELEASE_CHANNEL` | 软件发布渠道 |
| `SOFTWARE_CATEGORY` | 软件分类 |
| `PERSONAL_BILL_TYPE` | 账单类型 |
| `WOW_CHARACTER_RACE` | WoW 种族，当前先作为选项来源 |

## 2. 已落库的使用绑定

当前已写入 `gak_data_dictionary_usage` 的绑定重点如下：

- 用户管理：
  - `roleCode -> USER_ROLE_TYPE`
  - `status -> USER_STATUS`
- 注册登录：
  - `roleCode -> USER_ROLE_TYPE`
  - `status -> USER_STATUS`
- 工作日志：
  - `typeCodes -> WORK_LOG_TYPE`
  - `projectCode -> WORK_LOG_PROJECT`
- 待办列表：
  - `listCode -> TODO_LIST_CODE`
  - `status -> TODO_STATUS`
  - `importance -> TODO_IMPORTANCE`
- 应用管理：
  - `securityLevel -> APP_SECURITY_LEVEL`
  - `encryptionMode -> APP_ENCRYPTION_MODE`
  - `dataSourceMode -> APP_DATA_SOURCE_MODE`
  - `iconType -> APP_ICON_TYPE`
  - `status -> APP_STATUS`
  - `category -> APP_CATEGORY`
- 经验库：
  - `category -> KNOWLEDGE_CATEGORY`
- WoW角色统计：
  - `faction -> WOW_FACTION`
  - `className -> WOW_CLASS_NAME`
  - `mythicDungeonName -> WOW_MYTHIC_DUNGEON`
  - `raceName -> WOW_CHARACTER_RACE`
- 预留：
  - 油耗统计、软件仓库、个人账单也已经建好 usage 绑定，等对应后端模块开发时直接复用

## 3. 改造原则

### 3.1 要替代什么

优先替代以下三类硬编码：

- 后端枚举类里的固定选项集合
- Service 层里的 `isValid()` / `switch` / `if else` 校验分支
- Controller 或 VO 层里写死的 label 映射

### 3.2 暂时不完全交给字典的内容

- 超级管理员 `ADMIN` 的安全语义
- 登录鉴权、token、权限判断的核心常量
- 仅用于查询视图的伪枚举，例如待办的 `TodoViewCode`

说明：

- 这些值可以让字典负责“显示名”和“选项来源”
- 但不要把安全边界完全改成前端随意配置

## 4. 本轮必须同步的后端模块

## 4.1 `gak-data-dictionary` 模块

当前 `gak-data-dictionary` 只有 CRUD，还没有公共支撑层。  
本轮必须先补齐：

- `DataDictionarySupport`
  - 按 `dictCode` 查询启用项
  - 校验 `itemValue / itemCode`
  - 根据值反查 label
- `DataDictionaryUsageSupport`
  - 按 `appCode + moduleCode + bizFieldCode` 解析绑定字典
  - 按 usage 做校验与选项输出
- 可选接口：
  - `GET /system/dictionaries/options/{dictCode}`
  - `GET /system/dictionaries/options/by-usage?appCode=...&moduleCode=...&bizFieldCode=...`

推荐：

- 先用 Spring Bean + 本地缓存
- 字典/字典项/usage 变更后清缓存

## 4.2 `gak-user` 模块

当前仍使用：

- `UserRoleCode`
- `UserStatus`
- `UserValidationSupport`

本轮要求：

- `roleCode` 校验改为优先读取 `USER_ROLE_TYPE`
- `status` 校验改为优先读取 `USER_STATUS`
- 注册、用户创建、用户编辑、状态变更都统一走字典校验
- `ADMIN` 角色仍保留代码级安全兜底，不能因为字典被改掉就失去超管语义

## 4.3 `gak-worklog` 模块

当前仍使用：

- `WorkLogTypeCode`
- `WorkLogService.normalizeAndValidateTypeCodes`

本轮要求：

- `typeCodes` 改为读取 `WORK_LOG_TYPE`
- 允许多选校验，且继续保持去重逻辑
- `projectCode` 先接 `WORK_LOG_PROJECT` 作为选项来源
  - 当前可先不强制校验
  - 等前端把输入框改成下拉后，再切成强校验

特别注意：

- 当前库里的 `WORK_LOG_TYPE` 已经按前端现状同步成：
  - `NORMAL / LEAVE / BUSINESS_TRIP / SICK_LEAVE / OTHER`
- 不要再沿用旧的开发/测试/会议口径

## 4.4 `gak-todo-list` 模块

当前仍使用：

- `TodoListCode`
- `TodoStatus`
- `TodoImportance`
- `TodoViewCode`

本轮要求：

- `listCode -> TODO_LIST_CODE`
- `status -> TODO_STATUS`
- `importance -> TODO_IMPORTANCE`
- `TodoViewCode` 保留现状，不需要字典化

改造点：

- `TodoItemService` 里的 `isValid()` 校验改成调用数据字典支撑层
- 查询和保存都统一走字典值校验

## 4.5 `gak-permission-management` 模块

当前仍使用：

- `AppSecurityLevel`
- `AppEncryptionMode`
- `AppDataSourceMode`
- `AppIconType`
- 以及 `SystemAppService` 里的多段 normalize/validate 逻辑

本轮要求：

- `securityLevel -> APP_SECURITY_LEVEL`
- `encryptionMode -> APP_ENCRYPTION_MODE`
- `dataSourceMode -> APP_DATA_SOURCE_MODE`
- `iconType -> APP_ICON_TYPE`
- `status -> APP_STATUS`
- `category -> APP_CATEGORY`

边界说明：

- `enabled` 布尔字段继续保留，不建议完全移除
- `status` 可由字典负责选项与显示，`enabled` 继续承担行为语义
- `category` 当前可先做 option source，不一定立刻强校验

## 4.6 `gak-wow-character` 模块

当前仍使用：

- `WowFaction`
- `WowClassName`
- `normalizeMythicDungeonName`

本轮要求：

- `faction -> WOW_FACTION`
- `className -> WOW_CLASS_NAME`
- `mythicDungeonName -> WOW_MYTHIC_DUNGEON`
- `raceName -> WOW_CHARACTER_RACE`
  - 当前先作为选项来源预留

特别注意：

- `className` 当前存的是中文职业名，不是英文 enum code
- 所以 `WOW_CLASS_NAME` 这张字典要按 `itemValue` 做校验，不要误按 `itemCode`
- `mythicBestLevel` 与 `mythicDungeonName` 的成对校验逻辑要继续保留

## 5. 暂未在当前后端仓库内实现、但已预留好字典的模块

这些字典已经入库，不需要再重新设计：

- 油耗统计：
  - `FUEL_TYPE`
  - `FUEL_FILL_TYPE`
- 软件仓库：
  - `SOFTWARE_PLATFORM`
  - `SOFTWARE_RELEASE_CHANNEL`
  - `SOFTWARE_CATEGORY`
- 个人账单：
  - `PERSONAL_BILL_TYPE`

要求：

- 后续这些模块立项开发时，直接使用现有 `dictCode`
- 不要再新建一套重复枚举

## 6. 可直接投喂 AI 的后端增量 Prompt

```text
你正在维护一个 Spring Boot 多模块项目，当前已经有数据字典 CRUD 模块，但各业务模块仍保留了大量硬编码枚举和 isValid 校验。请基于现有数据库中已经落库的数据字典和 usage 绑定，对后端做一次“数据字典同步改造”。

【已存在的数据字典】
请直接使用以下 dictCode，不要重新定义：
- USER_ROLE_TYPE
- USER_STATUS
- WORK_LOG_TYPE
- WORK_LOG_PROJECT
- TODO_LIST_CODE
- TODO_STATUS
- TODO_IMPORTANCE
- APP_SECURITY_LEVEL
- APP_ENCRYPTION_MODE
- APP_DATA_SOURCE_MODE
- APP_STATUS
- APP_ICON_TYPE
- APP_CATEGORY
- KNOWLEDGE_CATEGORY
- WOW_FACTION
- WOW_CLASS_NAME
- WOW_MYTHIC_DUNGEON
- WOW_CHARACTER_RACE

另有预留字典：
- FUEL_TYPE
- FUEL_FILL_TYPE
- SOFTWARE_PLATFORM
- SOFTWARE_RELEASE_CHANNEL
- SOFTWARE_CATEGORY
- PERSONAL_BILL_TYPE

【已存在的 usage 绑定】
请优先按 gak_data_dictionary_usage 的 appCode + moduleCode + bizFieldCode 读取绑定关系。
典型绑定如下：
- APP_USER_MANAGEMENT / SYSTEM_USER / roleCode -> USER_ROLE_TYPE
- APP_USER_MANAGEMENT / SYSTEM_USER / status -> USER_STATUS
- APP_USER_AUTH / AUTH_REGISTER / roleCode -> USER_ROLE_TYPE
- APP_USER_AUTH / AUTH_REGISTER / status -> USER_STATUS
- APP_WORK_LOG / WORK_LOG / typeCodes -> WORK_LOG_TYPE
- APP_WORK_LOG / WORK_LOG / projectCode -> WORK_LOG_PROJECT
- APP_TODO_LIST / TODO_ITEM / listCode -> TODO_LIST_CODE
- APP_TODO_LIST / TODO_ITEM / status -> TODO_STATUS
- APP_TODO_LIST / TODO_ITEM / importance -> TODO_IMPORTANCE
- APP_APP_MANAGEMENT / SYSTEM_APP / securityLevel -> APP_SECURITY_LEVEL
- APP_APP_MANAGEMENT / SYSTEM_APP / encryptionMode -> APP_ENCRYPTION_MODE
- APP_APP_MANAGEMENT / SYSTEM_APP / dataSourceMode -> APP_DATA_SOURCE_MODE
- APP_APP_MANAGEMENT / SYSTEM_APP / iconType -> APP_ICON_TYPE
- APP_APP_MANAGEMENT / SYSTEM_APP / status -> APP_STATUS
- APP_APP_MANAGEMENT / SYSTEM_APP / category -> APP_CATEGORY
- APP_KNOWLEDGE_BASE / KNOWLEDGE_ENTRY / category -> KNOWLEDGE_CATEGORY
- APP_WOW_CHARACTER / WOW_CHARACTER / faction -> WOW_FACTION
- APP_WOW_CHARACTER / WOW_CHARACTER / className -> WOW_CLASS_NAME
- APP_WOW_CHARACTER / WOW_CHARACTER / mythicDungeonName -> WOW_MYTHIC_DUNGEON
- APP_WOW_CHARACTER / WOW_CHARACTER / raceName -> WOW_CHARACTER_RACE

【必须先补的公共支撑层】
请先在 gak-data-dictionary 模块生成公共支撑能力：
1) DataDictionarySupport
   - listEnabledOptions(String dictCode)
   - validateItemValue(String dictCode, String value, boolean required)
   - validateItemCode(String dictCode, String code, boolean required)
   - getLabelByValue(String dictCode, String value)
2) DataDictionaryUsageSupport
   - getBinding(String appCode, String moduleCode, String bizFieldCode)
   - validateByUsage(String appCode, String moduleCode, String bizFieldCode, String value, boolean required)
   - validateMultiValueByUsage(String appCode, String moduleCode, String bizFieldCode, Collection<String> values, boolean required)
   - listEnabledOptionsByUsage(String appCode, String moduleCode, String bizFieldCode)
3) 加缓存，字典和绑定变更时自动失效

【模块改造要求】
1) gak-user
   - 用 USER_ROLE_TYPE / USER_STATUS 替代角色和状态的硬编码校验
   - 保留 ADMIN 的安全语义，不能完全交给字典随意定义

2) gak-worklog
   - 用 WORK_LOG_TYPE 替代 WorkLogTypeCode.isValid
   - typeCodes 继续支持多选和去重
   - projectCode 先作为 options source 预留，不要求立即强校验
   - 注意当前 WORK_LOG_TYPE 的有效值应为：
     NORMAL / LEAVE / BUSINESS_TRIP / SICK_LEAVE / OTHER

3) gak-todo-list
   - 用 TODO_LIST_CODE / TODO_STATUS / TODO_IMPORTANCE 替代 TodoListCode / TodoStatus / TodoImportance 的 isValid
   - TodoViewCode 继续保留代码枚举

4) gak-permission-management
   - 用 APP_SECURITY_LEVEL / APP_ENCRYPTION_MODE / APP_DATA_SOURCE_MODE / APP_ICON_TYPE / APP_STATUS / APP_CATEGORY 替代对应 normalize 和枚举校验
   - enabled 布尔语义继续保留，不要简单删除
   - category 先支持 option source，不一定立即强校验

5) gak-wow-character
   - 用 WOW_FACTION / WOW_CLASS_NAME / WOW_MYTHIC_DUNGEON 替代 WowFaction / WowClassName 和副本名称硬编码校验
   - raceName 先支持 option source
   - className 当前存中文职业名，请按 itemValue 校验，不要按 itemCode
   - mythicBestLevel 与 mythicDungeonName 的配对校验继续保留

【不需要本轮改造的内容】
- TodoViewCode
- 超级管理员安全语义
- 登录/token 协议常量
- 系统错误码

【代码输出要求】
- 不要只改一两个 Service，必须把公共支撑层补齐
- 给出 Controller / Service / Mapper / DTO / VO / Support / Cache 的完整代码
- 给出至少 1 个单元测试，验证通过 usage 绑定做值校验
- 给出每个已改模块的关键改动点说明
```

## 7. 当前建议

如果要控制改造风险，推荐按这个顺序推进：

1. 先补 `gak-data-dictionary` 的公共支撑层
2. 再改 `gak-user / gak-worklog / gak-todo-list / gak-permission-management / gak-wow-character`
3. 最后再让前端逐步把输入框切成字典下拉，例如：
   - `WORK_LOG_PROJECT`
   - `APP_CATEGORY`
   - `SOFTWARE_CATEGORY`
   - `WOW_CHARACTER_RACE`

这样可以先让后端具备字典校验和选项输出能力，再逐步把前端交互切过去，风险最小。
