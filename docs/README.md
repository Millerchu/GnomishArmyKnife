# 后端文档索引

现行说明核对于 2026-10-15。先读仓库 [README](../README.md)，再读[架构](architecture.md)、[开发配置](development.md)。

| 文档 | 定位 |
| --- | --- |
| [NAS 部署](../deploy/nas/README.md) | 镜像、Compose、升级、备份 |
| [站内消息](../gak-modules/gak-message/README.md) | 消息、事务、SSE 和专项测试 |
| [行程备份](../gak-modules/gak-data-migration/docs/travel-record-backup.md) | 行程、关联账单与附件恢复 |
| sql/ | 增量 SQL，执行前核对目标库与适用范围 |
| frontend/、change-password-frontend-prompt.md | 历史跨端需求/提示词 |
| superpowers/specs/、superpowers/plans/ | 历史设计与实施计划 |
| ../doc/ | 早期专项设计、赛季资料，按日期阅读 |
| ../HELP.md | 框架参考链接，不是项目启动入口 |

历史设计可能包含未实现或已调整建议，不是完整现行接口规范。接口以 Controller、DTO/VO、Service 为准，启动以 POM、配置和脚本为准。保留历史计划原文，新功能完成后更新现行说明，避免把计划状态改写为已验收。
