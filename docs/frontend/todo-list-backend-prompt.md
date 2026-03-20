# 待办列表后端接口代码生成提示词

本文档用于把前端已预留的“待办列表”接口一次性生成为后端代码。  
前端项目已固定以下接口路径（基于 `/api` 代理后的实际后端路径）：

- `GET /todo-items`：分页查询待办任务列表
- `POST /todo-items`：新增待办任务
- `PUT /todo-items/{id}`：编辑待办任务
- `DELETE /todo-items/{id}`：删除待办任务
- `PATCH /todo-items/{id}/status`：修改任务状态
- `PATCH /todo-items/{id}/important`：修改任务重要标记
- `DELETE /todo-items/completed`：清理全部已完成任务

## 1. 推荐统一返回结构

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "list": [],
    "total": 0
  }
}
```

说明：
- 列表接口返回 `data.list` + `data.total`。
- 列表接口如果方便，建议同时返回 `data.summary`、`data.upcoming`、`data.listStats`，前端可直接复用。
- 非列表接口可返回 `data` 为对象或 `null`。
- 失败时返回清晰 `message`，前端会直接展示。

## 2. 可直接投喂 AI 的全量生成 Prompt

```text
你是一名资深 Java 后端工程师，请基于 Spring Boot 3 + Spring Web + Spring Validation + Spring Security + Spring Data JPA（或 MyBatis）生成“待办列表模块”完整代码，要求可直接运行。

【目标】
实现以下 REST 接口（基础路径 /todo-items）：
1) GET /todo-items
   - 查询参数：pageNo(默认1), pageSize(默认10), keyword(可选), listCode(可选), status(可选), importance(可选), viewCode(可选)
   - viewCode 可选值：ALL / TODAY / IMPORTANT / COMPLETED
   - 关键字匹配：title、note、stepTitle
   - 返回：
     {
       list: TodoItemListVO[],
       total: number,
       summary: {
         total: number,
         today: number,
         important: number,
         completed: number
       },
       upcoming: TodoItemSimpleVO[],
       listStats: ListStatVO[]
     }
2) POST /todo-items
   - 入参：
     title, listCode, importance, status, important, dueDate, reminderAt, note, steps[]
3) PUT /todo-items/{id}
   - 入参同新增
4) DELETE /todo-items/{id}
5) PATCH /todo-items/{id}/status
   - 入参：status(TODO / IN_PROGRESS / COMPLETED), completed(boolean，可选)
6) PATCH /todo-items/{id}/important
   - 入参：important(boolean)
7) DELETE /todo-items/completed
   - 删除当前登录用户的全部已完成任务

【数据模型】
请生成 todo_item 表，字段至少包含：
- id (Long, PK)
- ownerUserId (Long, 当前登录用户ID)
- title
- listCode (MY_DAY / WORK / PERSONAL / LEARNING / SHOPPING)
- importance (LOW / MEDIUM / HIGH)
- status (TODO / IN_PROGRESS / COMPLETED)
- important (boolean)
- dueDate (date, nullable)
- reminderAt (datetime, nullable)
- note (varchar/text, nullable)
- createdAt
- updatedAt

同时生成 todo_item_step 表，字段至少包含：
- id (Long, PK)
- taskId (Long, FK -> todo_item.id)
- title
- done (boolean)
- sortNo (int)
- createdAt
- updatedAt

【业务规则】
- title 必填，长度不超过 100
- listCode 必填，只允许 MY_DAY / WORK / PERSONAL / LEARNING / SHOPPING
- importance 必填，只允许 LOW / MEDIUM / HIGH
- status 必填，只允许 TODO / IN_PROGRESS / COMPLETED
- 当前登录用户只能访问自己的任务
- steps 为可选数组，每个 step.title 必填，长度不超过 80
- 列表按以下优先级排序：
  1. 未完成任务优先于已完成任务
  2. important=true 优先
  3. dueDate 升序（为空排后）
  4. updatedAt 降序
- viewCode = TODAY 时，返回：
  - dueDate = 今天 的未完成任务
  - 或 listCode = MY_DAY 的未完成任务
- viewCode = IMPORTANT 时，只返回 important=true 且未完成任务
- viewCode = COMPLETED 时，只返回 status=COMPLETED 的任务
- 清理已完成任务接口只删除当前用户 status=COMPLETED 的任务

【推荐返回 VO】
TodoItemListVO:
- id
- title
- listCode
- importance
- status
- important
- dueDate
- reminderAt
- note
- steps[]
- createdAt
- updatedAt

TodoItemStepVO:
- id
- title
- done

TodoItemSimpleVO:
- id
- title
- dueDate
- listCode

ListStatVO:
- listCode
- count

【返回格式】
统一返回：
{
  "code": "0",
  "message": "success",
  "data": ...
}
失败时 code 非 0，message 为可读错误信息。

【代码输出要求】
- 按 Controller / Service / Repository(Mapper) / Entity / DTO / VO / Exception / Config 分层输出
- 提供 MySQL 8 建表 SQL
- 提供至少 8 条初始化测试数据，覆盖不同清单、优先级、状态、是否重要、是否有子任务
- 提供 curl 示例
- 代码中补充关键注释，不要只给伪代码
```

## 3. 单接口增量 Prompt

### 3.1 状态切换接口

```text
请为现有 Spring Boot 项目新增 PATCH /todo-items/{id}/status 接口。
入参 JSON:
{
  "status": "TODO|IN_PROGRESS|COMPLETED",
  "completed": true
}

要求：
1) 从登录态中获取当前用户ID
2) 校验任务归属 ownerUserId = 当前用户ID
3) status 只允许 TODO / IN_PROGRESS / COMPLETED
4) 更新任务状态与 updatedAt
5) 返回统一结构：{ code, message, data }
6) 提供 Controller + Service + DTO + 单元测试
```

### 3.2 清理已完成任务接口

```text
请为现有 Spring Boot 项目新增 DELETE /todo-items/completed 接口。
要求：
1) 只删除当前登录用户 status=COMPLETED 的任务
2) 同时删除关联的 todo_item_step
3) 返回统一结构：{ code, message, data }
4) 提供 Controller + Service + Repository/Mapper + 单元测试
```

## 4. 联调约定

- 前端接口文件固定为 `src/api/todoList.js`
- 前端页面固定路由为 `/todo-list`
- 前端在后端不可用时会自动回退本地演示数据
- 前端列表支持 `viewCode / listCode / status / importance / keyword` 组合筛选
- 前端弹窗会直接提交 `steps[]`，建议后端在新增/编辑接口里一并处理子任务
- 如果后端返回结构不是 `data.list/data.total`，请同步调整前端解析逻辑
