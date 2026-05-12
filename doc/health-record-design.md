# 健康应用后端设计方案

本文档对应前端页面 `src/views/HealthRecord.vue` 的正式后端接入方案。  
本次目标是把健康应用从前端本地演示数据切到真实接口，并补齐“健康指标记录 / 医院就诊病历 / 报告单与附件”三类数据能力。

## 1. 设计范围

健康应用拆成 3 组业务对象：

1. 健康指标记录
   - 用于保存体重、血压、血脂、血糖、尿酸、肝功能等周期性指标
   - 对应前端现有“健康概览 / 指标趋势 / 历史记录”

2. 医院就诊记录
   - 用于保存每次医院就诊的病历摘要、科室、医生、诊断、处置建议等
   - 这是本次新增能力，解决“每次医院就诊的病历”沉淀问题

3. 报告单与附件
   - 用于保存体检报告、检查结果、化验单、影像报告等附件元数据
   - 报告既可以独立存在，也可以挂到某次就诊记录下

## 2. 已固定和新增的接口

### 2.1 保持前端现有接口

- `GET /health-records`
- `POST /health-records`
- `PUT /health-records/{id}`
- `DELETE /health-records/{id}`
- `GET /health-records/summary`
- `GET /health-records/trends`
- `GET /health-records/reports`
- `POST /health-records/reports`
- `PUT /health-records/reports/{id}`
- `DELETE /health-records/reports/{id}`
- `POST /health-records/reports/upload`

### 2.2 本次新增接口

- `GET /health-records/visits`
- `POST /health-records/visits`
- `PUT /health-records/visits/{id}`
- `DELETE /health-records/visits/{id}`

说明：

- 指标、报告接口保持不变，避免影响现有前端结构。
- 就诊记录单独成组，前端新增“医院就诊”业务块。
- 附件上传仍统一走 `/health-records/reports/upload`，前端保存时再把上传结果挂到“报告”或“就诊”上，避免重复造上传接口。

## 3. 表结构设计

### 3.1 健康指标表 `gak_health_record`

- `id`
- `owner_user_id`
- `measure_date`
- `height_cm`
- `weight_kg`
- `body_fat_rate`
- `systolic_pressure`
- `diastolic_pressure`
- `total_cholesterol`
- `triglycerides`
- `hdl_cholesterol`
- `ldl_cholesterol`
- `fasting_glucose`
- `heart_rate`
- `uric_acid`
- `alanine_aminotransferase`
- `aspartate_aminotransferase`
- `gamma_glutamyl_transferase`
- `note`
- `created_at`
- `updated_at`

索引：

- `idx_health_record_owner_measure_date`

说明：

- 金额类没有涉及，数值字段统一使用 `NUMERIC`，按指标精度控制 scale。
- 查询排序固定为 `measure_date DESC, updated_at DESC, id DESC`。

### 3.2 医院就诊表 `gak_health_visit`

- `id`
- `owner_user_id`
- `visit_date`
- `hospital_name`
- `department_name`
- `doctor_name`
- `visit_type`
- `chief_complaint`
- `diagnosis_summary`
- `treatment_plan`
- `doctor_advice`
- `case_record_file_name`
- `case_record_url`
- `note`
- `created_at`
- `updated_at`

索引：

- `idx_health_visit_owner_visit_date`

说明：

- `visit_type` 建议保存：`OUTPATIENT / EMERGENCY / INPATIENT / FOLLOW_UP`
- `case_record_file_name` 和 `case_record_url` 用于保存病历附件元数据
- 如果某次就诊有多张检查报告，使用报告表关联该就诊

### 3.3 健康报告表 `gak_health_report`

- `id`
- `owner_user_id`
- `visit_id`
- `exam_date`
- `hospital_name`
- `report_title`
- `summary`
- `doctor_advice`
- `report_file_name`
- `report_url`
- `created_at`
- `updated_at`

索引：

- `idx_health_report_owner_exam_date`
- `idx_health_report_owner_visit_id`

说明：

- `visit_id` 允许为空
- 体检报告、化验单、影像报告都落在这张表
- 如果是和某次就诊强关联的附件，可写入 `visit_id`

## 4. 返回口径

