import axios, { type AxiosResponse } from 'axios'
import { message } from 'antd'
import { useAuthStore } from '@/stores/auth'
import type { PageResult } from './request'

/**
 * 财务模块专用 Axios 实例（多账套）。
 * 与共享 request.ts 的差异仅一点：请求头自动携带当前账套 X-Book-Id，
 * 其余行为（JWT 附带 / Result 拆包 / 401 处理）与共享实例保持一致。
 * 账套为财务域概念，不改共享 request.ts（避免影响其他模块）。
 */
const fin = axios.create({ baseURL: '/api', timeout: 15000 })

/** 当前账套 ID（模块级状态，各财务页面筛选栏切换时更新；默认主账套 1001） */
let currentBookId = 1001

/** 账套切换：影响本模块后续所有请求的 X-Book-Id 头 */
export const setFinanceBookId = (id: number) => {
  currentBookId = id
}

/** 读取当前账套 ID（用于 queryKey / 徽标展示） */
export const getFinanceBookId = () => currentBookId

fin.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers['X-Book-Id'] = String(currentBookId)
  return config
})

/** 与共享 request.ts 相同的 Result 拆包约定（code===0 返回 data，否则报错拒绝） */
function unwrap<T>(response: AxiosResponse): T {
  const result = response.data
  if (result.code === 0) {
    return result.data as T
  }
  message.error(result.message || '操作失败')
  return Promise.reject(new Error(result.message)) as never
}

function unwrappedError(error: { response?: { status?: number; data?: { message?: string } }; message?: string }) {
  if (error.response?.status === 401) {
    useAuthStore.getState().logout()
    window.location.href = '/login'
  } else {
    const msg = error.response?.data?.message || error.message || '网络异常'
    message.error(msg)
  }
  return Promise.reject(error)
}

/** 财务模块 GET（自动带 X-Book-Id） */
function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return fin.get(url, { params }).then((r) => unwrap<T>(r)).catch(unwrappedError)
}

/** 财务模块 POST（自动带 X-Book-Id） */
function post<T>(url: string, body?: unknown): Promise<T> {
  return fin.post(url, body).then((r) => unwrap<T>(r)).catch(unwrappedError)
}

/** 财务模块 PUT（自动带 X-Book-Id） */
function put<T>(url: string, body?: unknown): Promise<T> {
  return fin.put(url, body).then((r) => unwrap<T>(r)).catch(unwrappedError)
}

/** 财务模块 DELETE（自动带 X-Book-Id） */
function del<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return fin.delete(url, { params }).then((r) => unwrap<T>(r)).catch(unwrappedError)
}

/** 核销状态：未核销 / 部分核销 / 已核销 */
export type SettleStatus = 'UNSETTLED' | 'PARTIAL' | 'SETTLED'

/** 凭证状态：草稿 / 已过账 */
export type VoucherStatus = 'DRAFT' | 'POSTED'

/** 会计科目（leaf=true 为末级科目，凭证分录只能选末级） */
export interface SubjectVO {
  id: number
  code: string
  name: string
  parentId: number
  category: string
  direction: string
  status: number
  remark: string | null
  leaf: boolean
}

/** 资金账户 */
export interface AccountVO {
  id: number
  accountNo: string
  name: string
  type: 'CASH' | 'BANK'
  bankName: string | null
  bankAccountNo: string | null
  initialBalance: number
  currentBalance: number
  status: number
  remark: string | null
}

/** 账户余额快照 */
export interface AccountBalanceVO {
  accountId: number
  accountNo: string
  accountName: string
  initialBalance: number
  income: number
  expense: number
  currentBalance: number
}

/** 账户流水 */
export interface AccountFlowVO {
  id: number
  accountId: number
  bizDate: string
  bizType: 'RECEIPT' | 'PAYMENT'
  bizId: number | null
  bizNo: string | null
  direction: 'INCOME' | 'EXPENSE'
  amount: number
  balanceAfter: number
  remark: string | null
}

/** 应收单 */
export interface ReceivableVO {
  id: number
  customerId: number
  customerName: string | null
  bizNo: string
  bizDate: string | null
  dueDate: string | null
  amount: number
  settledAmount: number
  unsettledAmount: number
  status: SettleStatus
  remark: string | null
  createTime: string
}

/** 应付单 */
export interface PayableVO {
  id: number
  supplierId: number
  supplierName: string | null
  bizNo: string
  bizDate: string | null
  dueDate: string | null
  amount: number
  settledAmount: number
  unsettledAmount: number
  status: SettleStatus
  remark: string | null
  createTime: string
}

