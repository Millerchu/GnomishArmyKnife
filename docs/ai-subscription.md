# 智订部署和验收

智订使用独立的 `gak-ai-subscription` 模块。六个预置工具均可手动记录账号、订阅、用量和支付。用户从工作台进入智订，或直接快速新增 AI 订阅、用量和支付。

关闭自动续费只更新 GAK 中的计划。固定周期留空扣费日时按起始日锚定推算。支付与退款保留历史，已确认的支付金额和覆盖期独立于当前订阅配置。退款现金流计在退款月，成本按原覆盖区间冲减。费用以原币展示，API 跨月桶、冲突观测和未提供的费用在汇总中标记。预算按所选币种的确认成本计算。

密码备忘录只保留引用，打开详情仍使用备忘录的权限和密码查看流程。用户可以显式关联或生成个人账单。解除关联保留原账单，重新为同一支付生成时复用原来源账单。续费、试用和预算提醒在授权用户进入工作台时检查，同一事件写入一次站内消息。

## 数据库升级

启动时允许初始化 SQL 的环境会应用 `gak-start/src/main/resources/schema.sql`。手工迁移的环境按下面顺序执行，随后发布后端及前端。脚本均可重复执行。

1. `docs/sql/20261118-ai-subscription-framework.sql`
2. `docs/sql/20261118-ai-subscription-business.sql`
3. `docs/sql/20261118-ai-subscription-sync.sql`
4. `docs/sql/20261118-ai-subscription-integrations.sql`
5. `docs/sql/20261119-ai-quota-snapshots.sql`

## 消费者套餐额度

在「套餐额度」选择账号后记录快照。ChatGPT 分别填写 5 小时和每周的剩余百分比，可逐项附上手动重置机会。Grok 可记录每周已用、额外额度状态和 Bot 用量，来源和 Bot 分组分别展示。Muse 可记录免费额度、额外额度、剩余数量和厂商单位。快照历史最多展示最近 50 条，同一窗口的已用与剩余各自展示变化。

采集时填厂商原始读数。只看到倒计时或重置日期时可直接填写提示，不需估算时分。提交相同账号和相同采集时间会更新该条快照。额度快照为手动录入，现有的 AI 订阅、用量和支付快捷入口仍用于原有记录。

## 官方同步

服务端环境变量 `GAK_AI_SYNC_MASTER_KEY` 接收 Base64 编码的 32 字节随机主密钥。各后端实例共用同一值。空值时仍可完整使用手动记录。用户在「订阅与账号」中为对应账户配置授权。

| 账号 | 入库范围 | 授权要求 |
| --- | --- | --- |
| ChatGPT | OpenAI 组织 API 费用，最近 35 个完整 UTC 日，按币种 | [OpenAI Costs API](https://platform.openai.com/docs/api-reference/usage/costs) 所要求的 Admin API Key |
| Grok | xAI 团队 API 费用，最近 35 个完整 UTC 日，美元 | [xAI Billing Management](https://docs.x.ai/developers/rest-api-reference/management/billing) 的团队 ID 与具有账单读取权限的 Management API Key |

同一个 OpenAI 组织应只配置一次。xAI 团队通过团队 ID 去重。消费者套餐额度继续手工录入。Gemini 的 [Cloud Billing 导出](https://docs.cloud.google.com/billing/docs/how-to/export-data-bigquery?hl=en) 需要另行配置账单数据集和访问。[QoderCN Usage APIs](https://docs.qoder.cn/en/enterprise/usage-api) 面向企业组织，需明确授权范围和额度归属。两者当前保持手动。Muse 产品身份待确认，Workbuddy 暂未接入官方同步。

用户进入工作台或恢复页面时检查，后端按连接时区为每个账号和本地日期复用任务。自动同步只处理由用户发起的检查所创建的任务。连接撤销、账号归档或鉴权失效会停止后续任务并清除有效密文，保留已导入记录。若需轮换主密钥，安排停机或维护窗口，让用户先撤销旧授权，统一切换实例配置，再重新授权。应用迁移包不包含智订数据或可用密钥。

## 验证范围

使用独立 PostgreSQL 实例验证初始化及增量脚本、真实登录、账号越权、累计覆盖、退款摊销、预算和站内提醒、账单解绑重连及历史保留。使用 Playwright 检查真实数据的工作台、总览趋势、390px 下四个分区和移动端快速新增。后端智订 30 条测试通过，覆盖计量、授权及同步任务。前端构建通过，173 条单元测试通过，组件测试 498 通过、1 跳过、2 条随身乐器断言失败（评审已记录）。OpenAI、xAI 的响应解析用样例验证，真实组织或团队凭据联调待完成。

当前费用估价由用户直接录入，暂未提供自定义费率、汇率维护、历史报价版本和额度阈值提醒。已确认的支付和用量历史独立保留。
