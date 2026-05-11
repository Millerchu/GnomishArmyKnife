# 个人账单后端设计方案

本文档对应前端页面 `src/views/PersonalBills.vue` 的正式后端接入方案。  
本次目标是把“个人账单”从前端本地演示逻辑切到真实接口，并把预算分类、账单分类、支付方式统一纳入系统数据字典维护。

## 1. 已固定的前端接口

- `GET /personal-bills`：分页查询账单列表
- `POST /personal-bills`：新增账单
- `PUT /personal-bills/{id}`：编辑账单
- `DELETE /personal-bills/{id}`：删除账单
- `GET /personal-bills/summary`：查询收支概览、分类分布、预算执行进度
- `GET /personal-bills/budgets`：查询年度预算列表
- `POST /personal-bills/budgets`：新增年度预算
- `PUT /personal-bills/budgets/{id}`：编辑年度预算
- `DELETE /personal-bills/budgets/{id}`：删除年度预算

统一返回结构沿用项目现有 `ApiResponse`：

```json
{
  "code": "0",
  "message": "success",
  "data": {}
}
```

其中账单列表接口返回：

```json
{
  "list": [],
  "total": 0
}
```

## 2. 数据字典设计

个人账单本次接入 3 组字典，全部由系统“数据字典”模块维护：

1. `PERSONAL_BILLS_BILL_CATEGORY`
   - 作用：账单分类
   - 用途：收支记录表单、账单筛选
   - 示例项：`餐饮 / 交通 / 居家 / 娱乐 / 数码 / 学习 / 旅行 / 工资 / 奖金`

2. `PERSONAL_BILLS_BUDGET_CATEGORY`
   - 作用：年度预算分类
   - 用途：预算表单
   - 示例项：`餐饮 / 交通 / 居家 / 娱乐 / 数码 / 学习 / 旅行`

3. `PERSONAL_BILLS_PAYMENT_METHOD`
   - 作用：支付方式
   - 用途：账单表单
   - 示例项：`支付宝 / 微信支付 / 银行卡 / 银行转账 / 现金`

### usage 绑定

- `APP_PERSONAL_BILLS / PERSONAL_BILLS / categoryName -> PERSONAL_BILLS_BILL_CATEGORY`
- `APP_PERSONAL_BILLS / PERSONAL_BILLS / budgetCategoryName -> PERSONAL_BILLS_BUDGET_CATEGORY`
- `APP_PERSONAL_BILLS / PERSONAL_BILLS / paymentMethod -> PERSONAL_BILLS_PAYMENT_METHOD`

后端保存前需要基于 `DataDictionaryUsageSupport` 做归一化和合法性校验。

## 3. 表结构设计

### 3.1 账单表 `gak_personal_bill`

- `id`
- `owner_user_id`
- `bill_type`
- `category_name`
- `amount`
- `account_name`
- `payment_method`
- `merchant_name`
- `bill_date`
- `note`
- `created_at`
- `updated_at`

索引：

- `idx_personal_bill_owner_date`
- `idx_personal_bill_owner_type_date`

### 3.2 预算表 `gak_personal_budget`

- `id`
- `owner_user_id`
- `budget_year`
- `category_name`
- `annual_limit`
- `alert_threshold`
- `note`
- `created_at`
- `updated_at`

索引和约束：

- `idx_personal_budget_owner_year`
- `uk_personal_budget_owner_year_category`

说明：
- 同一用户、同一年、同一预算分类只能存在一条记录。
- `alert_threshold` 存 0~1 之间的小数，前端按百分比展示。

## 4. 接口口径

### 4.1 `GET /personal-bills`

查询参数：

- `pageNo`
- `pageSize`
- `month`
- `billType`
- `categoryName`
- `keyword`

过滤规则：

- `month` 按 `yyyy-MM` 过滤账单日期
- `billType` 只允许 `EXPENSE / INCOME`
- `categoryName` 走字典 usage 归一化后精确匹配
- `keyword` 匹配 `category_name / account_name / payment_method / merchant_name / note`

排序规则：

- `bill_date DESC`
- `updated_at DESC`
- `id DESC`

### 4.2 `GET /personal-bills/summary`

查询参数：

- `month`
- `year`

返回字段：

- `currentMonthExpense`
- `currentMonthIncome`
- `currentMonthBalance`
- `currentYearExpense`
- `annualBudgetAmount`
- `annualBudgetUsed`
- `annualBudgetRemaining`
- `annualBudgetUsageRate`
- `categoryDistribution`
- `recentBills`
- `budgetProgressList`

说明：

- `categoryDistribution` 只统计当月 `EXPENSE`
- `recentBills` 返回最近 5 条账单
- `budgetProgressList` 以当年预算为主表，按同分类支出累计已用金额

### 4.3 `GET /personal-bills/budgets`

查询参数：

- `year`

默认行为：

- 不传 `year` 时，取当前年份

### 4.4 写接口规则

账单写接口：

- `billType` 必填，只允许 `EXPENSE / INCOME`
- `categoryName` 必填，必须命中字典
- `paymentMethod` 允许为空；非空时必须命中字典
- `amount` 必须大于 0
- `billDate` 必填

预算写接口：

- `year` 必填
- `categoryName` 必填，必须命中字典
- `annualLimit` 必须大于 0
- `alertThreshold` 必须在 `0.01 ~ 1.00`
- 同年同分类不能重复

## 5. 后端分层建议

- `Controller`：只做参数接收、登录用户透传、响应包装
- `Service`：负责字典归一化、预算去重、统计聚合和事务边界
- `Mapper`：使用 MyBatis Plus `BaseMapper`
- `DTO`：查询参数和保存请求拆分
- `VO`：账单列表、预算列表、分类分布、预算执行进度、概览输出拆分

## 6. 演示数据策略

只给 `admin` 用户初始化演示数据：

- 年度预算 7 条
- 账单流水 10 条
- 数据覆盖 `2026-04` 和 `2026-05`
- 同时覆盖收入、支出、多分类和多支付方式

这样首页首次进入时，`admin` 账号能直接看到：

- 当月支出
- 当月收入
- 最近账单
- 年度预算执行率

非 `admin` 用户默认不插演示数据，避免污染真实用户账本。

## 7. 前端联调要求

前端已经改为：

- 不再使用 localStorage 本地兜底账单
- 分类和支付方式直接调用 `/system/dictionaries/options/by-usage`
- 账单表单、预算表单统一使用字典选项

因此后端联调时需要保证：

1. 个人账单接口全部可用
2. 数据字典 usage 已落库
3. `APP_PERSONAL_BILLS` 的 `data_source_mode` 为 `REAL`

## 8. 验证要求

- 后端至少执行个人账单模块单测与最小聚合编译
- 前端执行 `npm run build`
- 如接口字段后续调整，必须同步更新：
  - `src/api/personalBills.js`
  - `src/views/PersonalBills.vue`
  - 本文档
