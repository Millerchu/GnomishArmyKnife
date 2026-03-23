# 工作日志后端增量调整 Prompt

## 1. 背景

前端 `工作日志` 已完成以下调整，文件位置：
- `src/views/WorkLog.vue`

当前前端行为已经变为：
- 日志类型改成多选下拉，优先读取数据字典 usage：
  - `APP_WORK_LOG / WORK_LOG / typeCodes`
- 地点改成下拉，优先读取数据字典 usage：
  - `APP_WORK_LOG / WORK_LOG / location`
- 所属项目改成下拉，优先读取数据字典 usage：
  - `APP_WORK_LOG / WORK_LOG / projectCode`
- 工作日不再手填加班时长，改为输入 `实际下班时间`
  - 前端按 `18:00` 为正常下班时间自动计算 `overtimeHours`
- 周末仍允许手动填写 `overtimeHours`
- 周视图上方新增了“本周统计”区域
  - 当前前端先用 `GET /work-logs` 本地汇总
  - 后端可继续保留 `GET /work-logs/weekly-brief`，也可以补充更完整统计

注意：
- 为兼容当前后端，前端目前提交时仍只强依赖旧字段 `overtimeHours`
- 但后端这次需要正式新增并持久化 `offWorkTime`

---

## 2. 当前真实后端现状

请直接基于当前模块修改，不要重建一套新模块。

模块位置：
- `gak-parent/gak-modules/gak-worklog`

当前类：
- `controller/WorkLogController.java`
- `service/WorkLogService.java`
- `dto/CreateWorkLogRequest.java`
- `dto/UpdateWorkLogRequest.java`
- `dto/WorkLogResponse.java`
- `dto/WeeklyWorkLogBriefResponse.java`
- `entity/WorkLog.java`

当前已存在的能力：
- `typeCodes` 已接数据字典支撑层：
  - `DataDictionaryUsageSupport.normalizeMultiValueByUsage(...)`
- `location`、`projectCode` 仍是普通字符串，尚未走数据字典校验
- `overtimeHours` 仍是前端直接传入，后端未根据 `offWorkTime` 自动计算
- `weekly-brief` 只返回：
  - `id`
  - `logDate`
  - `typeCodes`
  - `projectCode`
  - `brief`
  - `personDay`
  - `overtimeHours`

---

## 3. 本次必须修改

### 3.1 数据字典接入

请把以下字段都接入数据字典 usage 支撑：

1. `typeCodes`
- 继续保持多选
- 继续走：
  - `APP_WORK_LOG / WORK_LOG / typeCodes`

2. `projectCode`
- 改为正式字典校验
- 使用 usage：
  - `APP_WORK_LOG / WORK_LOG / projectCode`
- 当前数据库已有字典：
  - `WORK_LOG_PROJECT`

3. `location`
- 新增正式字典校验
- 新增 usage：
  - `APP_WORK_LOG / WORK_LOG / location`
- 新增字典：
  - `WORK_LOG_LOCATION`

要求：
- `typeCodes`：多值校验与去重
- `projectCode`：单值校验
- `location`：单值校验
- 非法值统一返回 `400`

---

### 3.2 加班逻辑改造

请把工作日志的加班逻辑改成下面这套规则：

1. 工作日（周一到周五）
- 请求新增字段：
  - `offWorkTime`
- 类型建议：
  - `LocalTime`
- 正常下班时间固定为：
  - `18:00`
- 后端根据 `offWorkTime` 自动计算 `overtimeHours`
- 如果 `offWorkTime <= 18:00`，则 `overtimeHours = 0`
- 前端传来的 `overtimeHours` 不应作为工作日最终可信值

2. 周末（周六、周日）
- 允许前端直接传 `overtimeHours`
- `offWorkTime` 可为空
- 周末不要求按 `18:00` 规则计算

3. 返回结构
- `WorkLogResponse` 需要补充：
  - `offWorkTime`
- `WeeklyWorkLogBriefResponse` 也建议补充：
  - `offWorkTime`

4. 数据库存储
- `gak_work_log` 新增字段：
  - `off_work_time time null`

建议 SQL：
```sql
alter table gak_work_log
  add column if not exists off_work_time time null;
```

---

### 3.3 weekly-brief 优化

当前前端“本周统计”已经能本地汇总，但建议后端把 `weekly-brief` 补强，方便后续复用。

建议两种方案二选一：

方案 A：保持现有接口，补字段
- `GET /work-logs/weekly-brief`
- 每条记录补充：
  - `location`
  - `offWorkTime`
  - `remark`

方案 B：新增周统计接口
- `GET /work-logs/weekly-summary`
- 返回：
  - `projectCodes`
  - `typeCodes`
  - `personDayTotal`
  - `overtimeHoursTotal`
  - `workDayCount`
  - `weekendLogCount`
  - `logCount`

如果只做一版，优先方案 A，改动最小。

