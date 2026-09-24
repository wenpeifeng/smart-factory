# 设备管理模块（erp-equipment）

> 职责：设备台账、保养计划与到期预警、维修工单全流程（报修→接单→完工→关闭）、
> 维修备件领用出库、设备管理统计。对标金蝶 K3 设备管理。二期新模块，依赖
> `erp-framework` + `erp-basedata`（物料/仓库主数据），不依赖 inventory 代码。

## 1. 表结构（V931）

| 表 | 说明 |
|---|---|
| `eqp_device` | 设备台账。`uk_device_code`；`status`：1运行 2停机 3维修中 4报废（终态）；`original_value DECIMAL(18,2)`；车间为文本列 `workshop` |
| `eqp_maintain_plan` | 保养计划。`maintain_type`：1一级保养 2二级保养 3大修；`next_date = last_date + cycle_days`（服务端维护的不变量）；`idx_plan_next_date` 支撑到期查询 |
| `eqp_maintain_record` | 保养执行记录。`plan_id + 冗余 device_id`；`execute_time / executor / content` |
| `eqp_repair` | 维修工单（头）。`uk_repair_no`（WX+yyyyMMdd+4位流水）；`status`：0待维修 1维修中 2已修复 3已关闭（单向）；`warehouse_id` 为完工备件出库仓库 |
| `eqp_repair_item` | 工单备件明细（行）。`material_id → base_material.id`（无物理外键）；`cost_ref = qty × base_material.ref_cost`（完工时点快照，HALF_UP 2 位） |

种子数据：3 台设备（CK6140 数控车床 / VMC850 加工中心 / SA-22A 空压机）+ 1 条二级保养计划
（`next_date = CURDATE()+3`，落在 7 天预警窗口内，验证 `/plans/due`）。

## 2. 与库存模块的集成约定（重要）

本模块**不 import erp-inventory 代码**（依赖方向约束），按
[库存模块文档](./inventory.md) 的「过渡期兼容」约定直接操作两张约定表：

- **扣库存**：`EqpInvStockMapper.deductStock` —— 条件 UPDATE 行锁
  `UPDATE inv_stock SET qty=qty+?, version=version+1 WHERE ... AND qty+?>=0`，
  影响行数 0 一律按「库存不足」抛 `BusinessException`（行不存在与余额不足同语义）；
- **写流水**：`EqpInvMovementMapper.insert`（实体 `EqpInvMovement` 映射 `inv_movement`），
  `qty` 正入负出（出库为负数），与库存变更同一事务；
- **biz_type 自建常量** `com.erp.equipment.constant.EqpBizType`：
  - `EQP_OUT`：维修工单完工领用备件出库（当前使用）；
  - `EQP_IN`：备件退回入库（预留，当前无业务入口）。

  > 库存模块的 `InvBizType` 枚举（V401）只收录了 PUR/SAL/OTH/ADJUST/MOVE/PROD 十种，
  > 尚无设备类型。按并行开发约定本模块先落库字符串常量，**待主线程将 EQP_OUT/EQP_IN
  > 合入 `InvBizType` 枚举与 `inv_movement.biz_type` 列 COMMENT 后收口**。
- 完工事务边界：`EqpRepairServiceImpl.complete` 带
  `@Transactional(rollbackFor = Exception.class)`，明细行 INSERT + 扣库存 + EQP_OUT 流水
  任一步失败整体回滚，工单保持「维修中」。

## 3. 业务规则

### 3.1 设备状态流转（`PUT /equipment/devices/{id}/status`）

```
   运行(1) ←→ 停机(2) ←→ 维修中(3)      三态互通
        └────────┬────────┘
               报废(4)                    单向终态，任何非报废状态可进，进入后不可再变更
```

联动规则：工单**接单**时设备自动置「维修中」；**完工**时自动回「运行」；
保养**执行**时若设备为「维修中」也回「运行」。删除设备前校验无未完结工单。

### 3.2 保养计划

- `next_date` 只在创建/修改/执行三个入口计算，客户端不可提交；
- `GET /equipment/plans/due?days=7`：已到期 + 7 天内到期清单（含已逾期，按到期日升序，
  返回 `daysLeft` 负数=逾期天数 / `overdue` 标记），供 workflow 预警模块直查；
- `POST /equipment/plans/{id}/execute`：写 `eqp_maintain_record`（执行时间/执行人/内容，
  执行人留空记「系统」），计划顺延一个周期（`last=今天, next=今天+周期`），同一事务。

### 3.3 维修工单状态机（单向）

```
报修(POST /equipment/repairs) → 0 待维修
接单(PUT /{id}/accept {repairer}) → 1 维修中        设备联动置「维修中」
完工(PUT /{id}/complete) → 2 已修复                备件出库 + 设备联动回「运行」
关闭(PUT /{id}/close) → 3 已关闭                    归档
```

完工表单：`{ faultReason?, warehouseId?, lines: [{materialId, qty>0}] }`。
有备件行时 `warehouseId` 必填；物料/仓库经 basedata Service 校验存在性；
`cost_ref = qty × ref_cost` 快照入库。

### 3.4 统计口径（`GET /equipment/statistics`）

