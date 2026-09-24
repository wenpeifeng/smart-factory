# 库存管理模块（erp-inventory）

> 职责：全系统的**库存守门人**。拥有 `inv_stock`（即时库存）与 `inv_movement`（库存流水）两张核心表，
> 对外暴露 `StockService` 作为**全系统唯一库存入口**；同时提供其他出入库、仓库调拨、库存盘点、
> 即时库存/预警/流水查询能力。对标金蝶 K3 库存管理。

## 1. 表结构（V401）

> 二期 V411 起表结构有批次维度扩展（inv_stock 唯一键加 batch_no 等），见文末「二期扩展」章节；
> 一期调用方语义不变。

| 表 | 说明 |
|---|---|
| `inv_stock` | 即时库存（仓库+物料维度；V411 起为仓库+物料+批次）。`uk_stock(warehouse_id, material_id, deleted)`（V411 起含 batch_no）一仓一料一行；`qty DECIMAL(18,4)` 非负；`version` 每次变动 +1 |
| `inv_movement` | 库存流水。`biz_type` 十种业务类型；`qty` **正入负出**；`biz_id/biz_no` 溯源单据；索引 `(material_id, warehouse_id, create_time)`、`(biz_type, biz_id)`、`biz_no`、`create_time` |
| `inv_other_in` / `inv_other_in_line` | 其他入库单（头/行），状态 0 草稿 → 1 已审核 |
| `inv_other_out` / `inv_other_out_line` | 其他出库单（头/行），状态 0 草稿 → 1 已审核 |
| `inv_check` / `inv_check_detail` | 盘点单（头/行）。明细 = 生成时的库存快照（book_qty）+ 实盘（actual_qty）+ 差异（diff_qty） |

业务类型枚举 `com.erp.inventory.constant.InvBizType`：
`PUR_IN / PUR_RET / SAL_OUT / SAL_RET / OTH_IN / OTH_OUT / ADJUST / MOVE / PROD_IN / PROD_USE`

与基础资料的边界：仅 LEFT JOIN `base_warehouse(id,code,name)`、`base_material(id,code,name,safety_stock)`，
不 import basedata 代码、不建物理外键。

## 2. StockService —— 全系统唯一库存入口（跨模块 SPI）

**采购/销售/生产模块请停止裸 SQL 改库存，改为直接 `@Autowired StockService`（同 JVM 单体）。**

```java
package com.erp.inventory.service;

public interface StockService {

    /**
     * 库存变动（唯一过账入口）。
     * qty：正数=入库，负数=出库；出库超余量抛 BusinessException("库存不足")，整体回滚。
     * 行为：行锁 UPSERT inv_stock（条件 UPDATE ... AND qty+?>=0 防负库存；
     * 首次入库 INSERT ... ON DUPLICATE KEY UPDATE 兜底并发）+ INSERT inv_movement，同一事务。
     */
    void changeStock(Long warehouseId, Long materialId, BigDecimal qty,
                     InvBizType bizType, Long bizId, String bizNo);

    /** 同上，可带流水备注（inv_movement.remark） */
    void changeStock(Long warehouseId, Long materialId, BigDecimal qty,
                     InvBizType bizType, Long bizId, String bizNo, String remark);

    /** 仓库调拨：调出仓出库 + 调入仓入库，两条 MOVE 流水共用同一 bizNo，同一事务 */
    void transfer(Long fromWarehouseId, Long toWarehouseId, Long materialId,
                  BigDecimal qty, String bizNo);
}
```

### 各模块调用示例

```java
@Autowired private StockService stockService;

// 采购入库单审核时（采购模块）
stockService.changeStock(order.getWarehouseId(), line.getMaterialId(), line.getQty(),
        InvBizType.PUR_IN, order.getId(), order.getNo());

// 销售出库（负数）
stockService.changeStock(warehouseId, materialId, line.getQty().negate(),
        InvBizType.SAL_OUT, deliver.getId(), deliver.getNo());

// 生产领料 / 生产入库
stockService.changeStock(whId, materialId, qty.negate(), InvBizType.PROD_USE, workOrder.getId(), workOrder.getNo());
stockService.changeStock(whId, materialId, qty,        InvBizType.PROD_IN,  workOrder.getId(), workOrder.getNo());
```