---

### 3.4 前端当前字段契约

前端当前提交字段：

```json
{
  "userId": 1,
  "logDate": "2026-03-23",
  "typeCodes": ["NORMAL", "BUSINESS_TRIP"],
  "location": "上海办公室",
  "projectCode": "GAK",
  "workItem": "完成权限管理联调并修复用户管理问题",
  "zentaoNo": "12345,12346",
  "personDay": 1,
  "overtimeHours": 2.5,
  "remark": "需要明天继续验证"
}
```

后端改完后，建议前端升级到正式提交：

```json
{
  "userId": 1,
  "logDate": "2026-03-23",
  "typeCodes": ["NORMAL"],
  "location": "上海办公室",
  "projectCode": "GAK",
  "workItem": "完成工作日志改版",
  "zentaoNo": "12345",
  "personDay": 1,
  "offWorkTime": "20:30:00",
  "remark": "工作日按下班时间自动算加班"
}
```

周末示例：
```json
{
  "userId": 1,
  "logDate": "2026-03-29",
  "typeCodes": ["NORMAL"],
  "location": "居家",
  "projectCode": "GAK",
  "workItem": "周末补充测试",
  "personDay": 0.5,
  "overtimeHours": 4,
  "remark": "周末允许手填加班时长"
}
```

---

## 4. 数据字典新增要求

请新增以下字典和 usage：

### 4.1 字典

1. `WORK_LOG_LOCATION`
- 字典名称：`工作地点`
- 参考应用：`工作日志`
- 推荐初始项示例：
  - `SH_OFFICE / 上海办公室 / 上海办公室`
  - `SZ_OFFICE / 深圳办公室 / 深圳办公室`
  - `HOME / 居家 / 居家`
  - `CLIENT_SITE / 客户现场 / 客户现场`
  - `TRAVEL / 出差在途 / 出差在途`

### 4.2 usage 绑定

- `APP_WORK_LOG / WORK_LOG / location -> WORK_LOG_LOCATION`
- `APP_WORK_LOG / WORK_LOG / projectCode -> WORK_LOG_PROJECT`
- `APP_WORK_LOG / WORK_LOG / typeCodes -> WORK_LOG_TYPE`

其中：
- `typeCodes.allowMultiple = true`
- `location.allowMultiple = false`
- `projectCode.allowMultiple = false`

---

## 5. 实现要求

请按下面顺序改：

1. 修改 `CreateWorkLogRequest`
- 新增 `offWorkTime`

2. 修改 `UpdateWorkLogRequest`
- 新增 `offWorkTime`

3. 修改 `WorkLogResponse`
- 新增 `offWorkTime`

4. 修改 `WorkLog` 实体
- 新增 `offWorkTime`

5. 修改 `WorkLogService`
- `projectCode` 接字典 usage 校验
- `location` 接字典 usage 校验
- 工作日按 `offWorkTime` 自动算 `overtimeHours`
- 周末允许手填 `overtimeHours`

6. 修改数据库
- `gak_work_log` 增加 `off_work_time`
- 新增字典 `WORK_LOG_LOCATION`
- 新增 usage `location`

7. 可选增强
- 优化 `weekly-brief`

---

## 6. 可直接执行的增量 Prompt

请基于当前已有 `gak-worklog` 模块直接增量修改，不要新建重复模块。

目标：

1. 工作日志前端已改版，后端需要同步支持：
- `typeCodes` 多选字典
- `projectCode` 单值字典
- `location` 单值字典
- 工作日输入 `offWorkTime` 后自动计算 `overtimeHours`
- 周末保留手填 `overtimeHours`

2. 请修改这些类：
- `CreateWorkLogRequest`
- `UpdateWorkLogRequest`
- `WorkLogResponse`
- `WorkLog`
- `WorkLogService`
- 如有需要，补 `WeeklyWorkLogBriefResponse`

3. 数据字典要求：
- 复用 `WORK_LOG_TYPE`
- 复用 `WORK_LOG_PROJECT`
- 新增 `WORK_LOG_LOCATION`
- 新增 usage：
  - `APP_WORK_LOG / WORK_LOG / location`

4. 计算规则：
- 工作日：
  - `overtimeHours = max(offWorkTime - 18:00, 0)`
- 周末：
  - 允许直接使用前端传入的 `overtimeHours`

5. 数据库：
```sql
alter table gak_work_log
  add column if not exists off_work_time time null;
```

6. 返回结果中补充：
- `offWorkTime`

7. 保持现有接口路径不变：
- `POST /work-logs`
- `PUT /work-logs/{id}`
- `GET /work-logs/{id}`
- `GET /work-logs`
- `GET /work-logs/weekly-brief`

输出要求：
- 直接给出修改后的 Java 代码
- 给出 SQL
- 说明哪些地方接入了字典 usage
- 不要省略 DTO / entity / service 的具体改动
