# docs 目录导读

> 三层分工（对标 sf-auth）：**系统概览（速览）→ 设计文档（原理）→ 接入指南（对接操作）**。

| 层 | 文档 | 用途 |
|---|---|---|
| 速览 | [系统概览.md](系统概览.md) | 一页看懂：定位/端口/能力总览/**140 张表目录**/文档索引/验证基线 |
| 原理 | [架构设计.md](架构设计.md) | 分层/模块依赖/横切机制 |
| | [集团组织与数据权限设计.md](集团组织与数据权限设计.md) | 多租户/组织树/五档数据范围 |
| | [开发规范.md](开发规范.md) / [前端交互统一规范.md](前端交互统一规范.md) / [日志规范.md](日志规范.md) | 编码与交互唯一规范 |
| | modules/*.md（26 篇） | 每模块一篇：业务模型/接口清单/实现决策 |
| 对接 | modules/sso-authcenter.md | 统一认证中心接入（OIDC/SCIM 同步/JIT/用户入口收敛） |
| | modules/mes-integration.md | ERP↔MES 六类接口契约与联调 |
| | modules/reverse-docchain.md | 通用红冲/单据联查 |
| | modules/supplier-admit-rating.md | 供应商准入/评分算法 |
| | modules/demo-data.md | 演示数据（苏州精工）口径 |

运维速查：MySQL 容器 erp-mysql(3306, root/erp123456, 库 erp_db)；Redis erp-redis(6379, erp123456)；后端 8080 / 前端 5173；一键启动 ./script/sh/start-all.sh。
