# 固定资产模块（erp-asset）

> 对标金蝶 K3 固定资产（简化版）：资产类别（折旧政策+科目映射）、资产卡片、平均年限法折旧计提、处置清理、自动凭证、部门/类别统计。
> 后端模块：`erp-backend/erp-asset`；前端：`erp-web/src/pages/asset/`、`src/api/asset.ts`、`src/router/modules/asset.tsx`；SQL：`script/sql/migration/V911__asset_init.sql`。

## 1. 功能范围

| 功能 | 说明 |
|---|---|
| 资产类别 | 折旧方法（当前仅 AVG 平均年限法，WORKLOAD 预留）、预计年限月、残值率%、科目映射（资产/累计折旧/折旧费用 → fin_subject）；有资产引用禁删 |
| 资产卡片 | CRUD + 编码自动生成（ZC+4位流水，Redis 全局序列）；净值 = 原值−累计折旧（数据库生成列）；状态机 在用⇄闲置 / →处置（终态） |
| 入账凭证 | 新增卡片自动生成 POSTED 凭证：借 固定资产(类别资产科目) / 贷 应付账款 2202，摘要"资产入账 {编码}" |
| 折旧计提 | 月折旧额 =（原值−残值）/预计年限月，HALF_UP 2位；期间 yyyyMM 全局唯一，run 一期一张汇总凭证（借 折旧费用按部门×科目汇总多行 / 贷 累计折旧），资产累计折旧/已提月数条件 UPDATE +1，期间 CLOSED 后重复运行拒绝 |
| 资产处置 | 出售/报废/捐赠；净值转固定资产清理（1606）借贷平衡凭证：借 累计折旧+借 固定资产清理(净值) / 贷 固定资产(原值)；清理收入>0 追加 借 库存现金 1001 / 贷 固定资产清理；状态→处置后停提折旧 |
| 查询统计 | 资产分页（类别/状态/部门/关键词）；GET /asset/statistics?period 部门/类别维度原值、累计折旧（截至期间）、净值汇总 |

## 2. 表结构（V911，4 张新表 + 1 条科目种子）

| 表 | 说明 | 关键约束 |
|---|---|---|
| ast_category | 资产类别（折旧政策+科目映射） | uk(code)；三科目逻辑关联 fin_subject，保存时校验启用+末级 |
| ast_asset | 资产卡片 | uk(asset_code)；`net_value DECIMAL(18,2) GENERATED ALWAYS AS (original_value - accumulated_depreciation) STORED`；状态 IN_USE/IDLE/DISPOSED；入账/处置凭证 ID 回写 |
| ast_depreciation | 折旧计提期间 | uk(period yyyyMM)；状态 OPEN/CLOSED；汇总凭证 ID、资产数、总额 |
| ast_depreciation_detail | 折旧计提明细 | uk(period_id, asset_id)；月折旧额、凭证 ID（同期间共用汇总凭证） |

种子数据：

- 补充会计科目 `1606 固定资产清理`（ASSET/DEBIT，幂等插入，处置凭证专用）；
- 3 个类别，科目映射按**编码**解析 fin_subject 真实 ID（不硬编码主键）：
  - `DZ 电子设备` 36月/残值率5% → 1601 固定资产 + 1602 累计折旧 + 6602 管理费用
  - `YS 运输设备` 60月/5% → 1601 + 1602 + 6601 销售费用
  - `JQ 机器设备` 120月/3% → 1601 + 1602 + 5101 制造费用

## 3. 跨模块协作约定（遵守 finance.md 第 6 节）

- **fin_voucher / fin_voucher_entry / fin_subject**：本模块按约定表结构直写/只读（本地实体镜像 `com.erp.asset.entity.FinVoucher/FinVoucherEntry/FinSubject`，Mapper 类名加 Ast 前缀避免 Bean 冲突），**不 import erp-finance 代码**；
- **凭证号与 finance 共用 Redis 序列**：`AssetBizNoGenerator` 的 PZ 序列 key 与 `FinanceBizNoGenerator` 完全一致（`erp:no:PZ:yyyyMMdd`），跨模块凭证号全局唯一；资产编码用全局序列 `erp:no:ZC:asset`（不按天隔离）；
- **sys_dept / base_supplier 只读名称**（`AssetNameLookup`，表未落库时降级为 null，前端显示 ID 占位）；
- 自动凭证（入账/折旧/处置）生成即 **POSTED**：业务单据驱动的凭证不允许在手工凭证界面修改/删除（DRAFT 可改可删会破坏账实一致）；借贷平衡用 `BigDecimal.compareTo`，与 FinVoucherServiceImpl 规则一致（≥2 行、单边、末级启用科目）。

## 4. 核心事务与并发

### 4.1 折旧计提 run（AstDepreciationServiceImpl#run，`@Transactional`）

1. 期间格式校验（^\d{6}$ + YearMonth 解析）；期间行懒创建（uk_period 防重，DuplicateKey 容错）；
2. `SELECT ... FOR UPDATE` 锁期间行 → **串行化同期间并发 run**；状态 CLOSED 直接拒绝；
3. 圈定资产：`status='IN_USE' AND depreciated_months < expected_life_months`（闲置/已处置/提满不计提）；
4. 逐资产月折旧额（HALF_UP 2位），借方按（部门 × 折旧费用科目）分组汇总多行，贷方按累计折旧科目汇总；
5. `AssetVoucherWriter.createPostedVoucher` 生成汇总凭证（凭证日期=期间末日，借贷平衡校验）；
6. 逐资产**条件 UPDATE**（`applyDepreciation`，XML）：`SET 累计折旧+=?, 已提月数+=1 WHERE id=? AND status='IN_USE' AND 已提月数<年限` —— 影响行数≠1（并发处置/提满/删除）整体回滚，数据库层兜底不超提；
7. 明细落库 + 期间条件关闭（`WHERE status='OPEN'`，影响行数≠1 回滚）。

