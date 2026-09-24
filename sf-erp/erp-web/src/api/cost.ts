import { get, post, type PageResult } from '@/api/request'

/**
 * 成本管理模块 API：存货计价（移动加权平均）/ 月末核算 / 工单成本归集 / 成本分析。
 * 类型与后端 com.erp.cost 的 VO/Form 对齐。
 */

// ===================== 类型定义（与后端 VO 对齐） =====================

/** 计价流水行 */
export interface CostFlowRow {
  id: number
  bizType: string
  bizTypeLabel: string
  bizId: number | null
  bizNo: string | null
  movementId: number | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  warehouseId: number | null
  warehouseName: string | null
  bizDate: string | null
  qty: number
  unitCost: number
  amount: number
  balanceQty: number
  balanceCost: number
  period: string
}

/** 核算期间行 */
export interface CostPeriodRow {
  id: number
  period: string
  status: 'OPEN' | 'CLOSED'
  outAmount: number
  voucherId: number | null
  voucherNo: string | null
  settleBy: number | null
  settleTime: string | null
  remark: string | null
}

/** 物料收发存汇总行 */
export interface MaterialSummaryRow {
  materialId: number
  materialCode: string | null
  materialName: string | null
  beginQty: number
  beginAmount: number
  inQty: number
  inAmount: number
  outQty: number
  outAmount: number
  endQty: number
  endAmount: number
}

/** 收发存汇总（含合计） */
export interface SettleSummary {
  period: string
  status: 'OPEN' | 'CLOSED'
  rows: MaterialSummaryRow[]
  totalBeginAmount: number
  totalInAmount: number
  totalOutAmount: number
  totalEndAmount: number
}

/** 成本趋势点 */
export interface TrendPoint {
  period: string
  inAmount: number
  outAmount: number
  salOutCost: number
  prodUseCost: number
  stockBalance: number
}

/** 工单成本行（料工费 + 单位成本） */
export interface WorkOrderCostRow {
  workOrderId: number
  orderNo: string
  materialId: number
  materialCode: string | null
  materialName: string | null
  qty: number
  finishQty: number
  status: string
  materialCost: number
  laborCost: number
  overheadCost: number
  totalCost: number
  unitCost: number
}

// ===================== 查询参数 / 提交负载 =====================

/** 计价流水查询参数 */
export interface CostFlowQueryParams {
  pageNum?: number
  pageSize?: number
  period?: string
  materialId?: number
  bizType?: string
  keyword?: string
}

/** 计价重算结果 */
export interface SyncResult {
  fromPeriod: string
  toPeriod: string
  movementsProcessed: number
  flowsInserted: number
  materials: number
}

/** 手工补录负载 */
export interface HookReceivePayload {
  materialId: number
  warehouseId?: number
  bizType: string
  bizNo: string
  qty: number
  amount?: number
  bizDate?: string
}

/** 手工补录结果 */
export interface HookResult {
  flowId: number
  period: string
  qty: number
  unitCost: number
  amount: number
  balanceQty: number
  balanceCost: number
}

/** 月末结账结果 */
export interface SettleResult {
  period: string
  status: string
  outAmount: number
  salOutCost: number
  prodUseCost: number
  otherOutCost: number
  voucherId: number | null
  voucherNo: string | null
}

/** 制造费用分摊负载 */
export interface OverheadPayload {
  overheadAmount: number
  laborHours: number
  remark?: string
}

/** 基础资料下拉（跨模块只读约定，字段最小集） */
export interface MaterialOption {
  id: number
  code: string
  name: string
}

// ===================== 业务类型映射 =====================

/** 计价业务类型 -> 中文（与后端 CostBizType 对齐） */
export const BIZ_TYPE_OPTIONS: { label: string; value: string }[] = [
  { label: '采购入库', value: 'PUR_IN' },
  { label: '采购退货', value: 'PUR_RET' },
  { label: '销售出库', value: 'SAL_OUT' },
  { label: '销售退货', value: 'SAL_RET' },
  { label: '其他入库', value: 'OTH_IN' },
  { label: '其他出库', value: 'OTH_OUT' },
  { label: '盘点调整', value: 'ADJUST' },
  { label: '仓库调拨', value: 'MOVE' },
  { label: '生产入库', value: 'PROD_IN' },
  { label: '生产领料', value: 'PROD_USE' },
]

// ===================== 格式化 =====================

/** 金额格式化：2 位小数 + 千分位 */
export function fmtAmount(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 数量格式化：最多 4 位小数 + 千分位 */
export function fmtQty(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

/** 单价格式化：最多 6 位小数 */
export function fmtPrice(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 6 })
}

/** 期间(yyyyMM) -> 展示(yyyy-MM) */
export function fmtPeriod(period: string | null | undefined): string {
  if (!period || period.length !== 6) return period ?? '-'
  return `${period.slice(0, 4)}-${period.slice(4)}`
}

// ===================== API =====================

/** 计价流水分页 */
export function fetchFlowPage(params: CostFlowQueryParams) {
  return get<PageResult<CostFlowRow>>('/cost/flows', params as Record<string, unknown>)
}

/** 从库存流水重算指定期间计价（from 可空 = 从最早流水起） */
export function syncCost(from: string | undefined, to: string) {
  const qs = new URLSearchParams({ to })
  if (from) qs.set('from', from)
  return post<SyncResult>(`/cost/sync?${qs.toString()}`)
}

/** 手工补录一笔业务计价 */
export function receiveHook(data: HookReceivePayload) {
  return post<HookResult>('/cost/hooks/receive', data)
}

/** 核算期间列表 */
export function fetchPeriods() {
  return get<CostPeriodRow[]>('/cost/periods')
}

/** 月末结账（自动生成转账凭证并 CLOSED） */
export function settlePeriod(period: string) {
  return post<SettleResult>(`/cost/settle/${period}`)
}

/** 期间收发存汇总 */
export function fetchSettleSummary(period: string) {
  return get<SettleSummary>(`/cost/settle/summary/${period}`)
}

/** 物料收发存汇总（成本分析口径，同 settle summary） */
export function fetchMaterialSummary(period: string) {
  return get<SettleSummary>('/cost/analysis/materials', { period })
}

/** 月度成本趋势 */
export function fetchTrend(from: string, to: string) {
  return get<TrendPoint[]>('/cost/analysis/trend', { from, to })
}

/** 工单成本分页（料工费 + 单位成本） */
export function fetchWorkOrderCostPage(params: {
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: string
}) {
  return get<PageResult<WorkOrderCostRow>>('/cost/work-orders', params as Record<string, unknown>)
}

/** 工单制造费用分摊录入（整体覆盖） */
export function saveWorkOrderOverhead(id: number, data: OverheadPayload) {
  return post<number>(`/cost/work-orders/${id}/overhead`, data)
}

/** 启用物料下拉（跨模块只读接口，字段最小集） */
export function fetchMaterialOptions(keyword?: string) {
  return get<MaterialOption[]>('/basedata/materials/enabled', keyword ? { keyword } : undefined)
}

/** 启用仓库下拉（跨模块只读接口，字段最小集） */
export function fetchWarehouseOptions() {
  return get<{ id: number; code: string; name: string }[]>('/basedata/warehouses/enabled')
}
