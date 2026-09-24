# 组织管理模块（erp-org，轻量多组织）

> 职责：**多工厂档案 + 仓库归属 + 组织间调拨**。定位是《集团组织与数据权限设计》中的
> 工厂层落地（L2 数据权限的前置数据模型），不引入多账套/法人层——完整集团版评估见 §7。
> 对标金蝶 K3「多工厂组织间调拨（工厂间转移）」的轻量实现。

## 1. 表结构（V961 / V962）

| 表 | 说明 |
|---|---|
| `org_factory` | 工厂档案。编码唯一（自动生成 `FC+yyyyMMdd+3位流水`）；种子 id=1 总厂（存量仓库统一归属，不可删/不可停用）。V961 同时幂等 `ALTER base_warehouse ADD factory_id` 并回填 `factory_id=1` |
| `org_transfer_price` | 调拨价目。定价线路 = 物料×调出工厂×调入工厂（`uk_transfer_price_line` 唯一）；`markup_type`：RATE 加价率%（10=10%）/ FIXED 固定加价额；状态启停，停用=无价目（按成本结算） |
| `org_transfer` | 调拨单头。单号 `IT+yyyyMMdd+3位流水`（Redis INCR）；状态机 `0草稿 → 1已审核 / 2已取消`（单向；已审核不可取消，红字冲销另立单）；调出/调入各为 工厂+仓库 对，仓库必须归属对应工厂 |
| `org_transfer_detail` | 调拨明细。`qty DECIMAL(18,4)`；三个价格快照列在**保存时**由服务端计算（见 §3），审核与统计以快照为准 |

跨模块约定表（只读 JOIN / 按约定列写入，不 import 对方代码、不建物理外键）：
`base_warehouse(+factory_id)`、`base_material`、`inv_stock`、`inv_movement`、`cost_flow`。

## 2. REST API

前缀 `/api`，统一 `Result<T>` / `PageResult<T>`。

### 工厂档案 `/org/factories`
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/org/factories` | 分页：`name`(名称/编码模糊) / `status`，带归属仓库数 |
| GET | `/org/factories/enabled` | 启用工厂下拉 |
| GET / POST / PUT / DELETE | `/org/factories/{id}` | 详情 / 新增（code 留空自动生成）/ 修改（code 不可改）/ 删除（总厂与有仓库归属的工厂拒删） |
| PUT | `/org/factories/{id}/status` | 启用/停用 |
| GET | `/org/factories/{id}/warehouses` | 工厂下仓库清单（JOIN base_warehouse 只读） |
| PUT | `/org/factories/assign-warehouse` | 仓库归属调整 `{warehouseId, factoryId}`（回写 base_warehouse.factory_id，目标工厂须启用） |

### 调拨价目 `/org/transfer-prices`
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/org/transfer-prices` | 分页：`materialId/fromFactoryId/toFactoryId/status`，带物料/工厂名称回显 |
| POST / PUT / DELETE | `/org/transfer-prices/{id}` | 新增（线路唯一，重复报业务错）/ 修改 / 删除 |
| PUT | `/org/transfer-prices/{id}/status` | 启用/停用 |

> 价目目前为 API 级管理（前端两页面按需求交付为工厂/调拨单，价目维护走接口或后续补页面）。

### 组织间调拨 `/org/transfers`
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/org/transfers` | 分页：`status/fromFactoryId/toFactoryId/keyword`(单号/工厂名)/`beginDate/endDate` |
| GET | `/org/transfers/{id}` | 详情（头 + 明细主子表，带物料回显与价格快照） |
| POST | `/org/transfers/preview` | 明细价格预演（保存前实时展示成本/结算价列，不落库） |
| POST | `/org/transfers` | 新增草稿：`{fromFactoryId, fromWarehouseId, toFactoryId, toWarehouseId, remark, lines:[{materialId, qty>0}]}`；明细行自动按价目算结算价 |
| PUT | `/org/transfers/{id}` | 修改（仅草稿；明细整体重算重存） |
| POST | `/org/transfers/{id}/audit` | 审核过账（见 §3） |
| POST | `/org/transfers/{id}/cancel` | 取消（仅草稿） |

### 统计 `/org/statistics`
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/org/statistics/stock?factoryId` | 工厂下各仓库库存量汇总（JOIN inv_stock×base_warehouse 只读聚合，含零库存仓库） |
| GET | `/org/statistics/transfer?from&to` | 期间（按审核时间闭区间）已审核调拨按线路汇总：单数/数量/结算金额 |

## 3. 核心机制

### 3.1 价格快照（保存时计算）
- **成本单价** = 调出方最近加权价：`cost_flow` 该物料**最新一行**运行余额
  `balance_cost / balance_qty`（6 位 HALF_UP）。表不存在（cost 模块 V801 未执行）或
  无数据/结存为 0 时**降级为 0**，不阻断录入——此时结算金额按 0 结算，须先在
  成本模块过账/重算后再保存调拨单以获得正确成本。