统一返回结构沿用 `ApiResponse`：

```json
{
  "code": "0",
  "message": "success",
  "data": {}
}
```

### 4.1 `GET /health-records`

查询参数：

- `pageNo`
- `pageSize`
- `metricKey`

返回字段：

- `list`
- `total`

说明：

- 前端当前主要一次性拉取历史数据，因此 page 默认允许较大值
- `metricKey` 仅作为前端后续优化预留，本次后端可先忽略并全量返回当前用户记录

### 4.2 `GET /health-records/summary`

返回字段建议：

- `latestMeasureDate`
- `lastExamDate`
- `lastVisitDate`
- `recordCount`
- `reportCount`
- `visitCount`

### 4.3 `GET /health-records/trends`

查询参数：

- `metricKey`
- `limit`

返回字段：

- `metricKey`
- `points`

其中 `points` 每项包含：

- `measureDate`
- `value`

说明：

- 复合指标如血压前端已拆成收缩压 / 舒张压，后端按单值指标返回即可
- 无效值不返回，避免趋势线出现空点

### 4.4 `GET /health-records/reports`

查询参数：

- `pageNo`
- `pageSize`
- `visitId`

返回：

- `list`
- `total`

### 4.5 `GET /health-records/visits`

查询参数：

- `pageNo`
- `pageSize`
- `keyword`

返回：

- `list`
- `total`

每条就诊记录建议附带：

- 基础字段
- `reportCount`

### 4.6 写接口规则

指标记录：

- `measureDate` 必填
- 至少要填写一个指标值
- 数值字段必须大于等于 0

就诊记录：

- `visitDate` 必填
- `hospitalName` 必填
- `chiefComplaint`、`diagnosisSummary`、`treatmentPlan`、`doctorAdvice` 至少填写 1 项
- `caseRecordUrl` 非空时必须同时写 `caseRecordFileName`

报告记录：

- `examDate` 必填
- `reportTitle` 必填
- `reportUrl` 非空时必须同时写 `reportFileName`
- `visitId` 非空时必须校验当前用户是否拥有该就诊记录

上传接口：

- 支持 `pdf / jpg / jpeg / png / webp / doc / docx`
- 文件大小建议不超过 `10MB`
- 返回：
  - `fileName`
  - `fileUrl`

## 5. 后端分层建议

- `Controller`
  - 只做参数接收、登录用户透传、响应包装
  - 文件上传接口单独暴露

- `Service`
  - 指标记录增删改查
  - 概览聚合与趋势计算
  - 就诊记录与报告归属校验
  - 文件路径安全校验

- `Mapper`
  - 使用 MyBatis Plus `BaseMapper`

- `DTO`
  - 指标记录、报告、就诊、趋势查询分别拆分

- `VO`
  - 概览、趋势点、就诊列表、报告列表分别拆分

## 6. 文件存储策略

- 参考现有应用图标上传实现，采用本地目录存储
- 建议目录：`./data/health-records`
- 对外访问前缀：`/api/health-records/report-files/{fileName}`

要求：

- 文件名使用随机 UUID，避免原文件名冲突
- 读取时必须做路径穿越校验
- 病历和报告统一走这套文件服务

## 7. 演示数据策略

只给 `admin` 用户初始化演示数据：

- 健康指标记录 6 条
- 医院就诊记录 3 条
- 健康报告 4 条

覆盖内容：

- 体重、血压、血脂、血糖、尿酸、肝功能趋势
- 门诊复诊、急诊、年度体检
- 带附件和不带附件两种场景

非 `admin` 用户不初始化演示数据。

## 8. 前端联调要求

前端需要同步完成：

1. 删除 localStorage 演示兜底
2. 保留现有指标与报告区域
3. 新增“医院就诊”业务块，支持：
   - 列表展示
   - 新增
   - 编辑
   - 删除
   - 打开病历附件
4. `APP_HEALTH_RECORD` 的 `data_source_mode` 更新为 `REAL`

## 9. 验证要求

- 后端至少执行健康模块单测和最小聚合编译
- 前端执行 `npm run build`
- 如接口字段后续调整，必须同步更新：
  - `src/api/healthRecord.js`
  - `src/views/HealthRecord.vue`
  - 本文档