### 并发与一致性约定（不变量）

1. **库存永不透支**：扣减走条件 UPDATE（行锁）`qty = qty + ? WHERE ... AND qty + ? >= 0`，
   影响行数 0 一律按"库存不足"抛异常回滚（行不存在与余额不足同语义）；
2. **每次变动必有流水**：movement 与 stock 变更同事务，失败同回滚；
3. 单号生成：Redis INCR，`前缀+yyyyMMdd+4位流水`（OI 其他入库 / OO 其他出库 / CK 盘点 / TR 调拨）。

> 过渡期兼容：采购/销售模块如仍直接 INSERT inv_movement / UPDATE inv_stock，请保持
> 「qty 正入负出、必须同时写两表、扣减带 qty+?>=0 条件」的约定，并尽快切换到 StockService。

## 3. REST API

前缀 `/api`（context-path），统一 `Result<T>` / `PageResult<T>`。

### 查询
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/inventory/stocks` | 即时库存分页：`warehouseId` / `keyword`(物料编码名称) / `zero`(all\|hide\|only) / `warningOnly` |
| GET | `/inventory/warnings` | 低于 `base_material.safety_stock` 清单（物料为主，无库存按 0 计，含 shortage 缺口） |
| GET | `/inventory/movements` | 流水分页：`bizType` / `beginDate` / `endDate` / `materialId` / `warehouseId` / `keyword`(单号/物料) |

### 其他入库 / 其他出库（结构对称）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/inventory/other-ins`、`/inventory/other-outs` | 分页：`status` / `warehouseId` / `keyword`(单号) |
| GET | `/inventory/other-ins/{id}`、`/inventory/other-outs/{id}` | 详情（头+行） |
| POST | `/inventory/other-ins`、`/inventory/other-outs` | 新建草稿：`{warehouseId, reason, remark, lines:[{materialId, qty>0, remark}]}` |
| POST | `/inventory/other-ins/{id}/audit`、`/inventory/other-outs/{id}/audit` | 审核过账（OTH_IN/OTH_OUT 流水；出库不足整体回滚） |
| DELETE | `/inventory/other-ins/{id}`、`/inventory/other-outs/{id}` | 删除（仅草稿） |

