# WoW角色统计字典化增量 Prompt

## 1. 背景

前端 `WoW角色统计` 已经完成以下调整：

- `className`、`faction`、`mythicDungeonName` 已走数据字典
- `raceName` 改为数据字典下拉
- `specName` 改为数据字典下拉
- `professionPrimary`、`professionSecondary` 改为数据字典下拉
- 前端已增加联动限制：
  - 职业 -> 可选种族
  - 种族 -> 可选阵营
  - 职业 -> 可选专精

当前数据库已补充：

- `WOW_CHARACTER_RACE`：26 个种族
- `WOW_CLASS_SPEC`：40 个专精
- `WOW_PRIMARY_PROFESSION`：11 个主专业
- 对应落库脚本：`docs/sql/wow-character-dictionary-upgrade.sql`
- usage 绑定：
  - `raceName -> WOW_CHARACTER_RACE`
  - `specName -> WOW_CLASS_SPEC`
  - `professionPrimary -> WOW_PRIMARY_PROFESSION`
  - `professionSecondary -> WOW_PRIMARY_PROFESSION`

## 2. 最新规则口径

请以后端最终校验为准，同步到与前端一致的最新口径。

本次前端规则整理依据：

- 2026-02-25 Blizzard 官方文章：`Midnight: Embrace the Void with the Devourer Specialization`
- 2026-03-22 查阅 Blizzard 官方职业页：
  - `Death Knight`
  - `Demon Hunter`
  - `Druid`
  - `Priest`
  - `Warlock`
- 2026-02-18 Blizzard 官方文章：`Haranir: The Next Allied Race Coming in World of Warcraft: Midnight`
- 2024-09-12 Blizzard 官方文章：`Dracthyr New Classes`

注意：

- `Void Elf Demon Hunter` 已在 `2026-01-20` 的 Midnight pre-expansion content update 开放，并在 `2026-02-25` 官方文章中再次确认
- `Demon Hunter` 当前前端按官方英文页支持 3 个专精：`Devourer / Havoc / Vengeance`
- `specName` 为解决“冰霜 / 神圣 / 恢复 / 防护”跨职业重名问题，前端当前保存的是唯一 code，不再直接保存中文显示名

## 3. 当前前端约定

### 3.1 className

- 继续按现状保存 `itemValue`
- 当前 `WOW_CLASS_NAME.itemValue` 仍是中文职业名：
  - `死亡骑士`
  - `恶魔猎手`
  - ...

### 3.2 raceName

- 前端保存 `WOW_CHARACTER_RACE.itemValue`
- 当前 `itemValue` 是中文种族名，例如：
  - `人类`
  - `血精灵`
  - `龙希尔`
  - `土灵`
  - `哈拉尼尔`

### 3.3 specName

- 前端保存 `WOW_CLASS_SPEC.itemValue`
- 当前 `itemValue` 是唯一 code，例如：
  - `blood_death_knight`
  - `frost_mage`
  - `holy_paladin`
  - `holy_priest`
  - `restoration_druid`
  - `restoration_shaman`
- 前端显示时用 `itemLabel`

### 3.4 professionPrimary / professionSecondary

- 前端保存 `WOW_PRIMARY_PROFESSION.itemValue`
- 当前 `itemValue` 为中文专业名，例如：
  - `炼金术`
  - `工程学`
  - `草药学`

## 4. 后端必须修改

### 4.1 gak-wow-character

请修改 `WowCharacterService`、相关 DTO/VO，要求：

1. `raceName` 不再只是“字典存在就放行，否则回退自由输入”
   - 改为正式按字典校验
   - 非法值直接报错

2. `specName` 接入数据字典 usage 校验
   - 绑定字段：`specName -> WOW_CLASS_SPEC`
   - 按 `itemValue` 校验
   - 当前允许保存唯一 code

