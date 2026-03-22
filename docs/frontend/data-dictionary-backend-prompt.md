# 数据字典后端接口代码生成提示词

本文档用于把系统菜单中的“数据字典”模块一次性生成为后端代码，并把它做成一个可被其他业务模块直接调用的通用支撑能力。  
本次目标不只是补一个 CRUD 页面，而是让数据字典能够逐步替代后端里大量“展示型、校验型、可配置型”的枚举常量，让前端改配置即可生效，尽量减少为了调整选项而重复发版后端。

前端项目当前已固定以下接口路径（基于 `/api` 代理后的实际后端路径）：

- `GET /system/dictionaries`：分页查询数据字典主列表
- `POST /system/dictionaries`：新增数据字典
- `PUT /system/dictionaries/{id}`：编辑数据字典
- `DELETE /system/dictionaries/{id}`：删除数据字典
- `PATCH /system/dictionaries/{id}/status`：切换字典启用状态
- `GET /system/dictionaries/{dictionaryId}/items`：查询指定字典的字典项
- `POST /system/dictionaries/{dictionaryId}/items`：新增字典项
- `PUT /system/dictionaries/{dictionaryId}/items/{itemId}`：编辑字典项
- `DELETE /system/dictionaries/{dictionaryId}/items/{itemId}`：删除字典项
- `PATCH /system/dictionaries/{dictionaryId}/items/{itemId}/status`：切换字典项启用状态

补充目标：

- 后端需要额外生成“数据字典支撑工具类/Facade”，方便 `用户管理 / 工作日志 / 待办列表 / 应用管理` 等模块直接注入调用
- 后端需要设计并保存“数据字典使用情况”，至少要知道“哪个应用的哪个字段在使用哪个字典”
- 本次希望用数据字典逐步替代后端枚举类，但要区分边界：
  - 适合替代：业务选项、展示标签、校验范围、下拉选项
  - 不建议替代：安全语义极强的常量，例如超级管理员保留角色、token 类型、异常码、协议字段

## 1. 设计目标

### 1.1 本次要解决的问题

- 提供完整的数据字典主表与字典项维护能力
- 提供统一的后端查询/校验工具，让其他模块不再自己维护枚举列表
- 支持通过 `dictCode` 或“应用 + 模块 + 字段”两种方式读取字典
- 保存字典被哪些业务字段引用，方便后续做治理、排查和影响分析
- 为后续“系统日志”“字典使用分析”“低代码字段配置”预留扩展位

### 1.2 推荐落地方式

采用三层模型：

- `数据字典主表`：定义字典编码、名称、状态、引用应用摘要等
- `数据字典项表`：定义每个字典下的选项值、显示名、排序、默认项
- `数据字典使用表`：定义某个业务字段绑定了哪个字典，并记录最近使用情况

这样做的原因：

- 前端当前的数据字典页面只需要主表和字典项 CRUD
- 但后端要替代枚举，仅靠主表和字典项还不够，必须知道“谁在用”
- 使用表建好后，后续可以做到：
  - 某字段从一个字典切换到另一个字典
  - 统计哪些字典已经无人使用
  - 删除字典前阻断仍被引用的配置

## 2. 前端真实联调契约

## 2.1 统一返回结构

建议统一返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {}
}
```

说明：

- 列表接口前端优先读取 `data.list` + `data.total`
- 明细列表如果方便，也可直接返回数组，前端已兼容
- 失败时请返回清晰 `message`，前端会直接展示

## 2.2 字典主列表

`GET /system/dictionaries`

查询参数：

- `pageNo`：默认 1
- `pageSize`：默认 10
- `keyword`：匹配 `dictCode / dictName / referenceApps`
- `status`：`ENABLED` / `DISABLED`
- `referenceApp`：按引用应用过滤

推荐返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "dictCode": "WORK_LOG_TYPE",
        "dictName": "工作日志类型",
        "status": "ENABLED",
        "creatorName": "系统管理员",
        "referenceApps": ["工作日志"],
        "itemCount": 4,
        "createTime": "2026-03-20 10:20:30",
        "description": "工作类型选项"
      }
    ],
    "total": 1
  }
}
```

