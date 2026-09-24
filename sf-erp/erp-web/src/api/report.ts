import axios from 'axios'
import { get, type PageResult } from '@/api/request'
import { useAuthStore } from '@/stores/auth'

/**
 * 报表中心模块 API（驾驶舱 / 销售明细 / 采购明细 / 库存库龄 / 财务汇总）。
 * 类型与后端 com.erp.report.dto.vo 一一对应，金额/数量全部 number（后端 BigDecimal 序列化）。
 */

// ==================== 驾驶舱 ====================

/** 近 6 个月销售/采购月度走势行 */
export interface MonthlyTrend {
  month: string
  salesAmount: number
  purchaseAmount: number
}

/** 仓库库存价值分布行 */
export interface WarehouseValue {
  warehouseId: number
  warehouseName: string | null
  value: number
}

/** 当月销售 TOP 物料行 */
export interface TopMaterial {
  materialId: number
  materialCode: string | null
  materialName: string | null
  amount: number
  qty: number
}

/** 经营驾驶舱聚合数据 */
export interface DashboardData {
  month: string
  prevMonth: string
  salesAmount: number
  salesAmountPrev: number
  purchaseAmount: number
  purchaseAmountPrev: number
  inventoryValue: number
  receivableBalance: number
  payableBalance: number
  pendingApprovalCount: number
  stockWarningCount: number
  productionFinishQty: number
  productionFinishQtyPrev: number
  trends: MonthlyTrend[]
  warehouseValues: WarehouseValue[]
  topMaterials: TopMaterial[]
}

/** 经营驾驶舱聚合数据 */
export const getDashboard = () => get<DashboardData>('/report/dashboard')

// ==================== 多维报表 ====================

/** 销售明细查询参数 */
export interface SalesDetailParams {
  from?: string
  to?: string
  customerId?: number
  groupBy?: 'material' | 'customer' | 'month'
  export?: string
}

/** 采购明细查询参数 */
export interface PurchaseDetailParams {
  from?: string
  to?: string
  supplierId?: number
  groupBy?: 'material' | 'supplier' | 'month'
  export?: string
}

/**
 * 多维报表通用行：维度列随 groupBy 变化
 * （material=物料 / customer·supplier=客商 / month=yyyy-MM）；
 * costAmount/profitAmount 仅销售明细有值。
 */
export interface ReportRow {
  dimCode: string | null
  dimName: string | null
  qty: number
  amount: number
  costAmount: number | null
  profitAmount: number | null
}

/** 销售明细聚合 */
export const getSalesDetail = (params: SalesDetailParams) =>
  get<ReportRow[]>('/report/sales/detail', params as Record<string, unknown>)

/** 采购明细聚合 */
export const getPurchaseDetail = (params: PurchaseDetailParams) =>
  get<ReportRow[]>('/report/purchase/detail', params as Record<string, unknown>)

/** 库龄分桶汇总行 */
export interface AgingBucket {
  bucket: string
  label: string
  qty: number
  ratio: number
}

/** 库龄明细行（仓库×物料） */
export interface AgingDetail {
  warehouseId: number
  warehouseName: string | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  qty: number
  price: number
  value: number
  lastInTime: string | null
  ageDays: number | null
  bucket: string
  bucketLabel: string
}

/** 库存库龄分析结果 */
export interface InventoryAging {
  totalQty: number
  buckets: AgingBucket[]
  details: AgingDetail[]
}

/** 库存库龄分析 */
export const getInventoryAging = () => get<InventoryAging>('/report/inventory/aging')

/** 财务汇总（period=yyyy-MM，默认当月） */
export interface FinanceSummary {
  period: string
  revenue: number
  revenueSource: 'VOUCHER' | 'BIZ'
  cost: number
  costSource: 'VOUCHER' | 'BIZ'
  grossProfit: number
  grossMargin: number
  receivableBalance: number
  payableBalance: number
  cashIn: number
  cashOut: number
  cashNet: number
}

/** 财务汇总 */
export const getFinanceSummary = (period?: string) =>
  get<FinanceSummary>('/report/finance/summary', period ? { period } : undefined)

// ==================== 制造业标准报表（分组目录） ====================