- **内部结算单价**：RATE = `成本×(1+加价率%)`；FIXED = `成本+固定额`；**无启用价目 = 成本**。
- **结算金额** = 结算单价×数量（2 位 HALF_UP）。
- 快照落在 `org_transfer_detail`，草稿重新保存即重算；审核按快照过账。

### 3.2 审核过账（一个事务，任一步失败整体回滚）
每行明细执行：
1. 调出仓扣库存：`UPDATE inv_stock SET qty=qty-?, version=version+1
   WHERE warehouse_id=? AND material_id=? AND batch_no='' AND deleted=0 AND qty-?>=0`
   —— 影响行数 0 一律抛 `BusinessException("库存不足…")` 回滚（行不存在与余不足同语义，防负库存）；
2. 写 `inv_movement`：`ORG_OUT`，qty 为**负**；
3. 调入仓加库存：常规 UPDATE，0 行时 `INSERT…ON DUPLICATE KEY UPDATE` UPSERT 兜底
   （不依赖 uk_stock 具体列结构，兼容一期三列/二期批次四列唯一键）；
4. 写 `inv_movement`：`ORG_IN`，qty 为**正**；
5. 全部行成功后条件更新单头 `status 0→1`（`WHERE status=0` 兜底并发审核冲突，0 行同样回滚全部过账）。
   流水 `biz_id`=调拨单 id、`biz_no`/`remark` 记 IT 单号，qty 遵循全系统「正入负出」约定。

### 3.3 与库存模块的边界（重要）
- 组织间调拨按 **batch_no='' 汇总行** 过账（与采购/销售裸 SQL 同口径）。
  **批次物料的组织间调拨不支持**：调出会因无汇总行按「库存不足」拒绝，属安全失败；
  批次物料请先在库存模块做批次出库/入库，或等待 StockService 批次接口接入本模块。
- `inv_movement.biz_type` 新增约定值 `ORG_OUT/ORG_IN`（VARCHAR 列，无需改表）。
  库存流水页（inventory 前端）的 BIZ_TYPE_MAP 未包含这两个值，会显示英文原值——
  属库存模块页面的小补充，由主线程/库存任务顺手补映射即可。

## 4. 前端

- 页面：`erp-web/src/pages/org/` 下 `factories/FactoryList.tsx`
  （PageTable 列表 + 新增/修改/启停/删除 + 「查看仓库」弹窗＝仓库清单×库存量汇总 + 仓库归属调整弹窗）、
  `transfers/TransferList.tsx`（调拨单 PageTable + 线路金额汇总卡(RangePicker) +
  新建/编辑 Modal（工厂→仓库级联下拉 + 物料明细行 + **自动结算价列**（调 `/preview` 实时算））+
  审核 Popconfirm + 取消 Popconfirm + 详情弹窗）
- API：`erp-web/src/api/org.ts`；路由片段：`erp-web/src/router/modules/org.tsx`
  （`orgRoutes`：`org/factories`、`org/transfers`，主线程合并）
- 列表页统一 `PageTable + PageHeader + StatusTag` 公共组件；菜单挂在 MainLayout「组织管理」。

## 5. 关键实现位置

| 内容 | 文件 |
|---|---|
| 幂等 ALTER + 种子 + 三张表 | `script/sql/migration/V961__org_factory_init.sql`、`V962__org_transfer_init.sql` |
| 库存过账原语（防负库存/UPSERT） | `mapper/OrgInvStockMapper.java` + `resources/mapper/org/OrgInvStockMapper.xml` |
| 审核事务/价格快照/成本降级 | `service/impl/OrgTransferServiceImpl.java` |
| 成本单价（cost_flow 只读） | `mapper/OrgCostFlowMapper.java` + 同名 XML |
| 单号生成（FC/IT，3位流水） | `support/OrgBizNoGenerator.java` |

## 6. 已验证的闭环（2026-09-05）

建苏州/东莞两厂并各挂仓库 → 价目 RATE 10% → cost_flow 成本单价 5 →
调拨单 10 个 → 审核 → 调出仓 110→100（-10）/调入仓 0→10（+10）/
`inv_movement` ORG_OUT(-10)+ORG_IN(+10) 两行（remark 记 IT20260905001）/内部结算 55.00；
超额/多行部分不足审核被「库存不足」拒绝且 0 流水残留、库存不变；草稿改/取消、
已审核禁改禁取消、总厂/有仓工厂禁删、ALTER 幂等重放均验证通过。
（测试遗留数据：苏州/东莞两工厂、价目 1 条、IT 单 3 张、cost_flow 一行 IT-FIXTURE 成本铺垫。）

