# P4 呈现层设计（report 模块 / 大屏 / 驾驶舱 / 报表中心 / demo-data）

> 2026-09-06 P4 完成。表：无新业务表（纯只读聚合）；权限种子 V19；演示数据 V1201。

## 1. report 模块（mes-biz-report）
- `StatsMapper` 集中维护跨模块只读聚合口径（工单/过站/质检/安灯/推送），写操作仍归各业务模块。
- 接口：`/report/screen`（大屏）、`/report/cockpit`（驾驶舱）、工单达成/报工明细/质量统计（JSON + CSV 导出，BOM 头 + 逗号转义）。
- CSV 导出为匿名直链（与 basedata 导出同现状，前端 a 标签不带 token），生产化改造时统一换签名 URL。

## 2. 车间电子看板大屏（/kanban/screen）
- 深色大屏（电视墙）：未关闭安灯 + 7 天产量面积图 + 工单进度榜 + 工序 WIP Top + 状态分布 + 质量汇总，30 秒轮询。

## 3. 管理驾驶舱（/cockpit）
- 指标卡（工单总数/生产中/缺料工单/未关闭安灯/回传积压）+ 14 天产量趋势 + 状态分布 + IQC/IPQC/FQC 合格率，60 秒轮询。

## 4. 报表中心（/report/*）
- 工单达成（达成率）、报工明细（近 7 天/导出 30 天）、质量统计（按物料合格率），均支持 CSV 导出。

## 5. demo-data（V1201）
- 演示角色：demo_operator（报工/WIP/安灯）、demo_qc（检验/处置/SPC 查看）、demo_wh（领退料/线边仓/批次SN）。
- 演示账号：operator / qc / wh，密码统一 `demo123456`（BCrypt；stub 阶段本地登录，auth-center 接入后由其管理身份）。
- 已验证权限边界：operator 可报工但访问驾驶舱 403；角色权限码按 V17/V18 组装。

## 6. 数据库一览（多系统边界）
- 三系统三库：mes_db（mes-mysql:3307）/ erp_db（erp-mysql:3306）/ auth_db（auth-mysql:13306）。
- MES 自有 RBAC：sys_user/sys_role/sys_menu/sys_role_menu/sys_user_role(+sys_user_org/sys_dept/sys_tenant)；
  auth-center 只管身份与进门权（AGENTS 决策 2），菜单/按钮/数据权限在 MES 库内维护。
