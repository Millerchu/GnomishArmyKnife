# 工作台二期里程碑①：偏好接口冻结契约

本次实现基于后端 HEAD `8991747` 与方案 4.1–4.4.2、第 6 节的后端默认值。
新增独立模块 `gak-workbench`，无需 APP 授权。身份只取内存 TokenService 的当前用户。
本期后端代码已本地提交；gak_dev 已由 gak_dev 角色执行建表迁移，未执行权限脚本；未推送、未部署。
Morgan 侧执行器此前已在沙箱外执行临时环境端到端验证；本轮按 Forge 评审修复并更新验证脚本，由 Morgan 侧执行器在沙箱外复验，本代理不执行该脚本。

## HTTP 与 JSON

请求头：`Authorization: Bearer <token>`；PUT 使用 `Content-Type: application/json`。
PUT 缺失或错误 Content-Type 返回 HTTP 415，code 为 `WORKBENCH_PREFERENCE_INVALID`，message 为「工作台偏好请求必须使用 Content-Type: application/json」。格式错误 JSON 仍为 HTTP 400 和同一业务码；该映射仅作用于工作台控制器。

成功响应与 HTTP 409 的 `data.version` 始终为 JSON 数字（整数），包括无记录时的 `0`。
这是不超过 JavaScript 安全整数范围（`2^53-1`）的小计数器，客户端可直接回传。
VO 字段显式使用数字序列化器，局部覆盖 `JacksonLongToStringConfig`；全局 Long→字符串规则及其他模块保持原行为。
PUT 继续仅接受 JSON 整数，拒绝 `"0"` 等纯数字字符串及 `0.0` 等小数，不增加隐式类型转换。

`updatedAt` 为服务端本地更新时间，沿用仓库 VO 常见的 `yyyy-MM-dd HH:mm:ss` 字符串格式，
精确到秒，不含 `T`、小数秒、时区偏移或 `Z`；源类型为 `LocalDateTime`，不做时区换算。
成功和 409 响应使用同一格式；无记录时省略该字段。

GET `/workbench/preferences`，无记录 HTTP 200（无 updatedAt 字段）：

```json
{"code":"0","message":"success","data":{"version":0,"schemaVersion":1,"payload":null}}
```

PUT `/workbench/preferences`，首次创建的完整示例：

```json
{
  "version": 0,
  "schemaVersion": 1,
  "payload": {
    "cards": [{"id":"todo","hidden":false,"size":"S","x":0,"y":0,"w":8,"h":4}],
    "dock": {"collapsed":false,"order":["todo-list","","todo-list","work-log"]}
  }
}
```

HTTP 200，保存规范化后的文档（后续 GET 返回同一文档，updatedAt 为服务端更新时间）：

```json
{
  "code":"0",
  "message":"success",
  "data":{
    "version":1,
    "schemaVersion":1,
    "payload":{
      "cards":[{"id":"todo","hidden":false,"size":"S","x":0,"y":0,"w":4,"h":2}],
      "dock":{"collapsed":false,"order":["todo-list","work-log"]}
    },
    "updatedAt":"2026-11-11 12:00:00"
  }
}
```

更新须发送 GET 返回的 version；成功 version 加一。重复 version=0 创建或版本过期返回 HTTP 409：

```json
{
  "code":"WORKBENCH_PREFERENCE_VERSION_CONFLICT",
  "message":"布局已在其他设备更新，请查看后选择保留云端或用本地覆盖。",
  "data":{
    "version":2,
    "schemaVersion":1,
    "payload":{
      "cards":[{"id":"todo","hidden":false,"size":"S","x":0,"y":0,"w":4,"h":2}],
      "dock":{"collapsed":true,"order":["todo-list","work-log"]}
    },
    "updatedAt":"2026-11-11 12:01:00"
  }
}
```

若 version>0 更新的记录不存在，同样返回 409，data 为 `{ "version":0,"schemaVersion":1,"payload":null }`，不自动重建。

校验失败 HTTP 400：

```json
{"code":"WORKBENCH_PREFERENCE_INVALID","message":"card.size 只接受 S、M、L","data":null}
```

无 token / 过期 token HTTP 401：

```json
{"code":"401","message":"未登录或登录已过期","data":null}
```

GET 和 PUT 开头均检查 token 用户是否仍在数据库中；已删除用户持旧 token 请求返回 HTTP 401，code 为 `401`、message 为「当前登录用户不存在」，与油耗等模块保持一致。