3. `professionPrimary / professionSecondary` 接入数据字典 usage 校验
   - 绑定字段：
     - `professionPrimary -> WOW_PRIMARY_PROFESSION`
     - `professionSecondary -> WOW_PRIMARY_PROFESSION`
   - 两个字段都允许为空
   - 两个字段不能相同

4. 增加联动校验
   - `className` 与 `raceName` 必须匹配
   - `raceName` 与 `faction` 必须匹配
   - `className` 与 `specName` 必须匹配

5. 为兼容旧数据，`specName` 写入时建议兼容两种入参
   - 新格式：唯一 code，例如 `holy_paladin`
   - 旧格式：旧中文名，例如 `神圣`
   - 后端收到旧中文名时，应按 `className + spec label` 归一化为当前唯一 code 再入库

6. 查询返回建议补充 label 字段
   - `specNameLabel`
   - `professionPrimaryLabel`
   - `professionSecondaryLabel`
   - 前端当前可以自行映射，但后端补 label 后会更稳

### 4.2 gak-data-dictionary

为了让这套联动规则后续真正由数据字典驱动，而不是继续写在前端常量里，请补这两个能力：

1. `DataDictionaryItemVO` 增加 `extraJson`
2. `DictionaryOptionVO` 增加 `extraJson`

当前数据库里已在字典项 `extra_json` 中预留：

- `WOW_CHARACTER_RACE.extra_json`
  - `factions`
  - `allowedClassCodes`
- `WOW_CLASS_SPEC.extra_json`
  - `classCode`

这样前端和后端后续都可以直接按元数据做联动，不再维护第二套硬编码规则。

## 5. 推荐实现

### 5.1 WowCharacterService

新增统一归一化方法：

- `normalizeRequiredRaceName`
- `normalizeRequiredSpecName`
- `normalizeOptionalProfession`
- `validateClassRaceRelation`
- `validateRaceFactionRelation`
- `validateClassSpecRelation`

### 5.2 关系读取方式

推荐不要继续写死枚举：

- 优先从数据字典项 `extraJson` 读关系
- 如果当前支撑层暂时还没开放 `extraJson`，可先在 `gak-wow-character` 内做一层过渡映射
- 但最终目标必须是：
  - 关系元数据在字典里
  - 前后端都消费同一份字典元数据

## 6. 直接可喂 Agent 的增量 Prompt

```text
请对 gak-wow-character 和 gak-data-dictionary 做一次增量修改，要求如下：

1. WowCharacterService：
   - raceName 不再保留“字典不存在则放行”的旧逻辑，改为正式字典校验
   - specName 接入数据字典 usage：APP_WOW_CHARACTER / WOW_CHARACTER / specName -> WOW_CLASS_SPEC
   - professionPrimary / professionSecondary 接入数据字典 usage：-> WOW_PRIMARY_PROFESSION
   - professionPrimary 与 professionSecondary 不能相同
   - 增加 3 个联动校验：
     - className 与 raceName
     - raceName 与 faction
     - className 与 specName
   - specName 需兼容旧中文 label 入参，并统一归一化为唯一 code 后存库

2. gak-data-dictionary：
   - SaveDataDictionaryItemRequest 增加 extraJson
   - DataDictionaryItemVO 增加 extraJson
   - DictionaryOptionVO 增加 extraJson
   - list items / options / options by usage 接口都能返回 extraJson

3. 当前数据库已存在以下字典，请按这些 dictCode 接入，不要重新起名：
   - WOW_CHARACTER_RACE
   - WOW_CLASS_SPEC
   - WOW_PRIMARY_PROFESSION

4. 当前前端约定：
   - className 保存中文职业名
   - raceName 保存中文种族名
   - specName 保存唯一 code，如 holy_paladin / frost_mage / restoration_shaman
   - professionPrimary / professionSecondary 保存中文专业名

5. 返回兼容旧数据：
   - 建议补充 specNameLabel / professionPrimaryLabel / professionSecondaryLabel
```