/** 经营月报（一页纸指标） */
export interface MonthlySummary {
  month: string
  finishedOrderCount: number
  productionQty: number
  salesQty: number
  salesAmount: number
  purchaseAmount: number
  revenue: number
  cost: number
  revenueSource: 'VOUCHER' | 'BIZ'
  costSource: 'VOUCHER' | 'BIZ'
  grossProfit: number
  grossMargin: number
  expenseTotal: number
  receivableBalance: number
  payableBalance: number
  inventoryValue: number
}

/** 经营月报 */
export const getMonthlySummary = (month?: string) =>
  get<MonthlySummary>('/report/monthly-summary', month ? { month } : undefined)

/** 销售毛利分析查询参数 */
export interface SalesMarginParams {
  from?: string
  to?: string
  groupBy?: 'material' | 'customer'
  export?: string
}

/** 销售毛利行（维度 × 数量/销售额/成本/毛利/毛利率） */
export interface SalesMarginRow {
  dimCode: string | null
  dimName: string | null
  qty: number
  amount: number
  costAmount: number
  profitAmount: number
  marginPct: number
}

/** 销售毛利合计 */
export interface SalesMarginTotal {
  qty: number
  amount: number
  costAmount: number
  profitAmount: number
  marginPct: number
}

/** 销售毛利分析结果 */
export interface SalesMargin {
  groupBy: 'material' | 'customer'
  rows: SalesMarginRow[]
  total: SalesMarginTotal
}

/** 销售毛利分析 */
export const getSalesMargin = (params: SalesMarginParams) =>
  get<SalesMargin>('/report/sales/margin', params as Record<string, unknown>)

/** 供应商绩效行 */
export interface SupplierPerformanceRow {
  supplierId: number
  supplierCode: string | null
  supplierName: string | null
  orderCount: number
  orderAmount: number
  instockAmount: number
  receiptLineCount: number | null
  onTimeLineCount: number | null
  onTimeRate: number | null
  sampleQty: number | null
  qualifiedQty: number | null
  qualifiedRate: number | null
}

/** 供应商绩效分析结果 */
export interface SupplierPerformance {
  rows: SupplierPerformanceRow[]
}

/** 供应商绩效分析 */
export const getSupplierPerformance = (params: { from?: string; to?: string }) =>
  get<SupplierPerformance>('/report/purchase/supplier-performance', params)

/** 收发存月报行（物料 × 期初/入库/出库/期末） */
export interface InventoryMonthlyRow {
  materialId: number | null
  materialCode: string | null
  materialName: string | null
  unitName: string | null
  beginQty: number
  beginAmount: number
  inQty: number
  inAmount: number
  outQty: number
  outAmount: number
  endQty: number
  endAmount: number
}

/** 收发存合计行 */
export interface InventoryMonthlyTotal {
  beginQty: number
  beginAmount: number
  inQty: number
  inAmount: number
  outQty: number
  outAmount: number
  endQty: number
  endAmount: number
}

/** 收发存月报结果 */
export interface InventoryMonthly {
  month: string
  rows: InventoryMonthlyRow[]
  total: InventoryMonthlyTotal
}

/** 收发存月报 */
export const getInventoryMonthly = (month?: string) =>
  get<InventoryMonthly>('/report/inventory/monthly', month ? { month } : undefined)

/** 呆滞物料行 */
export interface SluggishRow {
  materialId: number | null
  materialCode: string | null
  materialName: string | null
  unitName: string | null
  qty: number
  price: number
  value: number
  ratio: number
  lastOutTime: string | null
  daysSinceOut: number | null
  lastInTime: string | null
  ageDays: number | null
}

/** 呆滞物料分析结果 */
export interface SluggishMaterials {
  days: number
  totalValue: number
  rows: SluggishRow[]
}

/** 呆滞物料分析 */
export const getSluggishMaterials = (days?: number) =>
  get<SluggishMaterials>('/report/inventory/sluggish', days != null ? { days } : undefined)

/** 工单执行跟踪行 */
export interface WorkExecutionRow {
  orderId: number
  orderNo: string
  materialCode: string | null
  materialName: string | null
  planQty: number
  finishQty: number
  wipQty: number
  finishRate: number
  status: string
  statusLabel: string
  planStartDate: string | null
  planEndDate: string | null
  overdue: boolean
}