/** 收款单（列表） */
export interface ReceiptVO {
  id: number
  receiptNo: string
  customerId: number
  customerName: string | null
  accountId: number
  accountName: string | null
  bizDate: string
  amount: number
  verifiedAmount: number
  remark: string | null
  createTime: string
}

/** 收款核销明细行 */
export interface ReceiptItemVO {
  receivableId: number
  bizNo: string | null
  receivableAmount: number | null
  settledAmount: number | null
  verifyAmount: number
}

/** 收款单（明细） */
export interface ReceiptDetailVO extends ReceiptVO {
  items: ReceiptItemVO[]
}

/** 收款单提交表单 */
export interface ReceiptForm {
  customerId: number
  accountId: number
  bizDate: string
  amount: number
  remark?: string
  items: { receivableId: number; verifyAmount: number }[]
}

/** 付款单（列表） */
export interface PaymentVO {
  id: number
  paymentNo: string
  supplierId: number
  supplierName: string | null
  accountId: number
  accountName: string | null
  bizDate: string
  amount: number
  verifiedAmount: number
  remark: string | null
  createTime: string
}

/** 付款核销明细行 */
export interface PaymentItemVO {
  payableId: number
  bizNo: string | null
  payableAmount: number | null
  settledAmount: number | null
  verifyAmount: number
}

/** 付款单（明细） */
export interface PaymentDetailVO extends PaymentVO {
  items: PaymentItemVO[]
}

/** 付款单提交表单 */
export interface PaymentForm {
  supplierId: number
  accountId: number
  bizDate: string
  amount: number
  remark?: string
  items: { payableId: number; verifyAmount: number }[]
}

/** 凭证（列表） */
export interface VoucherVO {
  id: number
  voucherNo: string
  voucherDate: string
  word: string
  attachmentCount: number
  totalDebit: number
  totalCredit: number
  status: VoucherStatus
  remark: string | null
  createTime: string
}

/** 凭证分录行 */
export interface VoucherEntryVO {
  lineNo: number
  summary: string
  subjectId: number
  subjectCode: string | null
  subjectName: string | null
  debitAmount: number
  creditAmount: number
}

/** 凭证（明细） */
export interface VoucherDetailVO extends VoucherVO {
  entries: VoucherEntryVO[]
}

/** 凭证提交表单 */
export interface VoucherForm {
  voucherDate: string
  word: string
  attachmentCount?: number
  remark?: string
  entries: { summary: string; subjectId: number; debitAmount?: number; creditAmount?: number }[]
}

/** 科目汇总表 */
export interface VoucherSummaryVO {
  subjectId: number | null
  subjectCode: string | null
  subjectName: string | null
  direction: string | null
  openingBalance: number
  debitTotal: number
  creditTotal: number
  closingBalance: number
}

/** 试算平衡表行 */
export interface TrialBalanceRow {
  subjectId: number
  code: string
  name: string
  category: string
  direction: string
  openingDebit: number
  openingCredit: number
  periodDebit: number
  periodCredit: number
  closingDebit: number
  closingCredit: number
}

/** 试算平衡表 */
export interface TrialBalanceVO {
  from: string
  to: string
  balanced: boolean
  totalOpeningDebit: number
  totalOpeningCredit: number
  totalPeriodDebit: number
  totalPeriodCredit: number
  totalClosingDebit: number
  totalClosingCredit: number
  rows: TrialBalanceRow[]
}

// ---------------- 会计科目 ----------------

/** 科目列表（可选编码/名称关键词） */
export const getSubjects = (keyword?: string) =>
  get<SubjectVO[]>('/finance/subjects', keyword ? { keyword } : undefined)

// ---------------- 资金账户 ----------------

/** 账户列表 */
export const getAccounts = () => get<AccountVO[]>('/finance/accounts')

/** 账户余额快照 */
export const getAccountBalance = (id: number) => get<AccountBalanceVO>(`/finance/accounts/${id}/balance`)

/** 账户流水分页 */
export const getAccountFlows = (id: number, pageNum = 1, pageSize = 10) =>
  get<PageResult<AccountFlowVO>>(`/finance/accounts/${id}/flows`, { pageNum, pageSize })

// ---------------- 应收 / 收款 ----------------

/** 应收分页查询 */
export const pageReceivables = (params: {
  pageNum?: number
  pageSize?: number
  customerId?: number
  status?: SettleStatus
  month?: string
  keyword?: string
  unsettledOnly?: boolean
}) => get<PageResult<ReceivableVO>>('/finance/receivables', params)

