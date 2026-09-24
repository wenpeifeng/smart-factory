# P2 执行核心模块设计（plan / execution / material / quality / integration）

> 2026-09-06 P2 完成。五模块后端 + mes-web 页面 + 权限种子（V17）+ 推送日志表（V801）全部落地，端到端联调通过。

## 1. plan 计划（V20x 表）
- **工单**：DRAFT→RELEASED→PRODUCING→FINISHED→CLOSED 状态机；下达事务内按 BOM 展开 `plan_wo_material`（需求=数量×单耗×(1+损耗)）、按工艺路线生成 `plan_wo_step`（快照工序名/工时/质检点）。`receiveFromErp` 按 source_no 幂等建单。
- **派工/过站累计**：`WoStepService.dispatch`（PENDING_DISPATCH→DISPATCHED）与 `accumulateStation`（累计合格/报废/返修，达标自动工序完工）。
- **齐套分析**：`KitCheckService` 需求(扣已领) vs 线边仓可用量逐料比对，落 `plan_kit_check(_item)` 快照；可用量 SPI `StockAvailabilityService`（暂直查 mat_shop_stock，material ShopStockService 就绪后改服务间调用）。
- **排程**：`plan_schedule` 时间片手动 upsert（不做自动 APS），同步回写工序计划时间。
- ERP 来源单号 `SC260906001` 测试流已验证。

## 2. execution 执行（V21x 表）
- **过站报工** `POST /execution/stations`：一事务内落 `exe_station_record`(GZ单号)→upsert `exe_wip`→回写工序/工单累计（工单自动转 PRODUCING）→PASS 回传 ERP（失败不阻断，重试任务补偿）。
- **WIP**：wip = input - output - scrap，工序维度汇总页。
- **返工/报废**：`exe_rework_scrap`（FG/BF 单号），OPEN→CLOSED 处理闭环；与质检处置单（qc_reject_id）关联。

## 3. material 车间物料（V40x 表）
- **领料**：DRAFT→AUDITED；审核事务=线边仓入库→回写工单已领（超需求回滚）→回传 ERP。
- **退料**：余料/退供应商；审核=线边仓扣减（不足报错）→回传 ERP。
- **线边仓** `mat_shop_stock`：物料+批次+工作中心维度；齐套分析数据源。
- **批次/SN 档案** `mat_batch_sn` + **标签打印日志** `mat_label_log`（P2 打印内容前端渲染，服务端留痕）。

## 4. quality 质量（V30x 表；SPC 表留 P3）
- **模板**：qc_template(_item) 共享表（TenantConfig 白名单），上下限+标准值。
- **检验单**：选模板带出项目行；录实测值自动判行结论（数值比上下限/目测按 OK·合格）；提交汇总 sample/qualified/unqualified+总结论并回传 ERP。
- **不合格处置**：qc_reject，处置累计 ≤ 检验单不合格数；决定=返工/让步/报废/退货。

## 5. integration 集成（见 modules/integration.md）
- 入站：订单/物料/BOM/工艺路线下推（X-Integration-Token 鉴权 + 幂等落库；BOM/路线下推即 ACTIVE）。
- 出站：完工报工/领料/退料/质检结果统一走 `ErpPushService`（int_push_log 幂等+5 分钟重试+对账页）。
- **注意**：工艺路线下推需带 `materialCode` 才能绑定物料（工单下达按物料取 ACTIVE 路线）。

## 6. 权限与缓存（重要）
- P2 权限码已种入 V17（含 P1 遗漏的 basedata）；**角色权限变更有 Redis 10 分钟缓存**（`auth:perms:{userId}`），调权后需 DEL 缓存或等待过期。
- 新增权限码必须同步 V1x 迁移脚本 + 前端 HasPerm code。

## 7. 已知待办（P3）
- StockAvailabilityService 改为调 material 服务（解耦直查表）；Bom/Routing 查询接口出参改 VO；
- 排程甘特页面、移动端 /m 报工页、trace 正反向追溯、SPC、equipment/kanban。