原有 BusinessException 仍为 HTTP 400，code 和 message 沿用原值；框架公共响应与异常映射未修改。

## 写入校验和规范化细则

- version 必填，非负 JSON 整数，最大 `Long.MAX_VALUE-1`；禁止字符串、小数强制转整数。
- schemaVersion 必填，只接受 JSON 整数 1。高版本提示更新客户端或升级服务端；本期没有可识别的历史版本，0/负数明确拒绝，纯函数 `upgrade(payload, from)` 保留逐级迁移入口；v1 返回独立副本，不修改原树。读取使用下面的独立宽松入口。
- payload 必须是对象。缺失 cards 默认 `[]`；缺失 dock 默认 `{ "collapsed":false,"order":[] }`。显式 null 均按错误处理。未知字段丢弃，包括请求中的 userId、user_id、pref_key。
- cards 最多 6 张；id 只能为 todo/workLog/bills/health/fuel/knowledge，重复拒绝；每张卡的 id/hidden/size/x/y/w/h 必填。hidden 为严格布尔，size 仅 S/M/L；坐标及输入 w/h 为 int 范围内整数。
- w/h 以档位覆盖（S=4×2，M=6×3，L=8×4）；覆盖后校验 x>=0、x+w<=12、y>=0、y+h<=48；不检查卡片之间重叠。
- dock 可缺省 collapsed/order；存在时 collapsed 必须为布尔、order 为字符串数组。输入 order 最多 100 项；元素 strip 后最多 64 个 UTF-16 单元，去空、去重、保留首次顺序和历史应用键；拒绝 JSONB 无法存储的 NUL/孤立代理字符。
- 规范化后紧凑 JSON 的 UTF-8 字节数 <=16384，恰好 16384 合法；未知字段不计入。原始请求流 <=65536 字节，在 Jackson 解析前限流，含 Content-Length 缺失或分块请求。

## 读取兼容（GET 与 409 最新文档）

- 已存 payload 不走写入严格校验，不因历史或脏数据返回 400。始终保留数据库中的 version（JSON 数字）、schemaVersion 和 updatedAt，便于客户端选择覆盖；宽松读取不回写数据库。
- 已知 schema 的对象 payload 仅保留白名单字段。cards 非数组回落 `[]`；未知、非法卡片丢弃，相同 id 保留第一张合法卡片。有效卡片仍按档位规范化尺寸并校验网格边界。
- dock 非对象回落默认 `{ "collapsed":false,"order":[] }`；collapsed 非布尔回落 false，order 非数组回落 []。数组内丢弃非字符串、过长或 JSONB 不支持的键，strip 后去空去重，最多保留 100 个有效键；其他合法 dock 字段独立保留。
- payload 非对象（含 null、数组、标量）、schema_version 高于服务端认识的版本（例如 2）或为不支持的历史版本、JSONB 超过当前 Jackson 解析能力（例如嵌套过深）、宽松规范化后仍超出 16384 字节，返回 `payload:null`。未来格式返回其原始 schemaVersion，例如 `{ "version":4,"schemaVersion":2,"payload":null,"updatedAt":"2026-11-11 12:00:00" }`；不是 version=0 的无记录文档。
- 后续 PUT 仍须带客户端支持的 `schemaVersion:1`、最新 version 及严格合法的 payload；不会隐式接受已存脏数据。

## 持久化与迁移

- `public.gak_user_preference`：应用侧雪花 ID、固定服务端键 workbench、JSONB、行版本与时间戳。用户外键为 `REFERENCES public.gak_user (id) ON DELETE CASCADE`，删除用户时偏好自动随之删除；应用角色无需偏好表 DELETE 权限。
- UNIQUE(user_id,pref_key) 已覆盖精确查询与用户前缀，不建额外重复索引。
- MyBatis-Plus entity 开启 autoResultMap，JSONB TypeHandler 用 JDBC OTHER 写入，读取还原 JsonNode；自定义查询也显式指定 handler。
- 创建使用 `ON CONFLICT (user_id,pref_key) DO NOTHING`；竞争返回 0 后读最新文档，避免 PG 事务因唯一约束异常中止。更新显式绑定用户、键与旧 version。写事务使用 READ_COMMITTED，使冲突后查询可见胜出提交。
- 全新库用 schema.sql（表名沿用仓库惯例不加 schema 限定）；已有库只执行 docs/sql/20261111-user-preference*.sql 增量，初始化模式 never。两个增量脚本使用 psql、开头自带 `\set ON_ERROR_STOP on`，表及外键均限定 public，不受调用者 search_path 影响。`CREATE TABLE IF NOT EXISTS` 不修复已存在表的旧外键策略或权限；若已建旧表需另行安排 ALTER 迁移。
- 权限脚本由 owner 或有授权权限的角色执行。必传 `-v app_role=实际应用角色`；GRANT 前校验角色存在、不是 public（大小写不敏感）、不是表 owner、不是超级用户，任一不满足即报错非零退出且不授权。只 GRANT SELECT/INSERT/UPDATE，无 DELETE、无序列，可重复执行。普通 app 角色无法给自己授权。

