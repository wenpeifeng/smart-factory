# 人力资源模块（erp-hr）

> 对标用友 U8 人事薪资域的制造业裁剪：员工档案 / 考勤（CSV 批量导入）/ 计件价目 / 计件工资（与生产报工联动）/ 工资汇总（计件 + 加班 − 缺勤扣款）。
> 后端模块：`erp-backend/erp-hr/`；前端：`erp-web/src/pages/hr/`；迁移脚本：`script/sql/migration/V921__hr_init.sql`（**hr 使用 V92x 段**）。

## 1. 数据表（5 张，均为 hr_ 前缀）

| 表 | 说明 | 关键字段 / 约束 |
|---|---|---|
| hr_employee | 员工档案 | emp_no（EMP+yyyyMMdd+4位流水，Redis INCR 生成，唯一）；id_card 返回脱敏（前6后4）；dept_id→sys_dept；status：ON_JOB在职 / RESIGNED离职；base_salary 月薪基准；**user_id→sys_user（计件归集的员工归属依据）** |
| hr_attendance | 考勤记录 | **uk(employee_id, att_date)** 一员工一天一行；status：NORMAL/ABSENT/LATE/LEAVE/OVERTIME；导入 upsert 覆盖（同月重复导入幂等） |
| hr_piece_price | 计件价目 | uk(material_id, process_name)；unit_price DECIMAL(18,2)；status：ENABLED/DISABLED |
| hr_piece_wage | 计件工资期间 | period（yyyy-MM，唯一）；status：OPEN开账 → CLOSED已结账（单向） |
| hr_piece_wage_detail | 计件工资明细 | **uk(period_id, work_order_id)** 防重复归集；qty DECIMAL(18,4)、unit_price/amount DECIMAL(18,2)；行内快照 work_order_no/process_name/unit_price 保证可追溯 |

状态机：

```
员工:      ON_JOB → RESIGNED（离职办理，填离职日期，单向；离职后档案冻结不可改）
考勤记录:   无状态机（upsert 覆盖）
价目:      ENABLED ↔ DISABLED
计件期间:   OPEN（首次归集自动开账）→ CLOSED（结账后拒绝再归集）
```

## 2. 计件归集口径（一期报工无工序粒度的简化，重要）

`POST /hr/piece-wages/{period}/collect` 的归集规则（`PieceWageServiceImpl`）：

1. **候选订单**（读 mrp 约定表 mrp_work_order，`HrIntegrationMapper.selectFinishedWorkOrders`）：
   `status IN ('FINISHED','CLOSED') AND finish_qty > 0 AND DATE_FORMAT(update_time,'%Y-%m') = 期间`
   —— 一期无"完工时间"字段，**以订单最后更新时间近似完工月份**（CLOSED 单按关单月， documented simplification）；
   且 `NOT EXISTS` 已被**其他期间**归集过的订单 → 同一完工量跨期只计一次计件。
2. **单价**：成品物料取**首条启用价目**（`hr_piece_price WHERE status='ENABLED' ORDER BY id LIMIT 1`，
   即"首层工序价"简化 —— 一期报工没有工序数量，无法按工序分摊；明细行快照 process_name/unit_price 可追溯定价依据）。
3. **数量与金额**：qty = 订单累计完工良品数 finish_qty；amount = qty × unit_price（HALF_UP 2 位）。
4. **员工归属**：订单制单人 `mrp_work_order.create_by`（sys_user.id）→ `hr_employee.user_id` 匹配员工，
   同账号多员工时在职优先、工号稳定取一；匹配不到计入 `skippedNoEmployee`（归集结果返回，过程可审计）。
5. **幂等**：collect 为"整单重建"——物理清空本期间旧明细后重建（派生数据可再生；逻辑删除行会占用
   uk(period_id, work_order_id) 导致重建唯一键冲突，故用物理 DELETE）。CLOSED 期间一律拒绝。

## 3. 工资汇总（仅计算，不生成凭证）

