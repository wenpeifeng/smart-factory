# P3 深化模块设计（trace / SPC / equipment / kanban / 甘特 / 移动端）

> 2026-09-06 P3 完成。表：trace V501（已有）、SPC V311（已有）、equipment V601、kanban V701；权限种子 V18。

## 1. trace 追溯
- **事件驱动采集**：`TraceEvents`（framework 层中立契约）由领料审核（PickingAudited）与过站报工（StationReported，SN+关键件SN）发布，trace 模块 `@TransactionalEventListener(AFTER_COMMIT)` 落 `trace_link`——上游事务成功才写链路，且 material/execution 不依赖 trace。
- **正反向**：`GET /trace/links/forward?no=来料批次或SN` → 下游产品；`backward?no=产品SN/工单号` → 来料批次/关键件（工单号自动补 `WO:` 前缀）。
- **电子批记录 EBR**：`POST /trace/ebrs/workorder/{id}` 聚合工单+工序+过站+领料+检验为 JSON 快照（uk_ebr_workorder 一单一份，只读）。

## 2. SPC（quality 模块）
- 规则：物料 x 检验项目唯一（UCL/LCL/target + 准则开关，R4 默认停用）。
- 采样：检验单提交自动匹配规则采数值型实检值（失败不影响提交）。
- 判异：R1 超限 / R2 连续9点同侧 / R3 连续6点趋势 / R5 3点中2点同侧B区外（+R4 可选），命中写样本 violations。
- 图表：`GET /quality/spc/rules/{id}/chart`，前端 Recharts 控制图（UCL/LCL/CL 参考线）。
- 坑：BigDecimal.divide 必须带 scale，否则非整除抛异常。

## 3. equipment 设备
- 台账（SB 单号）：RUNNING/IDLE/DOWN/REPAIR 状态流转。
- 点检（DJ）：NG 自动置设备 REPAIR；保养/维修（BY）：完成自动恢复 IDLE。
- OEE 实时聚合（不落表）：`GET /equipment/oee`，OEE=时间开动率x性能x合格率；负荷=工作中心日产能工时x天数（接入排班日历后可精确到班次）；理论工时=过站合格数x工序单件工时。

## 4. kanban 安灯
- `kb_andon`：OPEN→RESPOND→CLOSED；类型 设备/物料/质量/其他；前端 30 秒轮询。大屏/工作台入口随 P4 呈现层强化。

## 5. 排程甘特 + 移动端
- `/plan/schedules` 甘特页：工作中心时间轴 + 时间条（拖拽编辑留 P4）。
- `/m/scan` 移动报工页：扫/选工单 → 选已派工工序 → 录合格/报废/返修 → 提交（复用 PC 端过站接口）。

## 6. P2 遗留清理（本迭代完成）
- 齐套可用量：material 模块 `ShopStockAvailabilityAdapter` 实现 plan 的 SPI（消除 plan 直查 mat_shop_stock）。
- Bom/Routing 查询接口出参 VO 化（BaseBomVO/BaseRoutingVO）。
