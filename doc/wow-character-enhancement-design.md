# WoW角色统计优化设计

## 1. 目标

本次对 `WoW角色统计` 做一次结构化增强，目标如下：

1. 主角色卡片从 2 个提升到 4 个，单行展示并整体收紧布局。
2. 主角色卡片增加联盟 / 部落两套特色背景图，由 `image-2` 生成静态位图资源。
3. 角色装等改为保留 2 位小数。
4. 大秘境赛季记录改为用户直接维护 8 个副本的单本整数分数，系统自动汇总 M+ 总分。
5. 保留“当前钥匙”字段，主角色卡片继续展示当前钥石层数和副本名。
6. 新增每周低保记录模块，支持团本 / 大秘境 / 世界任务或地下堡三条轨道的九宫格解锁情况。
7. 是否进入主角色卡片展示位改为由用户手工勾选，最多同时保留 4 个主角色。

## 2. 范围

本次改动包含：

- 后端 `gak-wow-character` 模块
- 前端 `WowCharacterStats.vue`
- 数据库 `schema.sql`
- 默认演示数据
- 设计文档

本次不做：

- Battle.net 实时同步
- 历史赛季切换
- 多角色共享周低保

## 3. 数据模型调整

### 3.1 角色主表 `gak_wow_character`

保留现有角色主表，重点调整：

- `item_level`：由 `INTEGER` 调整为 `NUMERIC(8, 2)`，统一保留 2 位小数
- `mythic_best_level`、`mythic_dungeon_name`：继续作为“当前钥匙”字段录入与展示
- `mythic_score`：由 8 个副本分数自动聚合，建议保留 `NUMERIC(10, 2)` 兼容历史数据
- `is_featured`：布尔值，表示是否作为主角色卡片展示，默认 `FALSE`

兼容策略：

- 历史数据如果已有 `mythic_best_level` 且 `mythic_dungeon_name` 非空，初始化时同步生成一条对应副本记录
- 历史 `mythic_score` 不再作为写入来源，但保留字段并由新逻辑覆盖刷新
- 历史数据 `is_featured` 默认按 `FALSE` 处理，由用户后续手工设置

### 3.2 大秘境副本记录表 `gak_wow_character_mythic_run`

用途：记录某角色在当前赛季 8 个副本的手工分数。

建议字段：

- `id`
- `character_id`
- `owner_user_id`
- `dungeon_name`
- `score`
- `created_at`
- `updated_at`

约束：

- 唯一键：`(character_id, dungeon_name)`
- `score >= 0`
- `score` 由前端录入单本整数分，后端只负责聚合总分

说明：

- 当前只记录单本赛季分数
- 若某副本未完成，则分数记为 `0`

### 3.3 每周低保记录表 `gak_wow_character_weekly_vault`

用途：记录某角色某一周三条轨道的完成数量和解锁状态。

建议字段：

- `id`
- `character_id`
- `owner_user_id`
- `week_start_date`
- `raid_progress_count`
- `mythic_progress_count`
- `world_progress_count`
- `raid_slot_1_unlocked`
- `raid_slot_2_unlocked`
- `raid_slot_3_unlocked`
- `mythic_slot_1_unlocked`
- `mythic_slot_2_unlocked`
- `mythic_slot_3_unlocked`
- `world_slot_1_unlocked`
- `world_slot_2_unlocked`
- `world_slot_3_unlocked`
- `note`
- `created_at`
- `updated_at`

唯一键：

- `(character_id, week_start_date)`

阈值规则：

- 团本：`2 / 4 / 6`
- 大秘境：`1 / 4 / 8`
- 世界任务 / 地下堡：`2 / 4 / 8`

说明：

- 三组 `*_slot_*_unlocked` 不接受前端直接维护，由后端根据完成次数自动推导
- 前端只提交三条轨道的完成数量

## 4. 评分规则

### 4.1 大秘境总分