说明：

- 前端显示依赖字段：`id / dictCode / dictName / status / creatorName / referenceApps / itemCount / createTime / description`
- `referenceApps` 建议直接返回数组，前端展示更稳定
- `itemCount` 可以实时 count，也可以冗余维护

## 2.3 新增数据字典

`POST /system/dictionaries`

请求体：

```json
{
  "dictCode": "WORK_LOG_TYPE",
  "dictName": "工作日志类型",
  "status": "ENABLED",
  "enabled": true,
  "referenceApps": ["工作日志"],
  "description": "工作类型选项"
}
```

业务要求：

- `dictCode` 必填，推荐大写下划线风格，如 `WORK_LOG_TYPE`
- `dictCode` 全局唯一
- `dictName` 必填
- `status` 与 `enabled` 二者至少兼容一个

## 2.4 编辑数据字典

`PUT /system/dictionaries/{id}`

请求体与新增基本一致。

建议规则：

- `dictCode` 创建后不允许修改，避免影响其他模块绑定关系
- 允许修改 `dictName / status / referenceApps / description`

## 2.5 切换字典状态

`PATCH /system/dictionaries/{id}/status`

请求体：

```json
{
  "status": "DISABLED",
  "enabled": false
}
```

说明：

- 前端会同时传 `status` 和 `enabled`
- 后端只用其中一个持久化也可以，但要兼容入参与出参

## 2.6 删除数据字典

`DELETE /system/dictionaries/{id}`

建议规则：

- 若该字典仍存在有效使用记录，拒绝删除，并返回可读错误信息
- 若允许删除，应一并删除其下字典项

推荐错误信息：

```json
{
  "code": "DICT_IN_USE",
  "message": "当前字典仍被业务字段引用，无法删除",
  "data": null
}
```

## 2.7 查询字典项

`GET /system/dictionaries/{dictionaryId}/items`