## 7. 多组织改造评估一页（S4：从轻量多工厂到完整集团版）

> 关联《集团组织与数据权限设计》S1–S3（org_org 树/数据权限拦截器/字段权限由主线程落地）。
> 本模块已交付的 `org_factory + base_warehouse.factory_id + 组织间调拨` 是 S4 的最小内核；
> 以下为补齐「全表 org_id / 权限隔离 / 内部往来 / 合并报表」的工作量与影响面评估。

### 7.1 全表 org_id 迁移（工作量：大，约 3~4 周；风险：高）

| 项 | 内容 |
|---|---|
| 范围 | `pur_*`(8) `sal_*`(8) `inv_stock/inv_movement`(2+批次) `fin_*`(10) `mrp_*`(12+) `qc/asset/hr/equipment/invoice`(~25) 共 **60+ 张表** `ADD COLUMN org_id BIGINT NOT NULL DEFAULT 1`；唯一键追加 org_id 重建（uk_stock/uk_warehouse_code/单号 uk 等 ~20 处） |
| 数据回填 | 存量全部归 org=1（总厂）；按 warehouse_id→factory_id→org 分仓拆分可选（一次性脚本，需停机窗口） |
| 写入侧 | MetaObjectHandler 自动填充 org_id（框架改 1 处）；各模块 Service 约 10% 显式传 org（跨组织单据：本模块调拨、内部往来） |
| 读取侧 | DataScopeInnerInterceptor（S2）注入行级条件，业务代码零改动；**唯独本模块的组织间调拨需要 `@DataScope(ignore)` 跨组织读写**（调出/调入两厂），是全系统唯一的越权白名单点 |

### 7.2 权限隔离（工作量：中，约 1~2 周，随 S2 免费获得大半）

- 用户绑定 org（sys_user.org_id + 组织切换器，S1 范围）后，本模块页面天然按登录组织过滤工厂/仓库/调拨单；
- 需新增的模块内校验：工厂/仓库选择器的组织可见性（约 3 个查询加 org 条件）；
  调拨单「调出工厂与调入工厂不能相同」升级为「须均在授权组织清单内」；
- 风险点：超管/集团管控角色跨组织报表与 组织间调拨审核权 的授权模型（建议新增 `org:transfer:cross` 按钮权限点）。

### 7.3 内部往来（工作量：大，约 3~4 周；与财务耦合最深）

现状：内部结算价/金额已快照在 `org_transfer_detail`，但**只算账不记账**。补齐需：
1. 调拨审核时自动生成两厂间的「内部往来」单据（内部应收/内部应付各一行，类似 fin_payable/receivable 的镜像对；
2. 期末内部对账：按线路汇总（本模块 `/org/statistics/transfer` 已给出雏形）与双方挂账核对，差异清单；
3. 与总账集成：内部交易双方各生成凭证（借：内部应收 贷：收入 / 借：成本 贷：内部应付），
   结算中心模式（收付款单核销内部往来）可选；
4. 影响面：erp-finance（凭证模板/科目挂载）、本模块（审核事务扩容，调拨+往来+凭证三表联动，需引入幂等防重复挂账）。

### 7.4 合并报表（工作量：大，约 4~6 周；独立子系统）

1. 抵销规则引擎：内部交易抵销（§7.3 的往来+未实现利润：调入方期末存货中的内部加价 × 未售出比例）、内部投资/债权债务抵销；
2. 合并工作底稿：分组织试算平衡表 → 抵销分录 → 合并资产负债表/利润表（fin_voucher 体系加「合并账套」维度或独立报表账套）；
3. 组织架构升级：`org_factory` 平移挂入 `org_org` 树（COMPANY/FACTORY 层级已预留设计），合并范围按树勾选；
4. 前端：合并报表工作台（范围选择→数据采集→抵销分录维护→差异定位），工程量约等于一个中型报表模块；
5. 影响面：finance（账套/期间概念扩展）、cost（组织间成本还原）、report（现有报表加 org 维度钻取）。

### 7.5 结论与建议排期

| 阶段 | 内容 | 工作量 | 依赖 |
|---|---|---|---|
| S4.1 | 全表 org_id + 权限隔离闭环 | 4~5 周 | S1/S2 完成 |
| S4.2 | 内部往来自动挂账 + 期末对账 | 3~4 周 | S4.1 |
| S4.3 | 合并报表（抵销+底稿+工作台） | 4~6 周 | S4.2 |

轻量多组织（本模块）已覆盖 80% 的「多工厂实物协同」场景；S4 的增量价值主要在
**法人级财务**（多账套/内部往来/合并）。建议 S4 单独立项，且 S4.1 与 S4.2 可并行设计、串行落地。