- 状态分布：四种状态全量返回（无设备补 0），前端饼图不出空扇区；
- 当月维修次数 TOP5 设备：按工单**报修时间**归月（报修即计次，含未完工）；
- 当月备件消耗成本：**完工工单**（`finish_time` 归月）的明细 `cost_ref` 合计。

## 4. REST API

前缀 `/api`（context-path），统一 `Result<T>` / `PageResult<T>`。

### 设备台账 `/equipment/devices`
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/equipment/devices` | 分页：`status` / `keyword`(编号/名称/型号) |
| GET | `/equipment/devices/{id}` | 详情 |
| POST | `/equipment/devices` | 新增（code 留空自动生成 `SB+yyyyMMdd+4位流水`） |
| PUT | `/equipment/devices/{id}` | 修改（编号/状态不走此接口） |
| DELETE | `/equipment/devices/{id}` | 逻辑删除（有未完结工单拒绝） |
| PUT | `/equipment/devices/{id}/status` | 状态流转 `{status}`（矩阵见 3.1） |

### 保养计划 `/equipment/plans`
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/equipment/plans` | 分页：`deviceId` / `dueOnly` |
| GET | `/equipment/plans/due` | 到期清单 `?days=7`（workflow 预警直查） |
| POST | `/equipment/plans` | 新增 `{deviceId, maintainType, cycleDays, lastDate?}` |
| PUT | `/equipment/plans/{id}` | 修改（重算 next） |
| DELETE | `/equipment/plans/{id}` | 逻辑删除（执行记录保留） |
| POST | `/equipment/plans/{id}/execute` | 执行一次保养（body 可空 `{executor?, content?}`） |

### 维修工单 `/equipment/repairs`
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/equipment/repairs` | 分页：`status` / `deviceId` / `keyword`(工单号/设备) |
| GET | `/equipment/repairs/{id}` | 详情（头 + 备件明细） |
| POST | `/equipment/repairs` | 报修 `{deviceId, faultDesc, reporter?, remark?}` → `{id}` |
| PUT | `/equipment/repairs/{id}/accept` | 接单 `{repairer}` |
| PUT | `/equipment/repairs/{id}/complete` | 完工 `{faultReason?, warehouseId?, lines}`（备件出库） |
| PUT | `/equipment/repairs/{id}/close` | 关闭 |

### 统计
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/equipment/statistics` | `{statusDist, monthRepairTop, monthSpareCost}` |

## 5. 前端

- 页面：`erp-web/src/pages/equipment/`
  - `devices/DeviceList.tsx`：台账（状态 Tag + 流转弹窗 + 新增/编辑 + 删除 + **报修入口**弹窗）
  - `maintains/MaintainPage.tsx`：Tab 结构
    - `RepairPanel.tsx`：工单列表（状态 Tag、备件成本列）+ 接单弹窗 + **完工弹窗（备件行动态增删 + 出库仓库）** + 关闭 + 详情 Drawer（含备件明细）
    - `PlanPanel.tsx`：计划列表（**到期高亮**：已逾期红 / 7 天内橙）+ 执行弹窗 + 新增/编辑 + 到期筛选
- API：`erp-web/src/api/equipment.ts`（类型与后端 VO 对齐；含 basedata 仓库/物料/设备下拉只读封装）
- 路由片段：`erp-web/src/router/modules/equipment.tsx`（`equipmentRoutes`，
  `equipment/devices` / `equipment/maintains`，与 MainLayout 菜单一致；由主线程合并进 router/index.tsx）

## 6. 关键实现位置

| 内容 | 文件 |
|---|---|
| 库存扣减原语（条件 UPDATE 防负库存） | `mapper/EqpInvStockMapper.java` + `resources/mapper/equipment/EqpInvStockMapper.xml` |
| EQP_OUT/EQP_IN 常量 | `constant/EqpBizType.java` |
| 工单全流程（事务边界在完工） | `service/impl/EqpRepairServiceImpl.java` |
| 计划顺延不变量 | `service/impl/EqpMaintainPlanServiceImpl.java` |
| 状态流转矩阵 | `service/impl/EqpDeviceServiceImpl.java` |
| 统计 SQL | `resources/mapper/equipment/EqpDeviceMapper.xml`、`EqpRepairMapper.xml` |
| 单号生成（SB/WX） | `support/EqpBizNoGenerator.java`（Redis INCR；类名带模块前缀，避免与 inventory 的 BizNoGenerator 在单体容器内 bean 名冲突） |
| 建表脚本 | `script/sql/migration/V931__equipment_init.sql` |

## 7. 遗留与协作事项

1. `EQP_OUT` / `EQP_IN` 待主线程合入 `InvBizType` 枚举（见第 2 节），并同步
   `inv_movement.biz_type` 列 COMMENT；设备模块后续可切换到 `StockService.changeStock`；
2. `equipmentRoutes` 需主线程合并进 `router/index.tsx`（MainLayout 菜单已就位）；
3. 责任人/报修人/维修人/执行人按 K3 习惯存文本，未关联 sys_user（equipment 不依赖
   erp-system，避免横向依赖）；如需人员选择器，后续由前端以字典/用户接口补齐。