推荐返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "list": [
      {
        "id": 11,
        "itemCode": "develop",
        "itemLabel": "开发",
        "itemValue": "DEVELOP",
        "sort": 1,
        "status": "ENABLED",
        "isDefault": true,
        "description": "默认项"
      }
    ]
  }
}
```

说明：

- 前端也兼容直接返回数组
- 前端显示依赖字段：`id / itemCode / itemLabel / itemValue / sort / status / isDefault / description`

## 2.8 新增或编辑字典项

`POST /system/dictionaries/{dictionaryId}/items`  
`PUT /system/dictionaries/{dictionaryId}/items/{itemId}`

请求体：

```json
{
  "itemCode": "develop",
  "itemLabel": "开发",
  "itemValue": "DEVELOP",
  "sort": 1,
  "status": "ENABLED",
  "enabled": true,
  "isDefault": true,
  "description": "默认项"
}
```

业务要求：

- `itemCode` 在同一字典内唯一
- `itemValue` 建议在同一字典内唯一，避免歧义
- 同一个字典最多一个默认项
- 若某项被设为默认项，建议自动取消同字典其他项的默认标记

## 2.9 切换字典项状态

`PATCH /system/dictionaries/{dictionaryId}/items/{itemId}/status`

请求体：

```json
{
  "status": "DISABLED",
  "enabled": false
}
```

## 2.10 删除字典项

`DELETE /system/dictionaries/{dictionaryId}/items/{itemId}`

建议规则：

- 若当前项被业务高频使用，可以允许删除，但历史数据展示需要允许按“含禁用项/含已删除项”兜底处理
- 更保守的做法是：不做物理删除，使用逻辑删除

## 3. 推荐数据模型

## 3.1 数据字典主表

建议表名：`gak_data_dictionary`

字段建议：

- `id`：主键
- `dict_code`：字典编码，唯一
- `dict_name`：字典名称
- `status`：`ENABLED / DISABLED`
- `reference_apps`：引用应用摘要，建议 JSON 或逗号文本，兼容前端当前页面展示
- `description`：说明
- `creator_user_id`
- `creator_name`
- `created_at`
- `updated_at`
- `deleted`

说明：

- 当前前端已经有 `referenceApps` 字段，建议先保留
- 长期来看，`referenceApps` 可以由使用表聚合得到，但本期为了兼容前端页面，主表中仍建议保留

## 3.2 数据字典项表

建议表名：`gak_data_dictionary_item`

字段建议：

- `id`
- `dictionary_id`
- `dict_code`：冗余一份，方便按编码直查
- `item_code`
- `item_label`
- `item_value`
- `sort_no`
- `status`
- `is_default`
- `description`
- `extra_json`：预留扩展字段
- `created_at`
- `updated_at`
- `deleted`

约束建议：

- 唯一索引：`(dictionary_id, item_code)`
- 唯一索引：`(dictionary_id, item_value)`

## 3.3 数据字典使用表

建议表名：`gak_data_dictionary_usage`

这张表不是“运行日志明细”，而是“业务字段与字典的绑定关系 + 最近使用痕迹”。

字段建议：

- `id`
- `dict_code`
- `dictionary_id`
- `app_code`：例如 `APP_WORK_LOG`
- `app_name`：例如 `工作日志`
- `module_code`：例如 `WORK_LOG`
- `module_name`：例如 `工作日志模块`
- `biz_field_code`：例如 `workType`
- `biz_field_name`：例如 `工作类型`
- `usage_type`：`OPTION_SOURCE / VALUE_VALIDATION / LABEL_MAPPING / DEFAULT_VALUE`
- `value_mode`：`ITEM_CODE / ITEM_VALUE`
- `allow_multiple`：是否允许多选
- `required_flag`：是否必填
- `status`：`ENABLED / DISABLED`
- `usage_count`：累计命中次数，可做近似值
- `last_used_at`
- `remark`
- `created_at`
- `updated_at`

唯一约束建议：

- `(app_code, module_code, biz_field_code)`

说明：

- 这张表解决的是“哪个业务字段绑定了哪个字典”
- 以后如果某个字段想从 `WORK_LOG_TYPE` 切换到 `WORK_CATEGORY`，只需要改绑定关系，不必改业务枚举类
- `usage_count / last_used_at` 不要求每次绝对精确，可异步或低频更新

## 3.4 系统日志预留

建议预留一张 `gak_data_dictionary_change_log` 或直接接入统一系统日志模块，记录：

- 操作人
- 操作类型：新增字典、编辑字典、编辑字典项、切换状态、删除
- 变更前 JSON
- 变更后 JSON
- 操作时间

本次不强制做日志查询页面，但建议至少预留实体与记录逻辑。

## 4. 数据字典支撑层设计

这里是本次最关键的部分。不要只生成 Controller 和 CRUD Service，还要生成可被其他模块直接注入的支撑组件。

不建议做纯静态 `Util` 类。  
更推荐做 Spring Bean 形式的 `Facade / Support / Resolver`，这样才能接数据库、缓存、权限和审计。

## 4.1 推荐对外暴露的支撑组件

### 4.1.1 `DataDictionarySupport`

建议方法：

```java
public interface DataDictionarySupport {
    List<DictionaryOptionVO> listEnabledOptions(String dictCode);
    List<DictionaryOptionVO> listAllOptions(String dictCode, boolean includeDisabled);
    Optional<DictionaryOptionVO> getDefaultOption(String dictCode);
    Optional<DictionaryOptionVO> findByItemCode(String dictCode, String itemCode);
    Optional<DictionaryOptionVO> findByItemValue(String dictCode, String itemValue);
    String getLabelByCode(String dictCode, String itemCode);
    String getLabelByValue(String dictCode, String itemValue);
    boolean existsEnabledItemCode(String dictCode, String itemCode);
    boolean existsEnabledItemValue(String dictCode, String itemValue);
    void validateItemCode(String dictCode, String itemCode, boolean required);
    void validateItemValue(String dictCode, String itemValue, boolean required);
}
```

适用场景：

- 表单下拉选项输出
- 保存前校验字段是否合法
- 根据存量值反查显示名称
- 获取默认项

### 4.1.2 `DataDictionaryUsageSupport`

建议方法：

```java
public interface DataDictionaryUsageSupport {
    DictionaryUsageBindingVO getBinding(String appCode, String moduleCode, String bizFieldCode);
    List<DictionaryOptionVO> listEnabledOptionsByUsage(String appCode, String moduleCode, String bizFieldCode);
    void validateByUsage(String appCode, String moduleCode, String bizFieldCode, String value, boolean required);
    void validateMultiValueByUsage(String appCode, String moduleCode, String bizFieldCode, Collection<String> values, boolean required);
    String getLabelByUsage(String appCode, String moduleCode, String bizFieldCode, String value);
    void registerOrUpdateUsage(RegisterDictionaryUsageCommand command);
    void recordUsageHit(String appCode, String moduleCode, String bizFieldCode);
}
```

适用场景：

- 业务模块不想直接写死 `dictCode`
- 业务只知道“我是工作日志模块的 workType 字段”，具体使用哪个字典由数据库配置决定
- 后续可以做低代码字段绑定

### 4.1.3 `DataDictionaryCacheSupport`

建议职责：

- 按 `dictCode` 缓存字典项
- 按 `appCode + moduleCode + bizFieldCode` 缓存使用绑定
- 在字典或字典项变更后自动清缓存

推荐实现：

- 优先用 Spring Cache
- 单体部署可用 Caffeine
- 若系统已有 Redis，可直接接 Redis

## 4.2 使用情况保存建议

“保存使用情况”建议拆成两个层次：

### 4.2.1 静态绑定关系

存到 `gak_data_dictionary_usage`：

- 某个应用的某个字段绑定哪个字典
- 这个关系相对稳定，是治理基础数据

### 4.2.2 动态命中痕迹

同样可以落到 `gak_data_dictionary_usage` 的 `usage_count / last_used_at`，或者后续单独接系统日志。

推荐策略：

- 工具类被调用时，异步记录一次 `recordUsageHit`
- 不要求每次实时更新数据库
- 可以采用“定时批量 flush”或“限频更新”

这样能避免：

- 每次业务查询字典都写一次库，性能太差
- 又完全没有使用痕迹，后续无法做清理和分析

## 4.3 替代后端枚举的推荐策略

建议分两类迁移：

### 4.3.1 优先迁移为数据字典

- 工作日志类型
- 工作日志项目
- 待办清单类型
- 待办优先级
- 应用分类
- 应用密级
- 应用加密方式
- WoW 职业/专精等偏展示和配置的选项

### 4.3.2 暂不完全数据字典化

- 超级管理员保留角色
- 登录态相关协议字段
- 安全认证类型
- 系统内部错误码

原因：

- 这些值一旦被前端任意改动，可能影响鉴权和安全边界
- 可以让数据字典负责“显示名/说明”，但不要让安全关键语义完全脱离代码控制

## 5. 可直接投喂 AI 的全量生成 Prompt

```text
你是一名资深 Java 后端工程师，请基于 Spring Boot 3 + Spring Web + Spring Validation + Spring Security + Spring Data JPA（或 MyBatis）生成“数据字典模块”完整代码，要求可直接运行，并且能作为其他业务模块的公共支撑能力使用。