### 调拨与盘点
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/inventory/transfers` | 即时调拨：`{fromWarehouseId, toWarehouseId, remark, lines:[{materialId, qty>0}]}` → `{transferNo}` |
| GET | `/inventory/checks` | 盘点单分页：`status` / `warehouseId` / `keyword` |
| GET | `/inventory/checks/{id}` | 详情（头+快照明细） |
| POST | `/inventory/checks` | 创建盘点（生成库存快照）：`{warehouseId, remark}` |
| PUT | `/inventory/checks/{id}` | 保存实盘：`{lines:[{detailId, actualQty>=0}]}`（可分批） |
| POST | `/inventory/checks/{id}/complete` | 完成盘点：差异行生成 ADJUST 流水调平 → `{adjusted}` |
| DELETE | `/inventory/checks/{id}` | 删除（仅盘点中） |

## 4. 前端

- 页面：`erp-web/src/pages/inventory/` 下 `stock/StockList.tsx`（即时库存：聚合筛选+预警行红色高亮+顶部预警汇总条）、
  `move/MovePage.tsx`（Tab：仓库调拨 / 其他入库单 / 其他出库单）、`check/CheckList.tsx`（盘点：快照→录实盘→差异调平）
- API：`erp-web/src/api/inventory.ts`
- 路由片段：`erp-web/src/router/modules/inventory.tsx`（`inventoryRoutes`，由主线程合并）
- 仓库/物料下拉取自 basedata 只读接口 `/basedata/warehouses/enabled`、`/basedata/materials/enabled`

## 5. 关键实现位置

| 内容 | 文件 |
|---|---|
| 行锁 UPSERT 三原语 | `mapper/InvStockMapper.java` + `resources/mapper/inventory/InvStockMapper.xml` |
| 库存守门逻辑 | `service/impl/StockServiceImpl.java` |
| 盘点快照 | `resources/mapper/inventory/InvCheckDetailMapper.xml`（INSERT...SELECT） |
| 单号生成 | `support/BizNoGenerator.java`（Redis INCR） |

---

# 二期扩展：批次追溯 + 移动扫码作业（V411）

> 交付内容：批次维度库存（inv_stock 加 batch_no/first_in_at，唯一键升维）、
> FIFO/指定批次/总量三种出库策略、批次正反溯查询、批次余额清单、
> 移动端 H5 扫码作业页（收货/拣货/盘点）。

## 6. 表结构变更（V411，幂等迁移）

| 表 | 变更 |
|---|---|
| `inv_stock` | + `batch_no VARCHAR(50) NOT NULL DEFAULT ''`；+ `first_in_at DATETIME`（批次行首插时写入，续入不回写，FIFO 排序键）；唯一键 `uk_stock(warehouse_id, material_id, deleted)` → **`uk_stock(warehouse_id, material_id, batch_no, deleted)`**（旧数据 batch_no=''，新键对旧数据等价，无需回填） |
| `inv_movement` | + `batch_no VARCHAR(50) NOT NULL DEFAULT ''`（FIFO 拆批出库时一批次一行流水；一期历史流水/裸 SQL 流水为空串） |
| `inv_other_in_line` / `inv_other_out_line` | + `batch_no`（入库行批次物料审核时必填；出库行传值=指定批次、空=FIFO） |
| `inv_check_detail` | + `batch_no`；`uk_check_material` 升维为 `(check_id, material_id, batch_no, deleted)`（快照按批次行抄库，批次物料一仓一批次一行） |

迁移脚本：`script/sql/migration/V411__inventory_batch.sql`（全部走 information_schema 判断 + PREPARE 动态执行，重复执行无害）。

## 7. StockService 二期签名（追加，一期签名不变）

```java
public interface StockService {
    // ---- 一期（不变）：changeStock / transfer 恒操作 batch_no='' 汇总行 ----

    /** 批次入库：批次物料 batchNo 必填（空抛异常）；非批次物料强制 ''（传了也忽略） */
    void stockIn(Long warehouseId, Long materialId, String batchNo, BigDecimal qty,
                 InvBizType bizType, Long bizId, String bizNo);
    void stockIn(..., String remark); // 备注重载

    /** 策略出库（qty 按正数收），返回实际扣减明细（FIFO 跨批=多元素） */
    List<DeductionLeg> stockOut(Long warehouseId, Long materialId, BigDecimal qty,
                                OutStrategy strategy, String batchNo,
                                InvBizType bizType, Long bizId, String bizNo, String remark);

    record DeductionLeg(String batchNo, BigDecimal qty) {}
}
```

出库策略 `com.erp.inventory.constant.OutStrategy`（仅对 batch_managed=1 物料生效；非批次物料一律走汇总行原逻辑）：

| 策略 | 语义 |
|---|---|
| `FIFO` | 按 `first_in_at` 升序（id 兜底）`SELECT ... FOR UPDATE` 锁行后逐批扣减，跨批自动拆多行 movement（同 bizNo），总量不足整体回滚 |
| `BATCH` | 只扣指定 batchNo 批次行，不足抛 `BusinessException("指定批次库存不足...")` |
| `AVG` | 总量扣减（不按入库时间排序，按 id 序扣足），流水仍逐批记录（移动平均核算口径） |

并发：FIFO/AVG 先锁定候选批次行再拆分，两笔并发出库不会互相抢批次；扣减原语仍是条件 UPDATE（`qty+delta>=0`）。

## 8. 跨模块兼容性约定（重要）

1. **非批次物料完全兼容**：一期采购/销售裸 SQL 的
   `UPDATE inv_stock SET qty=qty+? WHERE warehouse_id+material_id`、
   `INSERT ... ON DUPLICATE KEY` 与 mrp 的 SUM 聚合，在非批次物料上语义不变
   （batch_no='' 行为唯一汇总行）。
2. **本模块一期三原语已显式钉死 `batch_no=''`**（increaseStock/upsertIncrease/deductStock），
   `changeStock`/`transfer` 一期签名永远只动汇总行。
3. **批次物料禁止裸 SQL**：批次物料库存分散在多个批次行，裸 SQL 会多行命中
   （sales/mrp 的 deductStock）或查行报 TooManyResults（purchase 的单行 select）。
   批次物料的出入库必须走 `stockIn/stockOut`（按物料 batch_managed 强校验/分流）。
4. 其他出入库单/盘点单已批次化：other-in 行可带 batchNo（审核时批次物料必填）、
   other-out 行可带 batchNo（指定批次）或空（FIFO 自动分配）、盘点快照/调平按批次行。

## 9. 二期 REST API（追加）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/inventory/batches?materialId&warehouseId?` | 批次余额清单（batch_no≠'' 的行；含 qty/first_in_at/库龄天；按首入时间升序=FIFO 顺序） |
| GET | `/inventory/trace/forward?materialId&batchNo` | 批次正溯：该批次出库流水（qty<0，去向单号）按时间正序 |
| GET | `/inventory/trace/backward?materialId&batchNo` | 批次反溯：该批次入库流水（qty>0，来源单号）按时间正序；只读 inv_movement 的 biz_type/biz_no，不 JOIN 采购/销售实体 |