/** 应收明细 */
export const getReceivable = (id: number) => get<ReceivableVO>(`/finance/receivables/${id}`)

/** 手工补录应收 */
export const createReceivable = (body: {
  customerId: number
  bizNo: string
  amount: number
  bizDate?: string
  dueDate?: string
  remark?: string
}) => post<number>('/finance/receivables', body)

/** 收款单分页 */
export const pageReceipts = (params: {
  pageNum?: number
  pageSize?: number
  customerId?: number
  month?: string
  keyword?: string
}) => get<PageResult<ReceiptVO>>('/finance/receipts', params)

/** 收款单明细 */
export const getReceipt = (id: number) => get<ReceiptDetailVO>(`/finance/receipts/${id}`)

/** 保存收款单（核销应收） */
export const createReceipt = (body: ReceiptForm) => post<number>('/finance/receipts', body)

// ---------------- 应付 / 付款 ----------------

/** 应付分页查询 */
export const pagePayables = (params: {
  pageNum?: number
  pageSize?: number
  supplierId?: number
  status?: SettleStatus
  month?: string
  keyword?: string
  unsettledOnly?: boolean
}) => get<PageResult<PayableVO>>('/finance/payables', params)

/** 应付明细 */
export const getPayable = (id: number) => get<PayableVO>(`/finance/payables/${id}`)

/** 手工补录应付 */
export const createPayable = (body: {
  supplierId: number
  bizNo: string
  amount: number
  bizDate?: string
  dueDate?: string
  remark?: string
}) => post<number>('/finance/payables', body)

/** 付款单分页 */
export const pagePayments = (params: {
  pageNum?: number
  pageSize?: number
  supplierId?: number
  month?: string
  keyword?: string
}) => get<PageResult<PaymentVO>>('/finance/payments', params)

/** 付款单明细 */
export const getPayment = (id: number) => get<PaymentDetailVO>(`/finance/payments/${id}`)

/** 保存付款单（核销应付） */
export const createPayment = (body: PaymentForm) => post<number>('/finance/payments', body)

// ---------------- 总账凭证 / 账表 ----------------

/** 凭证分页查询 */
export const pageVouchers = (params: {
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: VoucherStatus
  from?: string
  to?: string
}) => get<PageResult<VoucherVO>>('/finance/vouchers', params)

/** 凭证明细 */
export const getVoucher = (id: number) => get<VoucherDetailVO>(`/finance/vouchers/${id}`)

/** 新增凭证（借贷不平衡后端拒绝） */
export const createVoucher = (body: VoucherForm) => post<number>('/finance/vouchers', body)

/** 修改凭证（仅草稿） */
export const updateVoucher = (id: number, body: VoucherForm) =>
  put<void>(`/finance/vouchers/${id}`, body)

/** 删除凭证（仅草稿） */
export const deleteVoucher = (id: number) => del<void>(`/finance/vouchers/${id}`)

/** 审核凭证（过账） */
export const auditVoucher = (id: number) => post<void>(`/finance/vouchers/${id}/audit`)

/** 科目汇总表 */
export const getVoucherSummary = (params: { subjectId?: number; from?: string; to?: string }) =>
  get<VoucherSummaryVO>('/finance/vouchers/summary', params)

/** 试算平衡表 */
export const getTrialBalance = (params?: { from?: string; to?: string }) =>
  get<TrialBalanceVO>('/finance/trial-balance', params)

// ---------------- 总账会计期间（结账/反结账） ----------------

/** 期间状态：开启 / 已结账 */
export type PeriodStatus = 'OPEN' | 'CLOSED'

/** 会计期间行（凭证数聚合 + 结账信息） */
export interface PeriodVO {
  /** 期间 yyyyMM */
  period: string
  status: PeriodStatus
  voucherCount: number
  draftCount: number
  postedCount: number
  closeBy: number | null
  closeTime: string | null
  reopenBy: number | null
  reopenTime: string | null
}

/** 期间列表（当前账套，期间倒序） */
export const getPeriods = () => get<PeriodVO[]>('/finance/periods')

/** 结账（该期凭证须全部过账，存在 DRAFT 后端拒绝并提示数量） */
export const closePeriod = (period: string) => post<void>(`/finance/periods/${period}/close`)

/** 反结账（CLOSED → OPEN） */
export const reopenPeriod = (period: string) => post<void>(`/finance/periods/${period}/reopen`)

// ---------------- 多账套（账套管理） ----------------