【目标】
实现一个“数据字典 + 字典项 + 字典使用绑定”的后端模块，不只是做 CRUD，还要生成可被其他模块直接调用的支撑类，逐步替代后端中大量展示型、校验型、可配置型枚举。

【前端已固定的 REST 接口】
1) GET /system/dictionaries
   - 查询参数：pageNo(默认1), pageSize(默认10), keyword(可选), status(可选), referenceApp(可选)
   - keyword 匹配 dictCode / dictName / referenceApps
   - 返回：
     {
       list: DataDictionaryVO[],
       total: number
     }

2) POST /system/dictionaries
   - 入参：
     dictCode, dictName, status, enabled, referenceApps[], description

3) PUT /system/dictionaries/{id}
   - 入参同新增
   - dictCode 创建后不可修改

4) DELETE /system/dictionaries/{id}
   - 如果字典仍被业务字段引用，则拒绝删除并返回可读错误
   - 若允许删除，需同步删除其下字典项

5) PATCH /system/dictionaries/{id}/status
   - 入参：
     {
       status: "ENABLED|DISABLED",
       enabled: true|false
     }

6) GET /system/dictionaries/{dictionaryId}/items
   - 返回：
     {
       list: DataDictionaryItemVO[]
     }
   - 也可以直接返回数组，前端兼容