## 离线验证与沙箱外端到端入口

沙箱限制 JVM 自附加，测试需显式加载已有 Byte Buddy agent：

```bash
./mvnw -o -pl gak-modules/gak-workbench -am test \
  -DargLine=-javaagent:/home/box/.m2/repository/net/bytebuddy/byte-buddy-agent/1.15.11/byte-buddy-agent-1.15.11.jar

./mvnw -o package -Dtest='*,!GakStartApplicationTests' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DargLine=-javaagent:/home/box/.m2/repository/net/bytebuddy/byte-buddy-agent/1.15.11/byte-buddy-agent-1.15.11.jar
```

排除的既有 GakStartApplicationTests 会默认启动完整上下文和数据库初始化，离线验证阶段未授权连接现有库，因此当时未执行。
其他数据库集成用例沿用仓库 opt-in 模式，默认跳过。新增真实 PG 用例仅接受
`GAK_WORKBENCH_TEST_URL=jdbc:postgresql://127.0.0.1:55491/wb2_verify`，在随机独立 schema 验证 JSONB 两种映射、版本更新和真实并发创建，并清理自身 schema。

沙箱外端到端脚本：`/tmp/wb2/verify-temp.sh`。本轮已更新全部新增检查，并通过 `bash -n` 及 4 段内嵌 Python compile 语法检查；本代理未执行脚本。
Morgan 侧执行器在沙箱外执行 `bash /tmp/wb2/verify-temp.sh`，使用已打包 jar、临时 PG16 55491/wb2_verify、后端 18091；初始化 schema 后 DROP 新表模拟已有库，增量及授权均执行两次，验证非 owner 角色实际权限，再由 owner 插入 bcrypt 合成用户，走真实验证码与 RSA 登录。
新增检查包括 public 表及 CASCADE 外键、缺 app_role/public/owner/超级用户授权负例（核对非零退出及 ACL 不变）、非 owner 删除用户触发级联且仍无偏好表 DELETE、415/400、删除用户后旧 token 的 GET/PUT 401、已存非法卡片/非法 dock/未来 schema/非对象文档的 GET 200 与 409 均保留数字 version。用户表 DELETE 由 owner 在测试中显式授给 wb2_app，仅用于合成用户级联测试。
脚本逐项输出 PASS/FAIL，trap 清理后写 `/tmp/wb2/verify-result.json`；删除临时 PG 与含口令、token、cookie 的私有目录，并记录受保护端口进程前后快照。已有 `/tmp/wb2/pg` 或临时端口占用时直接拒绝覆盖。

Morgan 侧执行器此前反馈：仅用 `schema.sql` 初始化的全新库缺少 `APP_USER_AUTH` / `AUTH_REGISTER` 数据字典绑定，
`/auth/register` 会返回 `ROLE_CODE_INVALID`。这是既有现象，与本期工作台偏好无关，本轮只记录，不修改注册逻辑或初始化数据。
Morgan 侧执行器此前已将上述脚本改为由 owner 直接插入 bcrypt 合成用户，再走真实登录；当前脚本不再依赖注册流程。

## 本次实际验证结果

