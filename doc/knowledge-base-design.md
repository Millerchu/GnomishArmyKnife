# 经验库后端设计方案

本文档对应前端页面 `src/views/KnowledgeBase.vue` 的正式后端接入方案。  
本次目标是按照当前前端既有结构，把经验库从本地演示逻辑切到真实接口，不额外改变交互模型。

## 1. 设计范围

经验库聚焦“可复用的经验沉淀”，本次按当前前端口径提供：

- 经验列表
- 经验详情
- 新增经验
- 编辑经验
- 删除经验
- 随机推荐 Banner

不做额外复杂能力：

- 不做 Markdown 渲染
- 不做权限共享
- 不做附件上传
- 不做多级分类

## 2. 已固定的前端接口

- `GET /knowledge-base/entries`
- `GET /knowledge-base/entries/{id}`
- `POST /knowledge-base/entries`
- `PUT /knowledge-base/entries/{id}`
- `DELETE /knowledge-base/entries/{id}`
- `GET /knowledge-base/highlights`

统一返回结构沿用 `ApiResponse`。

列表接口返回：

```json
{
  "list": [],
  "total": 0
}
```

## 3. 表结构设计

### 3.1 经验条目表 `gak_knowledge_entry`

- `id`
- `owner_user_id`
- `title`
- `category_name`
- `scenario`
- `source_name`
- `tags_text`
- `summary`
- `content`
- `created_at`
- `updated_at`

索引：

- `idx_knowledge_entry_owner_updated_at`
- `idx_knowledge_entry_owner_category`

说明：

- `tags_text` 使用逗号拼接后的扁平字符串存储
- 后端出参时再拆成数组，和当前前端结构对齐
- 列表排序固定为 `updated_at DESC, id DESC`

## 4. 接口口径

### 4.1 `GET /knowledge-base/entries`

查询参数：

- `pageNo`
- `pageSize`

返回字段：

- `list`
- `total`

每条记录字段：

- `id`
- `title`
- `category`
- `scenario`
- `source`
- `tags`
- `summary`
- `content`
- `createdAt`
- `updatedAt`

### 4.2 `GET /knowledge-base/entries/{id}`

返回单条完整经验详情。

### 4.3 `GET /knowledge-base/highlights`

查询参数：

- `size`

默认行为：

- 默认返回 3 条
- 从当前用户全部经验中随机抽取
- 若总数不足，则全量返回

### 4.4 写接口规则

- `title` 必填，长度不超过 64
- `category` 必填，长度不超过 32
- `scenario` 必填，长度不超过 80
- `source` 允许为空，长度不超过 80
- `summary` 必填，长度不超过 180
- `content` 必填，长度不超过 2000
- `tags` 允许为空；保存前做去空、去重、trim

## 5. 后端分层建议

- `Controller`
  - 只做参数接收、登录用户透传、响应包装

- `Service`
  - 负责列表分页、详情归属校验、标签标准化和随机推荐

- `Mapper`
  - 使用 MyBatis Plus `BaseMapper`

- `DTO`
  - 查询参数、保存请求拆分

- `VO`
  - 列表项、详情项可复用同一 VO

## 6. 演示数据策略

只给 `admin` 用户初始化演示数据：

- 经验记录 8 条左右
- 覆盖工作、生活、学习、工具、健康、财务等分类
- 数据直接支撑首页首次进入时的“随机推荐”和列表浏览

非 `admin` 用户默认不插演示数据。

## 7. 前端联调要求

前端需要同步完成：

1. 删除 localStorage 演示兜底
2. 保持当前页面结构不变
3. Banner 改为真实随机推荐接口
4. `APP_KNOWLEDGE_BASE` 的 `data_source_mode` 更新为 `REAL`

## 8. 验证要求

- 后端至少执行经验库模块单测和最小聚合编译
- 前端执行 `npm run build`
- 如接口字段后续调整，必须同步更新：
  - `src/api/knowledgeBase.js`
  - `src/views/KnowledgeBase.vue`
  - 本文档