7) POST /system/dictionaries/{dictionaryId}/items
   - 入参：
     itemCode, itemLabel, itemValue, sort, status, enabled, isDefault, description

8) PUT /system/dictionaries/{dictionaryId}/items/{itemId}
   - 入参同新增

9) DELETE /system/dictionaries/{dictionaryId}/items/{itemId}

10) PATCH /system/dictionaries/{dictionaryId}/items/{itemId}/status
   - 入参：
     {
       status: "ENABLED|DISABLED",
       enabled: true|false
     }

【推荐额外生成但本轮前端暂未直连的支撑接口】
11) GET /system/dictionaries/options/{dictCode}
   - 返回某个 dictCode 下启用字典项，供前端动态下拉或其他服务复用

12) GET /system/dictionaries/options/by-usage
   - 查询参数：appCode, moduleCode, bizFieldCode
   - 返回指定业务字段绑定的启用字典项

13) GET /system/dictionaries/usages
   - 分页查询字典使用绑定

14) POST /system/dictionaries/usages
   - 新增或更新业务字段与字典的绑定关系

【数据模型】
请生成以下 3 张主表：

1) gak_data_dictionary
- id (Long, PK)
- dictCode (varchar64, unique, not null)
- dictName (varchar64, not null)
- status (ENABLED / DISABLED)
- referenceApps (json 或 varchar，兼容前端展示)
- description (varchar255, nullable)
- creatorUserId (Long, nullable)
- creatorName (varchar64, nullable)
- createdAt
- updatedAt
- deleted

2) gak_data_dictionary_item
- id (Long, PK)
- dictionaryId (Long, FK -> gak_data_dictionary.id)
- dictCode (varchar64, 冗余字段，便于按编码查询)
- itemCode (varchar64, not null)
- itemLabel (varchar64, not null)
- itemValue (varchar64, not null)
- sortNo (int, default 0)
- status (ENABLED / DISABLED)
- isDefault (boolean)
- description (varchar255, nullable)
- extraJson (text/json, nullable)
- createdAt
- updatedAt
- deleted
- 唯一约束：(dictionaryId, itemCode)
- 唯一约束：(dictionaryId, itemValue)

3) gak_data_dictionary_usage
- id (Long, PK)
- dictCode (varchar64, not null)
- dictionaryId (Long, nullable)
- appCode (varchar64, not null)
- appName (varchar64, nullable)
- moduleCode (varchar64, not null)
- moduleName (varchar64, nullable)
- bizFieldCode (varchar64, not null)
- bizFieldName (varchar64, nullable)
- usageType (OPTION_SOURCE / VALUE_VALIDATION / LABEL_MAPPING / DEFAULT_VALUE)
- valueMode (ITEM_CODE / ITEM_VALUE)
- allowMultiple (boolean)
- requiredFlag (boolean)
- status (ENABLED / DISABLED)
- usageCount (bigint, default 0)
- lastUsedAt (datetime, nullable)
- remark (varchar255, nullable)
- createdAt
- updatedAt
- 唯一约束：(appCode, moduleCode, bizFieldCode)

【核心业务规则】
1) dictCode 全局唯一，推荐大写下划线风格，例如 WORK_LOG_TYPE
2) dictCode 创建后不可修改
3) itemCode 在同一字典内唯一
4) itemValue 在同一字典内唯一
5) 同一个字典最多一个默认项；新默认项生效时，要自动取消其他默认项
6) 启用态字典才允许被外部模块用于校验与下拉选项输出
7) 启用态字典项才允许参与校验
8) 查询 label 时应支持“含禁用项兜底”，避免历史数据无法显示
9) 删除字典前必须检查是否仍存在启用中的 usage 绑定
10) 删除字典项时建议逻辑删除，避免历史业务数据彻底失联
11) referenceApps 继续保留，用于兼容当前前端页面；同时建议由 usage 表聚合出 appName 作为补充

【必须生成的支撑组件】
不要只生成 Controller 和 CRUD Service，还要生成如下公共支撑 Bean，供其他模块直接注入：