`GET /hr/wages/summary?month=yyyy-MM`，员工范围 = 在职 ∪ 当月有考勤/计件数据：

```
应发合计 = 计件合计(hr_piece_wage_detail 当期按员工 SUM(amount))
         + 加班天数(hr_attendance 当月 OVERTIME 天数) × 50      ← 常量 OVERTIME_ALLOWANCE_PER_DAY
         − 月薪基准 / 30 × 缺勤天数(当月 ABSENT 天数)            ← HALF_UP 2 位
```

加班单价 50 元/天、缺勤折算基数 30 天为 `HrConstants` 常量（简化模型）；凭证联动由 finance 后续扩展。

## 4. 接口清单（/api 前缀，JWT 认证）

| 分组 | 接口 |
|---|---|
| 员工 | GET /hr/employees（分页，keyword姓名/deptId/status）；GET/POST/PUT/DELETE /hr/employees/{id}；PUT /hr/employees/{id}/resign（离职办理） |
| 考勤 | GET /hr/attendances（分页明细，employeeId/from/to）；GET /hr/attendances/summary?month（按员工汇总五状态天数+出勤合计）；**POST /hr/attendances/import**（multipart CSV：`工号,日期,状态[,备注]`，首行表头可省略，重复行覆盖，逐行报错不中断） |
| 计件价目 | GET /hr/piece-prices（keyword=物料编码/名称/工序）；GET/POST/PUT/DELETE /hr/piece-prices/{id} |
| 计件工资 | GET /hr/piece-wages（期间分页，附明细数/合计）；GET /hr/piece-wages/{period}；GET /{period}/details（明细分页，可按员工过滤）；GET /{period}/summary（按员工合计）；**POST /{period}/collect**（归集，期间不存在自动开账，幂等重建）；**POST /{period}/close**（结账） |
| 工资汇总 | GET /hr/wages/summary?month |
| 支撑 | GET /hr/support/depts；GET /hr/support/materials?keyword；GET /hr/support/employees?keyword（在职下拉） |

前端：`hr/employees`（档案列表+编辑/离职 Modal）、`hr/attendance`（明细/月度汇总/CSV 导入三 Tab）、`hr/piecewage`（计件期间归集/结账/明细/按员工合计 + 计件价目维护两 Tab）；路由导出 `hrRoutes`（src/router/modules/hr.tsx）。
**注**：router/index.tsx 与 MainLayout 菜单为公共文件，`hrRoutes` 需主线程合并（同其他模块做法）。

## 5. 跨模块集成契约（并行协作，直接 SQL 不 import 对方代码）

集成网关：`HrIntegrationMapper` + `resources/mapper/hr/HrIntegrationMapper.xml`，只读以下约定列：

| 表 | 使用的列 | 用途 |
|---|---|---|
| sys_dept | id, parent_id, name, sort, status(1启用) | 员工部门回显/校验、部门下拉 |
| base_material | id, code, name, spec, status(1启用) | 计件价目物料校验/回显/选择器 |
| mrp_work_order | id, order_no, material_id, finish_qty, status, create_by, update_time | 计件归集完工数据来源（见 §2 口径） |

### ★ hr_piece_wage_detail 表结构契约（给 erp-cost）

**erp-cost 归集工单人工成本时按下表读取，不 import 本模块代码：**

```sql
SELECT work_order_id, employee_id, amount        -- 三列为核心契约，不可改名
FROM hr_piece_wage_detail
WHERE deleted = 0
  AND period_id IN (SELECT id FROM hr_piece_wage WHERE period = :month AND deleted = 0)
-- 按 work_order_id 分组 SUM(amount) = 该工单计件人工成本；employee_id 可细分到人
```

- `work_order_id BIGINT NOT NULL` → mrp_work_order.id；`employee_id BIGINT NOT NULL` → hr_employee.id；
  `amount DECIMAL(18,2) NOT NULL` = qty × unit_price。