既有接口的增量字段：`/inventory/stocks` 行含 `batchNo`（批次物料一仓一批次一行）；
`/inventory/movements` 行含 `batchNo`；other-ins/other-outs 与 checks 明细行含 `batchNo`。

## 10. 二期前端

- 桌面端批次页：`erp-web/src/pages/inventory/batch/BatchPage.tsx`（路由 `inventory/batch`，
  菜单「库存管理-批次追溯」）——选物料/仓库 → 批次余额表（数量/首入/库龄，库龄分色）→
  点「追溯」抽屉展示 反溯来源 / 正溯去向 两栏流水。
- 移动端 H5（`erp-web/src/mobile/`，桌面布局外独立轻页面，AntD 5 小屏适配 + Tailwind，零新依赖）：
  - `/m/login`：简化登录（复用 POST /auth/login 与桌面同一份 zustand 登录态）
  - `/m/scan?type=receive|pick|check`：扫码作业（核心=手工输入+查询；摄像头 getUserMedia +
    BarcodeDetector 为可选增强，不支持自动降级为纯预览）
    - receive 收货：输/扫物料 → 录批次+数量 → 提交**其他入库快速单**（创建+审核一次过账）
    - pick 拣货：输/扫物料 → 批次 FIFO 建议列表（点行=指定批次并带入数量，留空=FIFO 自动）→ 提交**其他出库单**（创建+审核）
    - check 盘点：选仓库生成快照 → 逐行录实盘（可分批保存）→ 完成盘点差异调平
  - 移动端**只复用既有接口**（other-in/other-out/checks/stocks/batches），未新增后端接口
  - 路由片段 `erp-web/src/router/modules/mobile.tsx` 只导出 `mobileRoutes`（`m/login`、`m/scan`），
    由主线程挂载到主布局之外（无侧边栏）
- API 追加：`erp-web/src/api/inventory.ts`（BatchRow/TraceNode/OutStrategy 类型 +
  fetchBatches/fetchForwardTrace/fetchBackwardTrace；出入库 payload 行增加 batchNo）

## 11. 二期关键实现位置

| 内容 | 文件 |
|---|---|
| 批次原语（增/UPSERT/条件扣）+ 行锁批次查询 + 批次清单 | `InvStockMapper.java` + `InvStockMapper.xml` |
| 批次路由分流（batch_managed）+ FIFO/AVG/BATCH 实现 | `service/impl/StockServiceImpl.java` |
| 追溯 SQL | `InvMovementMapper.xml`（selectForwardTrace/selectBackwardTrace） |
| 批次/追溯接口 | `controller/BatchTraceController.java` |
| 出库策略枚举 | `constant/OutStrategy.java` |

---

# 三期扩展：WMS 库内作业深化（V421/V422）