1) DataDictionarySupport
至少提供：
- listEnabledOptions(String dictCode)
- listAllOptions(String dictCode, boolean includeDisabled)
- getDefaultOption(String dictCode)
- findByItemCode(String dictCode, String itemCode)
- findByItemValue(String dictCode, String itemValue)
- getLabelByCode(String dictCode, String itemCode)
- getLabelByValue(String dictCode, String itemValue)
- existsEnabledItemCode(String dictCode, String itemCode)
- existsEnabledItemValue(String dictCode, String itemValue)
- validateItemCode(String dictCode, String itemCode, boolean required)
- validateItemValue(String dictCode, String itemValue, boolean required)

2) DataDictionaryUsageSupport
至少提供：
- getBinding(String appCode, String moduleCode, String bizFieldCode)
- listEnabledOptionsByUsage(String appCode, String moduleCode, String bizFieldCode)
- validateByUsage(String appCode, String moduleCode, String bizFieldCode, String value, boolean required)
- validateMultiValueByUsage(String appCode, String moduleCode, String bizFieldCode, Collection<String> values, boolean required)
- getLabelByUsage(String appCode, String moduleCode, String bizFieldCode, String value)
- registerOrUpdateUsage(RegisterDictionaryUsageCommand command)
- recordUsageHit(String appCode, String moduleCode, String bizFieldCode)

3) DataDictionaryCacheSupport
- 按 dictCode 缓存字典项
- 按 appCode + moduleCode + bizFieldCode 缓存 usage 绑定
- 在字典、字典项、usage 发生变化时清理缓存

【使用情况记录要求】
“数据字典使用情况”不要只做备注文本，要做结构化保存：
- 哪个应用(appCode)
- 哪个模块(moduleCode)
- 哪个字段(bizFieldCode)
- 绑定哪个字典(dictCode)
- 是按 itemCode 校验还是按 itemValue 校验(valueMode)
- 是否允许多选
- 是否必填
- 最近一次使用时间 lastUsedAt
- 近似累计命中次数 usageCount

说明：
- usageCount / lastUsedAt 可以异步更新，不要求每次调用实时写库
- 可以提供一个简单的异步实现，或者预留定时批量 flush

【替代枚举的使用方式】
请在代码中给出示例，演示其他模块如何注入并使用：

示例1：工作日志模块
- workType 字段不再写死枚举
- 保存时调用 DataDictionaryUsageSupport.validateByUsage("APP_WORK_LOG", "WORK_LOG", "workType", value, true)

示例2：用户管理模块
- 查询角色下拉时调用 DataDictionarySupport.listEnabledOptions("USER_ROLE_TYPE")

示例3：应用管理模块
- securityLevel / encryptionMode / category 可逐步改成读取字典

【边界要求】
以下内容不要完全数据字典化：
- 超级管理员保留语义
- 鉴权核心常量
- token 协议字段
- 系统错误码
这些可保留代码常量，只允许数据字典管理显示名称或说明。

【推荐返回 VO】
DataDictionaryVO:
- id
- dictCode
- dictName
- status
- creatorName
- referenceApps
- itemCount
- createTime
- description

DataDictionaryItemVO:
- id
- itemCode
- itemLabel
- itemValue
- sort
- status
- isDefault
- description

DictionaryOptionVO:
- itemCode
- itemLabel
- itemValue
- isDefault
- sort

DictionaryUsageBindingVO:
- id
- dictCode
- appCode
- appName
- moduleCode
- moduleName
- bizFieldCode
- bizFieldName
- usageType
- valueMode
- allowMultiple
- requiredFlag
- status
- usageCount
- lastUsedAt

【返回格式】
统一返回：
{
  "code": "0",
  "message": "success",
  "data": ...
}
失败时 code 非 0，message 为可读错误信息。

【代码输出要求】
- 按 Controller / Service / Repository(Mapper) / Entity / DTO / VO / Support / Cache / Exception / Config 分层输出
- 提供 MySQL 8 建表 SQL
- 提供至少 6 组初始化字典与字典项数据，例如：
  - USER_ROLE_TYPE
  - USER_STATUS
  - WORK_LOG_TYPE
  - WORK_LOG_PROJECT
  - APP_SECURITY_LEVEL
  - APP_ENCRYPTION_MODE