- 辅助列：period_id、work_order_no（快照）、material_id、process_name（快照）、qty DECIMAL(18,4)、unit_price DECIMAL(18,2)。
- 数据稳定性：仅 OPEN 期间可重建（collect）；**CLOSED 期间明细只读不变**，cost 取数建议限定已结账期间。
- 唯一键 uk(period_id, work_order_id)：同一工单在同一期间至多一条计件明细（首条启用价目口径）。

## 6. 集成点与遗留说明

1. **员工归属依赖 user_id**：hr_employee.user_id 关联 sys_user.id，归集按"工单制单人→员工"归属；
   员工未维护 user_id 时其相关订单计入 skippedNoEmployee 跳过（归集结果可审计）。二期若有报工人工序表，应切换为按报工人归集。
2. **完工月份近似**：mrp_work_order 无完工时间列，期间按 update_time 近似（PRODUCING 中部分完工订单在最终完工月一次性计入累计 finish_qty）。
3. **加班费/缺勤基数常量**：50 元/天、30 天/月 写死在 HrConstants，未做参数配置（后续可挂 sys_dict）。
4. **工资汇总不生成凭证**：按任务约定仅计算汇总；finance 凭证联动留待后续扩展。
5. **考勤 CSV 容错**：逐行业务错误收集返回（不中断），文件需 UTF-8；状态只认 5 个英文枚举码。
6. **Bean 命名**：本模块 SeqNoServiceImpl 因与 erp-mrp 同名冲突已更名 HrSeqNoServiceImpl（聚合启动扫描 com.erp.**，并行模块同类名需错开，先例见 mrp.md InvStockMapper）。
7. **验证环境说明（2026-09-05）**：共享 8080 当时被并行模块 equipment/inventory 的 `bizNoGenerator` Bean 同名冲突阻塞（非 hr 问题，equipment 侧待修），hr 闭环在**隔离实例（端口 18092，共享 erp_db，jar 内 equipment 以空壳替换）**验证通过；equipment 冲突修复后共享实例重启即含 hr 全部接口（V921 已在 erp_db 执行成功）。

## 7. 验证记录（2026-09-05，隔离实例 18092，全部通过）

- `mvn -pl erp-hr -am compile/package -q` 通过；`npx tsc --noEmit` hr 相关文件 0 错误。
- Flyway：V921 于共享 erp_db 执行成功，5 表建立，2 条计件价目种子（material 3 冷轧钢板 冲压成型 12.50 / material 4 六角螺栓 冷镦成型 0.80，对准 base_material 真实 ID）。
- 闭环：
  1. 建档 2 人：工号 EMP202609050001/0002 自动生成；**身份证返回脱敏** `110101********1234`；部门名回显（技术部）；
  2. CSV 导入 10 行 10 成功；二次导入 2 行（1 覆盖 09-06 状态 NORMAL→OVERTIME，1 工号不存在报"第3行"错误不中断）；
  3. 月度汇总与逐日明细完全一致：张三 正常2/缺勤1/迟到1/请假0/加班2/出勤3，李四 正常2/请假1/加班1/出勤2；
  4. 造完工工单 3 笔（1 笔正常、1 笔物料无价目、1 笔制单人无关联员工）：collect 候选 3 单 → 明细 1 条
     **100 × 12.50 = 1250.00 校验通过**，skippedNoPrice=1、skippedNoEmployee=1；另自动归集到 mrp 真实订单 MO202609050001（10×12.50=125，跨模块联动实证）；
  5. 开账状态重复 collect 幂等（明细重建后 2 条 / 1375.00，DB 无重复）；结账成功；**结账后再 collect 被拒**（"期间已结账，不允许重复归集"）；
  6. 工资汇总公式验证：张三 1375 + 2×50 − 6000/30×1 = **1275.00**；李四 0 + 1×50 − 0 = **50.00**；
  7. 离职办理：状态 RESIGNED + 离职日期 2026-09-10 落库；离职后修改档案被拒；价目 CRUD + 同物料同工序唯一拦截正常。