> 交付内容：库区/库位三级（仓库→库区→库位）、作业任务引擎（上架/拣选/移库/补货，
> 扫描器零侵入生成）、波次管理（建波/拣选路径序/关波）、ABC 循环盘点、
> 效期 FEFO 出库、序列号全生命周期追溯、库内 KPI 与移动端作业页。
> 对标金蝶云星空「库存管理+条码」的库内作业能力。

## 12. 库位维度口径（最重要，先读）

`inv_stock` 唯一键升维为 `uk_stock(warehouse_id, material_id, batch_no, location_id, deleted)`：

- **location_id = 0**（哨兵，V421 由 NULL 迁移为 0——MySQL 唯一键含 NULL 不防重，
  0 哨兵保证未上架行的唯一键强约束）＝「汇总/未上架行」，即一期/二期的批次行语义，
  历史行为完全不变；
- **location_id > 0** ＝「库位行」＝**真实库存行**（不是镜像！全表 `SUM(qty)` 恒等于账面总量）。
  **仅批次物料**落库位行——非批次物料强制汇总行（规避 org/equipment 模块裸 SQL
  对 batch_no='' 多行命中的重复扣加风险，与 V411 的兼容边界一致）。

### 双写与守恒（避免账实分裂的核心约定）

| 动作 | 汇总/未上架行 | 库位行 | inv_movement |
|---|---|---|---|
| 业务入库过账（单据审核） | +qty | — | +1 行（location=0） |
| **上架 PUTAWAY 完成** | −qty | +qty（同事务双写，效期从来源行复制） | **不产生**（纯实物挪动） |
| 业务出库过账（单据审核，FIFO/FEFO/AVG/BATCH） | 候选行**未上架优先**再库位行，逐行条件扣 | 同左 | 每扣减行 1 条（带 location_id） |
| 拣选 PICK 完成（扫描器来源任务） | 不动 | 不动（账面已在出库单审核时扣减，**防双扣**） | 不产生 |
| 拣选 PICK 完成（手工任务，无来源） | — | 精确行扣（账实同扣） | 1 行 OTH_OUT（biz_no=任务号） |
| 移库 MOVE / 补货 REPLENISH 完成 | — | 调出行 − / 调入行 + | 不产生 |
| 盘点差异调平（按快照行） | 未上架行走原逻辑 | 库位行走 stockInAt/stockOutAtLocation | 1 行 ADJUST |

不变量：**任何时点 SUM(该仓+料+批的全部行) = 账面总量**；MRP/报表/预警的
`SUM(qty)` 聚合因此天然正确（无需改这些模块）。库内挪动不写流水——
收发存报表按流水 SUM 不会双计；挪动审计依赖 StockServiceImpl 日志 + wms_task 记录。

## 13. 表结构变更（V421/V422，全部幂等）

| 表 | 变更 |
|---|---|
| `base_zone`（新） | 库区：warehouse_id/code(仓内唯一)/name/zone_type（RECEIVE 收货/STORAGE 存储/PICKING 拣选/SHIP 发货/DEFECT 不良品）。种子：每仓各类型一个库区（Z-RECEIVE 等） |
| `base_location` | + zone_id（空=未分区）+ loc_type（默认 STORAGE） |
| `inv_stock` | + location_id（NOT NULL DEFAULT 0，存量 NULL→0）+ expiry_date DATE（V422）；uk_stock 加 location_id |
| `inv_movement` | + location_id（0=未定位/历史流水） |
| `inv_check` | + source（MANUAL/CYCLE 循环盘点自动生成） |
| `inv_check_detail` | + location_id；uk_check_material 加 location_id（快照按 库位×批次行 一条） |
| `wms_task` | 作业任务：task_no(T+流水)/task_type(PUTAWAY/PICK/MOVE/REPLENISH)/仓库/物料/批次/qty/location_id(目标或来源)/from_location_id(移库调出)/wave_id/source_type+source_id+source_no(来源单)/assignee/status(0待分配 10待执行 20执行中 30完成 40取消)/recommend_reason/finish_time |
| `wms_wave` | 波次：wave_no(W+流水)/warehouse/status(0建波 10拣选中 20完成)/finish_time |
| `wms_abc_class` | 物料分级：material_id/level(A/B/C)/freq(近90天出库频次)/calc_date；现行分级=deleted=0 记录集 |
| `base_material` | + shelf_life_days（V422；入库未传效期时 expiry=入库日+天数） |
| `inv_other_in_line` | + expiry_date（V422 表单透传） |
| `inv_other_out` | + strategy（V422：FIFO 默认/FEFO/AVG；行指定批次时强制 BATCH） |
| `inv_serial`（新） | 序列号台账：serial_no(全局唯一)/material/warehouse/batch/status(IN_STOCK/OUT/SCRAP)/source_no(入库单)/out_no(出库单) |
| sys_menu | 236 作业任务 / 237 波次管理 / 238 序列号管理 / 239 ABC 分级（挂库存管理 230），权限码 `inventory:wms:{task,wave,serial,abc}:{list,edit,audit}` |