/** 账套档案 */
export interface BookVO {
  id: number
  code: string
  name: string
  /** 会计期间政策：NATURAL_MONTH 自然月 */
  periodPolicy: string
  /** 本位币 ISO 代码（默认 CNY） */
  currency: string
  /** 默认账套标记（默认账套不可删） */
  isDefault: number
  status: number
  remark: string | null
  createTime: string
}

/** 账套新增/修改表单 */
export interface BookForm {
  name: string
  periodPolicy?: string
  currency?: string
  status?: number
  remark?: string
}

/** 账套列表（全量，不分页） */
export const getBooks = () => get<BookVO[]>('/finance/books')

/** 新增账套（编码 BS+流水由后端生成） */
export const createBook = (body: BookForm) => post<number>('/finance/books', body)

/** 修改账套（名称/期间政策/本位币/状态/备注） */
export const updateBook = (id: number, body: BookForm) => put<void>(`/finance/books/${id}`, body)

/** 删除账套（默认账套 / 已有凭证的账套后端拒绝） */
export const deleteBook = (id: number) => del<void>(`/finance/books/${id}`)

/** 复制科目树到目标账套（新账套初始化；幂等：同编码跳过；fromBookId 缺省 1001） */
export const copyBookSubjects = (toBookId: number, fromBookId?: number) =>
  fin
    .post(`/finance/books/${toBookId}/copy-subjects`, undefined, {
      params: fromBookId != null ? { fromBookId } : undefined,
    })
    .then((r) => unwrap<number>(r))
    .catch(unwrappedError)

// ==================== 财务三大报表（资产负债表/利润表/应收应付账龄，V531 映射驱动） ====================

/** 资产负债表行（期初/期末两列，calcType 非空 = 合计计算行） */
export interface BalanceSheetRow {
  itemName: string
  side: 'ASSET' | 'LIABILITY' | 'EQUITY' | ''
  direction: 'DEBIT' | 'CREDIT' | ''
  calcType: string
  total: boolean
  subjectCodes: string
  opening: number
  closing: number
}

/** 资产负债表（勾稽：资产总计 = 负债合计 + 所有者权益合计，容差 0.01） */
export interface BalanceSheet {
  period: string
  bookId: number
  rows: BalanceSheetRow[]
  assetTotal: number
  liabTotal: number
  equityTotal: number
  liabEquityTotal: number
  balanced: boolean
  diff: number
  openingDiff: number
  profitLossNotCarried: boolean
  warnings: string[]
}

/** 利润表行（本月数 + 本年累计数） */
export interface IncomeStatementRow {
  itemName: string
  direction: 'DEBIT' | 'CREDIT' | ''
  calcType: string
  total: boolean
  subjectCodes: string
  monthAmount: number
  ytdAmount: number
}

/** 利润表 */
export interface IncomeStatement {
  period: string
  from: string
  to: string
  ytdFrom: string
  ytdTo: string
  bookId: number
  rows: IncomeStatementRow[]
  netProfitMonth: number
  netProfitYtd: number
  warnings: string[]
}

/** 账龄分桶（金额 + 占比） */
export interface AgingBucketAmount {
  key: string
  label: string
  amount: number
  ratio: number
}

/** 账龄行（客商粒度：客户/供应商 × 分桶金额） */
export interface PartnerAgingRow {
  partnerId: number
  partnerName: string | null
  docCount: number
  d30: number
  d60: number
  d90: number
  d180: number
  d180p: number
  total: number
  ratio: number
}

/** 应收/应付账龄分析 */
export interface PartnerAging {
  side: 'RECEIVABLE' | 'PAYABLE'
  asOf: string
  bookId: number
  buckets: AgingBucketAmount[]
  rows: PartnerAgingRow[]
  totalRow: PartnerAgingRow
  totalAmount: number
  totalDocCount: number
}

/** 资产负债表（period=yyyy-MM，默认当月） */
export const getBalanceSheet = (period?: string) =>
  get<BalanceSheet>('/finance/reports/balance-sheet', period ? { period } : undefined)

/** 利润表（period=yyyy-MM；from/to 显式传入时本月列切换为自定义区间） */
export const getIncomeStatement = (params?: { period?: string; from?: string; to?: string }) =>
  get<IncomeStatement>('/finance/reports/income-statement', params)

/** 应收账龄分析（asOf=yyyy-MM-dd，默认今天） */
export const getArAging = (asOf?: string) =>
  get<PartnerAging>('/finance/reports/ar-aging', asOf ? { asOf } : undefined)

/** 应付账龄分析（供应商维度，与应收对称） */
export const getApAging = (asOf?: string) =>
  get<PartnerAging>('/finance/reports/ap-aging', asOf ? { asOf } : undefined)