- `mythicScore = 8 个副本 score 求和`
- 单副本分数由用户直接录入，要求为非负整数
- 如果某角色只有部分副本记录，未录入副本按 `0` 分处理

### 4.2 主角色限制

- `isFeatured = true` 的角色最多只能有 4 个
- 新增或编辑时，如果勾选主角色后总数超过 4，后端直接拒绝保存
- 主角色卡片仍按 `itemLevel DESC`、`mythicScore DESC` 排序，但仅在 `isFeatured = true` 集合内排序

## 5. 接口设计

本次尽量复用现有接口路径，不新增角色主资源路由前缀。

### 5.1 角色分页接口

`GET /wow-characters`

返回新增字段：

- `itemLevel`：两位小数
- `mythicScore`：系统汇总结果
- `isFeatured`：是否主角色
- `mythicRuns`：8 副本记录列表
- `weeklyVaults`：最近若干周低保记录

### 5.2 新增角色

`POST /wow-characters`

请求体新增：

- `itemLevel`
- `isFeatured`
- `mythicBestLevel`
- `mythicDungeonName`
- `mythicRuns`
- `weeklyVaults`

其中：

- `mythicRuns` 固定按 8 副本维护，每项只提交分数
- `weeklyVaults` 可为空

### 5.3 编辑角色

`PUT /wow-characters/{id}`

规则同新增：

- 每次编辑角色时同步覆盖副本记录
- 周低保记录按 `id` 或 `weekStartDate` 做更新 / 新增

### 5.4 概览接口

`GET /wow-characters/overview`

调整点：

- `featuredCharacters` 返回最多 4 个且 `isFeatured = true` 的角色
- 排序规则按 `itemLevel DESC`，同装等再按 `mythicScore DESC`
- `highestItemLevel` / `averageItemLevel` 统一两位小数

## 6. 前端页面设计

### 6.1 主角色卡片

- 固定 4 列单行展示
- 缩小卡片内边距、头像区和统计区高度
- 联盟 / 部落根据阵营切换背景图
- 只展示装等、M+ 总分和当前钥匙
- 无数据时显示占位卡片补足 4 位

### 6.2 角色编辑弹窗

新增两个业务块：

1. 每周低保
   - 支持维护多周记录
   - 每周一张卡片
   - 使用 3 x 3 的九宫格表现解锁状态

2. 大秘境赛季副本记录
   - 放到每周低保模块下方
   - 固定 8 行
   - 每行展示副本名称 + 手工整数分数
   - 总分实时汇总展示

3. 角色核心字段
   - 增加“是否主角色”开关
   - 恢复“当前钥匙层数 + 当前钥匙副本”录入

### 6.3 列表展示

- 装等统一格式化为两位小数
- 大秘境评分展示系统汇总结果
- 主角色卡片不再显示 8 本进度，只显示当前钥匙

## 7. 资源设计

新增资源：

- `public/brand/wow-alliance-card-bg.png`
- `public/brand/wow-horde-card-bg.png`

要求：

- 使用 `image-2` 生成
- 保持横向卡片背景比例
- 联盟风格偏蓝金、圣光、狮鹫 / 城堡纹章感
- 部落风格偏赤红、黑铁、战旗 / 图腾感
- 不直接塞文字，避免和卡片信息冲突

## 8. 种子数据

仅为 `admin` 用户增加演示数据：

- 至少 4 个角色，确保主角色卡片可完整展示
- 每个角色带 8 副本层数示例
- 至少 2 周低保记录

## 9. 验证要求

后端：

- `gak-wow-character` 模块单测通过
- `./mvnw -pl gak-modules/gak-wow-character -am test`

前端：

- `npm run build`

联调关注点：

- 旧角色数据兼容读取
- 8 副本总分计算正确
- 周低保九宫格解锁阈值正确
- 主角色卡片 4 列在 1440 宽屏下单行稳定显示