## 14. StockService 三期签名（追加，一/二期签名不变）

```java
// V421 库位维度：locationId null/0=未上架汇总行；>0=库位行（仅批次物料）
void stockInAt(wh, mat, batchNo, locationId, qty, bizType, bizId, bizNo, remark, expiryDate);
DeductionLeg stockOutAtLocation(wh, mat, batchNo, locationId, qty, bizType, bizId, bizNo, remark); // 精确行扣
void transferLocation(wh, mat, batchNo, fromLocationId, toLocationId, qty, bizNo); // 库内挪动，无流水
InvStock getStockRow(wh, mat, batchNo, locationId);
// DeductionLeg(batchNo, qty, locationId) —— V421 起带库位
```

出库策略追加 **FEFO**（V422）：候选行按 `expiry_date` 升序（无效期批次按 first_in_at
兜底排后）；未上架行恒优先（"出库仍从汇总行扣"）。其他出库单表单可传
`strategy`（默认 FIFO）。

## 15. 扫描器（零侵入采购/销售）

- `@Scheduled(fixedDelay=30s)`（`WmsScheduleConfig`，`@EnableScheduling` 挂在本模块），
  开关 `wms.auto-task.enabled`（默认 true）、回看窗口 `wms.auto-task.days-back`（默认 7 天，
  防存量单据一次性涌入）；
- **只读约定表**生成任务：入库侧 pur_instock(status=10)+sal_return(10)+inv_other_in(1)
  +mrp_work_order(FINISHED 且有入库仓) → PUTAWAY；出库侧 sal_outstock(10)
  +inv_other_out(1) → PICK；幂等=NOT EXISTS 同 (source,material,batch) 未取消任务；
- **推荐库位**（记 recommend_reason）：上架=同物料现有库位优先→STORAGE 区空容库位
  （capacity=0 不限，占用=库位行 SUM）；拣选=该物料余量最大的库位行（批次随行锁定）；
- 定时线程无登录上下文 → 租户取默认 1000（OrgContextHolder 默认值）。
  任务落库对 Redis 序列失配导致的单号撞号做换号重试（并行环境的实例重叠曾触发）。

## 16. 三期 REST API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/inventory/wms/tasks` | 任务分页（类型/状态/仓库/执行人/关键词） |
| GET | `/inventory/wms/tasks/board?warehouseId` | 看板：(类型,状态)→计数 |
| GET | `/inventory/wms/kpi?from&to` | 库内 KPI：完成量/均耗时/及时率(当日完成占比)/库存准确率(1-差异行/盘点行)+人效 |
| POST | `/inventory/wms/tasks` | 手工创建（仅 MOVE/REPLENISH/PICK） |
| POST | `/inventory/wms/tasks/{id}/claim` / `start` / `complete` / DELETE | 领取→开始→完成(可传 actualQty 部分完成/locationId 改位)→取消 |
| POST | `/inventory/wms/tasks/scan` | 手动触发一轮扫描（调试/补生成） |
| GET | `/inventory/wms/waves` / `/{id}` / `/{id}/pick-path` | 波次列表/详情/拣选路径序（任务按库位编码升序=最简路径） |
| GET | `/inventory/wms/waves/outbound-docs` | 建波可选出库单（已审核且未全部生成拣选任务） |
| POST | `/inventory/wms/waves` / `/{id}/complete` | 建波（选多张出库单→生成 PICK 并入波）/ 关波（全部任务完成后） |
| GET | `/inventory/wms/abc` / `/counts` | 现行分级 / 各级计数 |
| POST | `/inventory/wms/abc/recalc` / `/cycle-checks` | 手动重算 / 手动触发循环盘点生成 |
| GET | `/inventory/serials` | 序列号正反查（serialNo 精确/前缀，materialId 反查） |
| POST | `/inventory/serials` / `/write-off` / `/scrap` | 入库登记(≤500条)/出库核销/报废 |
| GET | `/basedata/warehouses/{id}/zones` / `location-tree`；POST/PUT/DELETE `/basedata/zones` | 库区 CRUD + 仓库→库区→库位三级树（未分区兜底组） |