防重三重保障：uk_period 唯一约束 + FOR UPDATE 行锁 + CLOSED 状态条件关闭。

### 4.2 资产处置 dispose（`@Transactional`）

- 平衡关系：借(累计折旧 + 净值 + 清理收入) = 贷(原值 + 清理收入)；
- 状态置 DISPOSED（终态）：禁改禁删、run 自动排除；财务字段锁：已计提资产的 类别/原值/残值/年限/入账日期 禁止变更（金额比较用 compareTo，防 60000 vs 60000.00 尺度误判）。

## 5. 接口清单（统一前缀 /api，JWT 登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /asset/categories | 类别全量列表（含科目映射回显） |
| POST/PUT/DELETE | /asset/categories(/{id}) | 类别维护（删除时校验无资产引用） |
| GET | /asset/assets | 资产分页：categoryId/status/deptId/keyword + pageNum/pageSize |
| GET | /asset/assets/{id} | 资产详情（含类别/部门/供应商名称） |
| POST | /asset/assets | 新增（ZC 编码 + 入账凭证，残值/年限缺省取类别政策） |
| PUT | /asset/assets/{id} | 修改（财务字段计提后锁定；状态仅 在用/闲置 互转） |
| DELETE | /asset/assets/{id} | 删除（已计提/已处置/有明细拒绝） |
| POST | /asset/assets/{id}/dispose | 处置（method SELL/SCRAP/DONATE, income, remark 凭证说明） |
| GET | /asset/depreciations | 折旧期间分页（期间倒序，含凭证号） |
| GET | /asset/depreciations/{period} | 期间明细（头 + 逐资产行 + 合计；期间未计提报 DATA_NOT_EXISTS） |
| POST | /asset/depreciations/{period}/run | 执行计提（重复运行拒绝；无符合资产则空关期间并提示） |
| GET | /asset/statistics?period | 部门/类别维度：原值、截至期间累计折旧、净值 + 合计（period 缺省当月） |

## 6. 前端

- `pages/asset/cards/AssetCardList.tsx`：筛选（类别/状态/部门/关键词）+ 分页列表（原值/累计折旧/净值/已提月数）+ 新增 Modal（类别下拉回显折旧政策，残值/年限留空自动取类别默认）+ 处置 Modal（方式/清理收入/凭证说明，展示净值与平衡说明）+ 编辑（财务字段锁定态禁用）/删除；
- `pages/asset/depreciation/DepreciationPage.tsx`：期间列表（状态/资产数/总额/凭证号）+ 月份选择运行计提（Popconfirm，结果 Modal 展示汇总凭证号）+ 期间明细弹窗（带合计行）+ 截至期间统计（三指标卡 + 部门/类别双维度小表）；
- 路由：`router/modules/asset.tsx` 导出 `assetRoutes`（asset/cards、asset/depreciation，与 MainLayout 菜单一致），由主线程合并进 router/index.tsx。

## 7. 验证记录（2026-09-05）

- `mvn -pl erp-asset -am compile -q` 通过；
- Flyway V911 执行成功（4 张 ast_* 表 + 1606 科目 + 3 类别种子，科目映射按编码解析到真实 ID：1601/1602/6602/6601/5101）；
- `cd erp-web && npx tsc --noEmit` 通过（asset 相关 0 错误）；
- curl 端到端（主库 erp_db，资产原值 12000/残值 0/年限 36 月）：
  - 新增 → ZC 编码、入账凭证 12000.00/12000.00 借贷平衡 POSTED（借 1601 / 贷 2202，摘要"资产入账 ZCxxxx"）；
  - run 202608 → 月折旧 **333.33**（已提 1 月，净值 11666.67）；run 202609 → 累计 **666.66**（2 月，净值 11333.34）；
  - 两张折旧汇总凭证均 333.33/333.33 平衡 POSTED，分录：借 6602"折旧计提 202608 技术部" / 贷 1602"折旧计提 202608 累计折旧"；
  - 重复 run 202608 被拒（"期间已计提关闭，禁止重复运行"）；
  - 第三期处置（出售/收入 500）→ 处置凭证 12500.00/12500.00 平衡（借 1602 累计折旧 666.66 + 借 1606 净值 11333.34 + 借 1001 收入 500 / 贷 1601 原值 12000 + 贷 1606 收入 500）；
  - 第四期 run 202610 → 0 资产（"无符合条件的资产，期间已关闭"），资产保持 666.66/2 月/DISPOSED；
  - 附加：残值/年限缺省按类别政策带出（36000×3%=1080、120 月）；已计提资产改原值被拒（compareTo 锁）、改基本信息/闲置成功；闲置资产不计提；统计（截至 202611：原值 60000、折旧 1666.67、净值 58333.33，按部门/类别正确聚合）。

## 8. 遗留问题

- 月折旧额为等额平均，**未做最后一月尾差调整**（333.33×36=11999.88 ≠ 12000，残值 0 时净值残留 0.12）：金蝶按"提满即停"处理尾差留待后续迭代；
- 折旧费用科目按类别映射、借方按 部门×科目 分组，暂不支持"同一部门不同费用科目再分摊"的 finer 策略；
- `fin_voucher.audit_by` 未写审核人（同 finance 模块，待 framework 提供 SecurityUtils）；
- 删除资产不级联撤消其入账凭证（POSTED 历史凭证保留）；期间无"反结账/红冲"能力；
- WORKLOAD 工作量法未实现（类别校验当前仅接受 AVG）。