/** 工单执行跟踪结果 */
export interface WorkExecution {
  rows: WorkExecutionRow[]
}

/** 工单执行跟踪 */
export const getWorkExecution = (params: { from?: string; to?: string }) =>
  get<WorkExecution>('/report/production/execution', params)

/** 报工工时统计行（工序/员工维度） */
export interface WorkhoursRow {
  dimCode: string | null
  dimName: string | null
  extraCount: number
  goodQty: number
  badQty: number
  badRate: number
  workMinutes: number
  workHours: number
}

/** 报工工时统计结果 */
export interface Workhours {
  groupBy: 'operation' | 'employee'
  rows: WorkhoursRow[]
}

/** 报工工时统计 */
export const getWorkhours = (params: {
  from?: string
  to?: string
  workCenterId?: number
  groupBy?: 'operation' | 'employee'
}) => get<Workhours>('/report/production/workhours', params)

/** 材料损耗分析行（工单 × 物料） */
export interface MaterialLossRow {
  workOrderId: number
  orderNo: string
  materialId: number | null
  materialCode: string | null
  materialName: string | null
  shouldQty: number
  actualQty: number
  lossQty: number
  lossRate: number
}

/** 材料损耗分析结果 */
export interface MaterialLoss {
  rows: MaterialLossRow[]
  total: { shouldQty: number; actualQty: number; lossQty: number; lossRate: number }
}

/** 材料损耗分析 */
export const getMaterialLoss = (params: { from?: string; to?: string }) =>
  get<MaterialLoss>('/report/production/material-loss', params)

/** 费用分析行（部门 × 科目） */
export interface ExpenseRow {
  deptId: number
  deptName: string
  subjectCode: string
  subjectName: string
  amount: number
  ratio: number
}

/** 费用分析结果 */
export interface ExpenseAnalysis {
  period: string
  rows: ExpenseRow[]
  totalAmount: number
}

/** 费用分析 */
export const getExpenseAnalysis = (period?: string) =>
  get<ExpenseAnalysis>('/report/finance/expense', period ? { period } : undefined)

/** 计件工资行（员工 × 工序） */
export interface PiecewageRow {
  employeeId: number | null
  empNo: string | null
  employeeName: string | null
  processName: string | null
  qty: number
  amount: number
  woCount: number
}

/** 计件工资汇总结果 */
export interface Piecewage {
  period: string
  rows: PiecewageRow[]
  total: { qty: number; amount: number }
}

/** 计件工资汇总 */
export const getPiecewage = (period?: string) =>
  get<Piecewage>('/report/hr/piecewage', period ? { period } : undefined)

// ==================== 基础资料下拉（跨模块只读约定结构） ====================

/** 基础资料下拉选项 */
interface BaseOption {
  id: number
  code: string
  name: string
}

async function fetchOptions(url: string): Promise<BaseOption[]> {
  const page = await get<PageResult<BaseOption>>(url, { pageNum: 1, pageSize: 500 })
  return page.records ?? []
}

/** 客户下拉（销售明细筛选） */
export const getCustomerOptions = () => fetchOptions('/basedata/customers')

/** 供应商下拉（采购明细筛选） */
export const getSupplierOptions = () => fetchOptions('/basedata/suppliers')

// ==================== CSV 导出下载 ====================
//
// CSV 走 blob 响应，不能复用 api/request.ts 的 JSON 拦截器（它会按 Result 拆包），
// 因此这里用独立 axios 实例自带 JWT，并手工处理 401 跳登录。

/**
 * 下载报表 CSV：调用报表接口（自动追加 export=csv），把 blob 保存为文件。
 *
 * @param path     接口路径（如 /report/sales/detail）
 * @param params   查询参数（不含 export）
 * @param filename 保存的文件名（含 .csv）
 */
export async function downloadReportCsv(
  path: string,
  params: Record<string, unknown>,
  filename: string,
): Promise<void> {
  const token = useAuthStore.getState().token
  try {
    const resp = await axios.get(`/api${path}`, {
      params: { ...params, export: 'csv' },
      responseType: 'blob',
      timeout: 30000,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    const url = URL.createObjectURL(resp.data as Blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      useAuthStore.getState().logout()
      window.location.href = '/login'
      return
    }
    throw error
  }
}