循环盘点定时：每月 1 号 02:00（`wms.abc.cycle-enabled` 默认 true）——每仓按其
现存最高优先级分级决定间隔（A=1 月/B=3 月/C=12 月），距上次 CYCLE 盘点超期则
自动创建 inv_check（source=CYCLE）。

## 17. 三期前端

- 桌面端：`pages/inventory/tasks/TaskList.tsx`（KPI 卡片+看板+ABC 重算+任务闭环+
  手工任务）、`waves/WaveList.tsx`（建波→路径序→逐条完成→关波）、
  `serials/SerialList.tsx`（登记/核销/报废/追溯）；路由片段 `inventory.tsx` 追加三条；
- 移动端（`src/mobile/wms/`，复用 MobileShell/登录态/扫码组件）：
  `TaskList`（/m/wms/tasks 领取/完成）、`PickByWave`（/m/wms/waves[/:id] 波次拣选）、
  `SerialScan`（/m/wms/serials 序列号出入扫码）；路由片段
  `src/router/modules/wmsMobile.tsx`（只导出 wmsMobileRoutes，主线程挂载，
  与 mobile.tsx 互不冲突）；
- API：`api/inventory.ts` 追加 WMS 全量类型与函数（TaskRow/WaveRow/SerialRow/AbcRow/Kpi）。

## 18. 三期关键实现位置

| 内容 | 文件 |
|---|---|
| 库位原语（定位 UPDATE/UPSERT/行锁候选含 FEFO 排序） | `InvStockMapper.xml` |
| 库位路由+上架转移+行级出库+效期推算 | `service/impl/StockServiceImpl.java` |
| 任务引擎（扫描器/推荐/领取完成/手工） | `service/impl/WmsTaskServiceImpl.java` |
| 扫描器候选/看板/KPI SQL | `mapper/inventory/WmsTaskMapper.xml` |
| 波次（建波/路径序/关波） | `service/impl/WmsWaveServiceImpl.java` + `WmsWaveMapper.xml` |
| ABC 分级与循环盘点 | `service/impl/WmsAbcServiceImpl.java` + `WmsAbcClassMapper.xml` |
| 序列号台账 | `service/impl/InvSerialServiceImpl.java` + `InvSerialMapper.xml` |
| 定时调度（扫描器 30s / 盘点月度） | `config/WmsScheduleConfig.java` |
| 库区/库位树 | erp-biz-basedata `BaseZoneController` / `BaseZoneServiceImpl` |

### 已知边界与遗留

1. `base_zone` 未加入 TenantConfig 共享白名单（framework 公共文件由主线程维护）——
   表带 tenant_id 列，租户插件按隔离表处理，默认租户行为不受影响；主线程可择机登记；
2. org/equipment 模块裸 SQL 不感知库位维度——**仅批次物料**落库位行的设计已使其
   不受影响（它们的 batch_no='' 命中永远只有唯一汇总行）；建议后续统一迁到 StockService；
3. 序列号台账与库存数量账独立（登记/核销不触发 inv_stock 过账），一致性由
   出入库单与核销动作人工/对账任务保证；
4. 拣选路径为库位编码字典序（最简路径），未做巷道/层级优化。