- 两条上述 Maven 命令均离线通过，测试使用显式 Byte Buddy javaagent，无依赖下载或版本变更。全量 package 包含编译、测试及 Spring Boot repackage；排除 GakStartApplicationTests，数据库 opt-in 用例保持关闭。
- 全仓库：421 项，377 通过，44 跳过，0 失败，0 错误。
- 工作台模块：82 项，80 通过，2 项真实 PG 测试未开启；模块 test 命令连同 framework/user 共 91 项，89 通过，2 跳过，0 失败，0 错误。
- 本轮新增 22 项回归用例（按参数化执行计数）：415/400、已删除用户的 GET/PUT 401、非法卡片/字段/dock、未来 schema、非对象与超限已存文档、GET/409 版本及元数据保留、JSONB 超出 Jackson 解析限制的兜底。原有严格写入及 6 项序列化回归全部保留并通过。
- `bash -n /tmp/wb2/verify-temp.sh` 及 4 段内嵌 Python compile 检查通过；核对原有具名检查和 trap 入口均保留。只做语法与静态复核，未执行验证脚本。git diff --check 以及未跟踪工作台文件的空白检查通过。
- 可执行产物：gak-start/target/gak-start-1.0.0-SNAPSHOT.jar。全仓库日志：/tmp/wb2/all-tests-package.log；相关模块日志：/tmp/wb2/workbench-tests.log。
- 已核对可执行 jar 中 4 个本轮修改的工作台生产类与编译输出逐字节一致，打包 schema 包含 ON DELETE CASCADE；jar SHA-256：`a6ef7132790f09f4a318aecd6756158aed2a70819679794dd8d22290f57bc216`。
- 迁移脚本 SHA-256：`a6478fa4b2eb1b5e80cf07f36bc4b71d2a453c3291840741cc2f5d71de740585`；权限脚本 SHA-256：`c6983282075dfe1cb618d12d47e268cc1f4a67acdde3a236cd9b40bcb90ce90b`。
- Morgan 侧执行器此前已在沙箱外做过临时 PG/后端验证并报告契约与注册问题；离线验证阶段，本代理未执行 verify-temp.sh、未连接任何数据库或 55432/8080/5173，新增 SQL 和 HTTP 端到端检查待 Morgan 侧执行器在沙箱外复验。此后 gak_dev 已由 gak_dev 角色执行建表迁移，未执行权限脚本。
- 已用 git status 确认指定计划文件为未跟踪后，仅删除 docs/superpowers/plans/2026-11-11-workbench-preferences.md；已跟踪的其他计划文件未改动。代码已本地提交，未推送、未部署。

## 修改与新增文件清单

- 修改：`gak-modules/pom.xml`
- 修改：`gak-start/pom.xml`
- 修改：`gak-start/src/main/resources/schema.sql`
- 新增：`docs/sql/20261111-user-preference-privileges.sql`
- 新增：`docs/sql/20261111-user-preference.sql`
- 新增：`docs/workbench-preferences-contract.md`
- 新增：`gak-modules/gak-workbench/pom.xml`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/controller/WorkbenchPreferenceBodyAdvice.java`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/controller/WorkbenchPreferenceController.java`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/controller/WorkbenchPreferenceExceptionHandler.java`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/domain/UserPreference.java`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/dto/WorkbenchPreferenceRequest.java`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/dto/WorkbenchPreferenceRequestDeserializer.java`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/exception/WorkbenchPreferenceConflictException.java`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/exception/WorkbenchPreferenceInvalidException.java`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/mapper/UserPreferenceMapper.java`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/persistence/JsonbTypeHandler.java`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/service/WorkbenchPayloadNormalizer.java`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/service/WorkbenchPreferenceService.java`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/serialization/WorkbenchPreferenceVersionSerializer.java`
- 新增：`gak-modules/gak-workbench/src/main/java/com/gak/workbench/vo/WorkbenchPreferenceVO.java`
- 新增：`gak-modules/gak-workbench/src/test/java/com/gak/workbench/controller/WorkbenchPreferenceBodyAdviceTest.java`
- 新增：`gak-modules/gak-workbench/src/test/java/com/gak/workbench/controller/WorkbenchPreferenceControllerTest.java`
- 新增：`gak-modules/gak-workbench/src/test/java/com/gak/workbench/persistence/JsonbTypeHandlerTest.java`
- 新增：`gak-modules/gak-workbench/src/test/java/com/gak/workbench/persistence/WorkbenchPreferencePostgresTest.java`
- 新增：`gak-modules/gak-workbench/src/test/java/com/gak/workbench/service/WorkbenchPayloadNormalizerTest.java`
- 新增：`gak-modules/gak-workbench/src/test/java/com/gak/workbench/service/WorkbenchPreferenceServiceTest.java`
- 新增：`gak-modules/gak-workbench/src/test/java/com/gak/workbench/vo/WorkbenchPreferenceVOTest.java`
- 新增（仓库外）：`/tmp/wb2/verify-temp.sh`
