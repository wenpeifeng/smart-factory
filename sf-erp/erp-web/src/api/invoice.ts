import { get, post, type PageResult } from './request'

/** 发票状态（销项/进项共用数值，语义见各 VO 注释） */
export type InvoiceStatus = 0 | 10 | 20

/** 销项发票 */
export interface SalesInvoiceVO {
  id: number
  invoiceNo: string
  invoiceDate: string | null
  customerId: number
  customerName: string | null
  bizType: string
  bizId: number
  bizNo: string
  amount: number
  taxRate: number
  taxAmount: number
  totalAmount: number
  redFlag: 0 | 1
  sourceInvoiceNo: string | null
  status: InvoiceStatus
  issueBy: number | null
  issueTime: string | null
  remark: string | null
  createTime: string
}

/** 进项发票 */
export interface PurchaseInvoiceVO {
  id: number
  invoiceNo: string
  invoiceDate: string | null
  supplierId: number
  supplierName: string | null
  bizType: string
  bizId: number
  bizNo: string
  amount: number
  taxRate: number
  taxAmount: number
  totalAmount: number
  redFlag: 0 | 1
  sourceInvoiceNo: string | null
  status: InvoiceStatus
  authDate: string | null
  remark: string | null
  createTime: string
}

/** 客户待开票清单行 */
export interface ToBeInvoicedVO {
  outstockId: number
  outNo: string
  customerId: number
  customerName: string | null
  outDate: string
  amount: number
}

/** 供应商待登记清单行 */
export interface ToBeRegisteredVO {
  instockId: number
  instockNo: string
  supplierId: number
  supplierName: string | null
  instockDate: string
  amount: number
}

/** 期间税负统计 */
export interface TaxStatVO {
  from: string
  to: string
  salesAmount: number
  salesTax: number
  purchaseTax: number
  vatPayable: number
  taxBurdenRate: number
}

/** 月度价税走势行 */
export interface MonthlyRowVO {
  month: number
  salesTotal: number
  salesTax: number
  purchaseTotal: number
  purchaseTax: number
}

/** 年度月度价税走势 */
export interface MonthlyStatVO {
  year: number
  months: MonthlyRowVO[]
}

/** 客户/供应商对账 */
export interface ReconciliationVO {
  partnerType: 'customer' | 'supplier'
  partnerId: number
  partnerName: string | null
  from: string
  to: string
  bizOrderCount: number
  bizAmount: number
  invoiceCount: number
  invoicedAmount: number
  diffAmount: number
}

// ---------------- 销项发票 ----------------

/** 销项发票分页查询 */
export const pageSalesInvoices = (params: {
  pageNum?: number
  pageSize?: number
  customerId?: number
  status?: InvoiceStatus
  keyword?: string
  from?: string
  to?: string
}) => get<PageResult<SalesInvoiceVO>>('/invoice/sales', params)

/** 销项发票明细 */
export const getSalesInvoice = (id: number) => get<SalesInvoiceVO>(`/invoice/sales/${id}`)

/** 开票申请（从销售出库单生成待开票，税率默认 13%） */
export const applySalesFromOrder = (outstockId: number, body?: { taxRate?: number; remark?: string }) =>
  post<number>(`/invoice/sales/from-order/${outstockId}`, body)

/** 开票（待开票 → 已开票） */
export const issueSalesInvoice = (id: number) => post<void>(`/invoice/sales/${id}/issue`)

/** 红冲（原票置已红冲 + 生成负数红字发票） */
export const reverseSalesInvoice = (id: number) => post<void>(`/invoice/sales/${id}/reverse`)

/** 客户待开票清单（可选客户过滤） */
export const listToBeInvoiced = (customerId?: number) =>
  get<ToBeInvoicedVO[]>('/invoice/sales/toBeInvoiced', customerId ? { customerId } : undefined)

// ---------------- 进项发票 ----------------

/** 进项发票分页查询 */
export const pagePurchaseInvoices = (params: {
  pageNum?: number
  pageSize?: number
  supplierId?: number
  status?: InvoiceStatus
  keyword?: string
  from?: string
  to?: string
}) => get<PageResult<PurchaseInvoiceVO>>('/invoice/purchases', params)

/** 进项发票明细 */
export const getPurchaseInvoice = (id: number) => get<PurchaseInvoiceVO>(`/invoice/purchases/${id}`)

/** 登记进项发票（从采购入库单，税率默认取明细统一税率否则 13%） */
export const registerPurchaseFromInstock = (
  instockId: number,
  body?: { invoiceNo?: string; invoiceDate?: string; taxRate?: number; remark?: string },
) => post<number>(`/invoice/purchases/from-instock/${instockId}`, body)

/** 认证（待认证 → 已认证，税额进入税负统计） */
export const authenticatePurchaseInvoice = (id: number) =>
  post<void>(`/invoice/purchases/${id}/authenticate`)

/** 红冲（已认证 → 已红冲 + 负数红字发票冲减税额） */
export const reversePurchaseInvoice = (id: number) =>
  post<void>(`/invoice/purchases/${id}/reverse`)

/** 供应商待登记清单（可选供应商过滤） */
export const listToBeRegistered = (supplierId?: number) =>
  get<ToBeRegisteredVO[]>('/invoice/purchases/toBeRegistered', supplierId ? { supplierId } : undefined)

// ---------------- 税务统计 / 对账 ----------------

/** 期间税负统计（缺省近一年） */
export const getTaxStat = (params?: { from?: string; to?: string }) =>
  get<TaxStatVO>('/invoice/statistics/tax', params)

/** 年度 12 个月价税走势（缺省当前年） */
export const getMonthlyStat = (year?: number) =>
  get<MonthlyStatVO>('/invoice/statistics/monthly', year ? { year } : undefined)

/** 客户/供应商对账辅助 */
export const getReconciliation = (params: {
  partnerType: 'customer' | 'supplier'
  partnerId: number
  from?: string
  to?: string
}) => get<ReconciliationVO>('/invoice/reconciliation', params)