- 提供至少 5 条 usage 绑定初始化数据
- 提供 curl 示例
- 提供至少 2 个其他模块调用支撑类的示例代码
- 提供缓存失效处理
- 提供关键注释，不要只给伪代码
```

## 6. 单接口/单能力增量 Prompt

### 6.1 只补“数据字典支撑工具类”

```text
请为现有 Spring Boot 项目新增“数据字典支撑层”，不要改前端接口路径。

要求新增以下 Spring Bean：
1) DataDictionarySupport
2) DataDictionaryUsageSupport
3) DataDictionaryCacheSupport

要求：
1) DataDictionarySupport 支持按 dictCode 查询启用字典项、取默认项、校验 itemCode/itemValue、反查 label
2) DataDictionaryUsageSupport 支持按 appCode + moduleCode + bizFieldCode 获取绑定字典、校验值、查询选项
3) 需要有缓存，字典主表、字典项、usage 变更后自动失效
4) 提供 2 个业务模块调用示例
5) 返回统一结构或明确方法签名，不要只给伪代码
```

### 6.2 只补“数据字典使用情况表 + 注册能力”

```text
请为现有 Spring Boot 项目新增数据字典使用绑定能力。

要求：
1) 新增表 gak_data_dictionary_usage
2) 字段至少包含：
   dictCode, appCode, appName, moduleCode, moduleName, bizFieldCode, bizFieldName,
   usageType, valueMode, allowMultiple, requiredFlag, status, usageCount, lastUsedAt, remark
3) 唯一约束为 (appCode, moduleCode, bizFieldCode)
4) 生成 Service + Repository/Mapper + DTO + VO
5) 提供 registerOrUpdateUsage 方法
6) 提供 recordUsageHit 方法，允许异步更新 usageCount / lastUsedAt
7) 提供查询接口 GET /system/dictionaries/usages
8) 返回统一结构：{ code, message, data }
```

### 6.3 只补“按 dictCode 输出前端下拉选项”

```text
请为现有 Spring Boot 项目新增 GET /system/dictionaries/options/{dictCode} 接口。

要求：
1) 只返回启用状态的字典项
2) 返回字段至少包含：
   itemCode, itemLabel, itemValue, isDefault, sort
3) 若 dictCode 不存在或字典被禁用，返回清晰错误信息
4) 统一返回结构：{ code, message, data }
5) 同时补一个 DataDictionarySupport.listEnabledOptions(String dictCode) 公共方法
```

## 7. 联调约定

- 前端接口文件固定为 `src/api/dataDictionary.js`
- 前端页面固定路由为 `/data-dictionary`
- 前端当前页面已经固定为“上方字典主表 + 下方字典项维护”结构
- 前端列表请求默认走：
  - `GET /system/dictionaries?pageNo=1&pageSize=10`
- 前端字典项请求默认走：
  - `GET /system/dictionaries/{dictionaryId}/items`
- 前端创建和编辑时会同时传 `status` 与 `enabled`
- 前端当前允许直接删除字典和字典项；如果后端需要更保守的删除策略，请返回清晰错误信息，不要返回空泛 `500`
- 如果后端最终返回结构不是 `data.list/data.total`，需要同步调整前端解析逻辑

## 8. 推荐实现边界

为了让这套方案可持续，建议后端按下面边界实现：

- 数据字典负责“可配置选项”的维护、校验、显示名映射
- 核心安全语义仍保留代码常量，不建议完全交给前端配置
- 其他业务模块今后新增可配置字段时，优先新增字典与 usage 绑定，不要再新增 Java 枚举
- 老枚举类可以逐步废弃，但不要一次性把所有安全常量都改造成动态配置

这套方式能达到的效果是：

- 新增或修改字典项，不需要改后端代码
- 调整下拉顺序、默认值、禁用项，不需要改后端代码
- 某业务字段切换到另一套字典，可以通过 usage 绑定完成
- 后续做系统日志、字典影响分析、无用字典清理时有据可查
