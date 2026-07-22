# WoW 角色列表后端排序设计

## 目标

让 `GET /wow-characters` 接口支持前端角色列表已经发送的排序参数，并确保排序在分页切片之前完成。未传、残缺或非法的排序参数继续使用现有默认顺序。

## 请求契约

`WowCharacterQueryRequest` 增加两个可选字段：

- `sortField`：允许 `faction`、`characterName`、`specName`、`level`、`realmName`、`itemLevel`、`currentKey`、`mythicScore`。
- `sortDirection`：允许 `ASC` 或 `DESC`，方向值忽略大小写并去除首尾空白。

只有字段和方向同时有效时才启用自定义排序。任一字段缺失、空白或不在白名单内，均回退到默认排序，不返回参数错误。

## 排序行为

Service 根据白名单选择内存 Comparator，并在现有分页切片之前对全部匹配角色排序：

- 文本字段按字符串自然顺序比较，空值按空字符串处理。
- 数值字段按数值大小比较，空值按零处理。
- `currentKey` 先比较 `mythicBestLevel`，再比较 `mythicDungeonName`；排序方向同时作用于两级比较。
- 自定义字段相同时，继续使用现有默认 Comparator，再以角色 ID 升序兜底，保证分页结果稳定。
- 未启用自定义排序时，保持“装等降序、M+ 总分降序、角色名升序”的现有默认行为，并增加角色 ID 升序作为最终稳定顺序。

排序字段只通过代码白名单映射到 getter，不拼接 SQL 字段名。

## 代码范围

- `WowCharacterQueryRequest`：新增请求字段及访问器。
- `WowCharacterService`：新增排序字段映射、方向解析和稳定 Comparator。
- `WowCharacterServiceTest`：覆盖文本和数值字段的升降序、当前钥匙组合排序、非法或残缺参数回退，以及分页前排序。

不修改 Controller、数据库结构和前端代码。

## 验证

使用项目内 JDK 21 执行：

```powershell
.\scripts\backend-dev.ps1 -pl gak-modules/gak-wow-character -am test
```

验收标准：前端点击任一支持列后，接口返回顺序随 `ASC`/`DESC` 改变；第三次点击取消排序后恢复默认顺序；非法参数也恢复默认顺序。
