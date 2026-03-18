# WoW角色统计后端接口代码生成提示词

本文档用于把前端已预留的“WoW角色统计”接口一次性生成为后端代码。  
前端项目已固定以下接口路径（基于 `/api` 代理后的实际后端路径）：

- `GET /wow-characters`：分页查询角色列表
- `POST /wow-characters`：新增角色
- `PUT /wow-characters/{id}`：编辑角色
- `DELETE /wow-characters/{id}`：删除角色
- `GET /wow-characters/overview`：查询角色概览、主角色名片和统计信息

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
- 列表接口返回 `data.list` + `data.total`
- 概览接口返回 `data` 为单个对象
- 失败时返回清晰 `message`，前端会直接展示

## 2. 可直接投喂 AI 的全量生成 Prompt

```text
你是一名资深 Java 后端工程师，请基于 Spring Boot 3 + Spring Web + Spring Validation + Spring Security + Spring Data JPA（或 MyBatis）生成“WoW角色统计模块”完整代码，要求可直接运行。

【目标】
实现以下 REST 接口（基础路径 /wow-characters）：
1) GET /wow-characters
   - 查询参数：pageNo(默认1), pageSize(默认10), keyword(可选), faction(可选), className(可选)
   - keyword 匹配：characterName、realmName、raceName、professionPrimary、professionSecondary
   - 返回分页列表：{ list: WowCharacterListVO[], total: number }
2) POST /wow-characters
   - 入参：
     characterName, className, specName, raceName, realmName, faction, level, itemLevel, mythicBestLevel, mythicScore, professionPrimary, professionSecondary, note
3) PUT /wow-characters/{id}
   - 入参同新增
4) DELETE /wow-characters/{id}
5) GET /wow-characters/overview
   - 查询参数：keyword(可选), faction(可选), className(可选)
   - 返回：
     {
       totalCharacters: number,
       totalRealms: number,
       highestItemLevel: number,
       highestMythicScore: number,
       averageItemLevel: number,
       featuredCharacters: WowCharacterSimpleVO[],
       factionStats: FactionStatVO[],
       classStats: ClassStatVO[],
       realmStats: RealmStatVO[]
     }

【数据模型】
请生成 wow_character 表，字段至少包含：
- id (Long, PK)
- ownerUserId (Long, 当前登录用户ID)
- characterName
- className
- specName (nullable)
- raceName
- realmName
- faction (ALLIANCE / HORDE)
- level
- itemLevel
- mythicBestLevel (nullable)
- mythicScore (nullable)
- professionPrimary (nullable)
- professionSecondary (nullable)
- note (nullable)
- createdAt
- updatedAt

【业务规则】
- characterName 必填，长度不超过 32
- className 必填，且必须是正式服 13 职业之一：
  死亡骑士、恶魔猎手、德鲁伊、唤魔师、猎人、法师、武僧、圣骑士、牧师、潜行者、萨满、术士、战士
- raceName 必填，长度不超过 24
- realmName 必填，长度不超过 32
- faction 只允许：ALLIANCE / HORDE
- level 必填，范围建议 1-80
- itemLevel 必填，且 >= 0
- mythicBestLevel、mythicScore 可为空；为空时按 0 处理
- 当前登录用户只能访问自己的角色数据
- 列表排序建议：itemLevel DESC, mythicScore DESC, characterName ASC
- overview.featuredCharacters 返回装等最高的 2 个角色；装等相同时按 mythicScore DESC
- overview.classStats 返回每个职业的角色数量和平均装等
- overview.realmStats 返回服务器角色数量和该服最高装等，可只返回前 5 个

【推荐返回 VO】
WowCharacterListVO:
- id
- characterName
- className
- specName
- raceName
- realmName
- faction
- level
- itemLevel
- mythicBestLevel
- mythicScore
- professionPrimary
- professionSecondary
- note
- updatedAt

WowCharacterSimpleVO:
- id
- characterName
- className
- specName
- raceName
- realmName
- faction
- level
- itemLevel
- mythicBestLevel
- mythicScore
- professionPrimary
- professionSecondary

FactionStatVO:
- label
- count
- ratio

ClassStatVO:
- className
- count
- averageItemLevel

RealmStatVO:
- realmName
- count
- highestItemLevel

WowCharacterOverviewVO:
- totalCharacters
- totalRealms
- highestItemLevel
- highestMythicScore
- averageItemLevel
- featuredCharacters[]
- factionStats[]
- classStats[]
- realmStats[]

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
- 提供至少 12 条初始化测试数据，覆盖联盟 / 部落、不同职业、不同服务器、不同装等和不同大秘境评分
- 提供 curl 示例
- 代码中补充关键注释，不要只给伪代码
```

## 3. 单接口增量 Prompt

### 3.1 概览接口

```text
请为现有 Spring Boot 项目新增 GET /wow-characters/overview 接口。
查询参数：keyword(可选), faction(可选), className(可选)

要求：
1) 只统计当前登录用户自己的角色数据
2) 返回 totalCharacters、totalRealms、highestItemLevel、highestMythicScore、averageItemLevel
3) 返回 featuredCharacters，取装等最高的 2 个角色；装等相同按 mythicScore 降序
4) 返回 factionStats、classStats、realmStats
5) 返回统一结构：{ code, message, data }
6) 提供 Controller + Service + VO + 单元测试
```

### 3.2 列表接口

```text
请为现有 Spring Boot 项目新增 GET /wow-characters 分页接口。
查询参数：pageNo, pageSize, keyword, faction, className

要求：
1) 只查询当前登录用户自己的角色
2) keyword 匹配 characterName、realmName、raceName、professionPrimary、professionSecondary
3) 返回 data.list + data.total
4) 列表按 itemLevel DESC, mythicScore DESC, characterName ASC
5) 提供 Controller + Service + Mapper/Repository + VO + 单元测试
```

### 3.3 新增 / 编辑接口

```text
请为现有 Spring Boot 项目新增 POST /wow-characters 和 PUT /wow-characters/{id} 接口。

要求：
1) 入参字段：
   characterName, className, specName, raceName, realmName, faction, level, itemLevel, mythicBestLevel, mythicScore, professionPrimary, professionSecondary, note
2) className 必须限制为正式服 13 职业之一
3) faction 只允许 ALLIANCE / HORDE
4) level 范围建议 1-80，itemLevel >= 0
5) 返回统一结构：{ code, message, data }
6) 提供 DTO 校验、Controller、Service、单元测试
```

## 4. 联调约定

- 前端接口文件固定为 `src/api/wowCharacter.js`
- 前端页面固定路由为 `/wow-character-stats`
- 前端主页应用入口名称固定为 `WoW角色统计`
- 前端会优先展示装等最高的 2 个主角色名片
- 前端列表支持 `keyword / faction / className` 组合筛选
- 前端后备演示数据包含正式服 13 职业色，请不要随意改动 `className` 中文值
- 如果后端返回结构不是 `data.list/data.total`，或 overview 字段名不一致，请同步调整前端解析逻辑
