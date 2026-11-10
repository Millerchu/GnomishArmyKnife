# 系统日志实施清单

依据用户已批准的 /workspace/gak/work/syslog-retention-traceid-plan.md 第 4 节及本轮明确边界，直接在当前会话实施，不提交 Git。

- [x] 采集接口返回服务端 traceId；GlobalExceptionHandler 仅为已采集且尚未提交的失败响应接头，保留 JSON。
- [x] 注册共享上海 Clock，采集时间使用 Clock；验证固定时钟、写失败与超时返回编号。
- [x] 新增默认关闭的 cleanup 配置与启动校验；独立事务 Purger（本地超时、双 int4 advisory 锁、函数调用）；任务分批限量、失败封闭、仅应用日志。
- [x] docs/sql 新增幂等 SECURITY DEFINER 函数，严格边界、30 天硬下限、非法参数拒绝、PUBLIC 撤权。
- [x] 补齐单测、Spring 上下文测试、临时 PG16 集成测试、单用户 PG 权限测试。
- [x] 更新契约、README、development；执行离线编译和相关测试，写 sl-tr-be1.md 验证记录。

验证入口：./mvnw -B -o -pl gak-modules/gak-system-log -am test；临时 PG 测试使用现有系统属性开关。仅连接测试自行启动的 PG，不触碰现有服务和数据库。

JDBC 集成已尝试运行但受套接字沙箱限制；详细结果与外部命令见 sl-tr-be1.md，不记为通过。

## Forge 审查 L1–L3 修复计划

沿用当前未提交实现，按用户指定方案在本会话逐项修复，不提交 Git。

- [x] L1：Sanitizer 腾标记空间时按换行回退；首行超长才按安全 UTF-16 边界截短。补完整行、代理对、首行超长测试，保留 30/31 帧和 4000/4001 字符边界。
- [x] L2：删除 Properties/application 的 lock-timeout 与 Mapper/Purger 的对应设置；保留 statement-timeout，调整配置和任务测试。集成测试继续验证 ACCESS EXCLUSIVE 下约 2s、55P03、不外抛；同步 SQL 注释、README、契约和 development。
- [x] L3：psql 脚本内开启 ON_ERROR_STOP，检查 app_role 存在，缺参立即非零退出，空角色在授权阶段失败回滚。SQL-only 测试仅执行 BEGIN 起的事务正文；新增自起临时 PG 的真实 psql 缺参、空参、正确授权测试。
- [x] 执行指定离线 Maven test、Python 测试和 diff 检查；尝试 JDBC 集成测试并记录实际结果，追加 sl-tr-be1.md 验证节。核对权限模块及禁止修改路径的内容哈希未变。

## search_path 加固实施计划

沿用用户明确指定的方案，在当前会话执行，不提交 Git；仅修改清理脚本、相关测试和文档。

- [x] SQL：固定 `search_path = pg_catalog, pg_temp`，保留显式 public 表引用，将时钟函数限定为 `pg_catalog.now()`；头部解释 public 不可信和 pg_temp 最后解析的原因。
- [x] Python：断言 `pg_proc.proconfig` 的实际路径；用有 public CREATE 权限的非属主应用角色创建带标记的 public.now() 及同名临时表，验证严格边界、删除结果和劫持对象未被调用，事务结束回滚测试对象及授权。
- [x] JDBC：在现有权限检查中断言相同 proconfig；README、后端契约及 development 同步安全约束。
- [x] 验证：运行指定 Python、离线 Maven test、git diff --check；尝试仅自起临时 PG 的清理 JDBC 测试，记录实际执行/跳过/受限结果，核对 52 个受保护文件内容，追加 sl-tr-be1.md。

## B1 表替换提权修复计划

沿用用户已明确的 B1/I-S3 设计在当前工作区实施，不提交或部署。

- [x] SQL：安装事务锁定目标表，核验执行者、应用角色及成员关系、清理角色属性和既有同签名函数属主；通过安装时读取的表属主 OID 生成固定函数体。支持默认表属主模式和推荐的独立 NOLOGIN 清理角色模式。
- [x] 守卫：函数第一条语句锁表；核验普通表、固化属主、模式 A 的 definer、全部触发器（含内部 RI）、规则、RLS/强制 RLS、继承子表，失败统一 42501；保留既有清理行为。
- [x] Python：真实 psql 安装两种模式，覆盖库 owner 替换表、属主添加普通/内部触发器及其他结构变体、边界/下限/排序、劫持、最小权限、幂等/模式迁移、安装拒绝及事务回滚；同步 SQL-only/JDBC 的函数安装辅助代码。
- [x] 文档：SQL 头部和 README、契约、development 说明两模式、前置条件、快照和回滚；验证后在 sl-tr-be1.md 追加实测结果。
- [x] 验证：指定 Python、离线 Maven test、git diff --check；如 socket 被禁则明确区分通过和未执行。比对受保护路径内容哈希。
