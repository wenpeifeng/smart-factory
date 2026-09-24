import { get, post, put, del, type PageResult } from './request'

/**
 * 预算管理模块 API 定义（预算方案/明细矩阵/控制配置/预算控制/执行分析）。
 * 出参类型与后端 VO 严格对齐；金额后端 DECIMAL(18,2)，JSON 序列化为 number。
 */

/** 方案状态：DRAFT草稿(可编制) RELEASED已下达(锁定禁改) CLOSED已关闭 */
export type BudgetPlanStatus = 'DRAFT' | 'RELEASED' | 'CLOSED'

/** 方案状态选项（与后端 budget_plan.status 注释一致） */
export const PLAN_STATUS_OPTIONS: { value: BudgetPlanStatus; label: string }[] = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'RELEASED', label: '已下达' },
  { value: 'CLOSED', label: '已关闭' },
]

/** 状态文案 */
export const PLAN_STATUS_TEXT: Record<BudgetPlanStatus, string> = {
  DRAFT: '草稿',
  RELEASED: '已下达',
  CLOSED: '已关闭',
}

/** 控制方式：WARN超预算警告放行 / BLOCK超预算阻止 */
export type BudgetControlWay = 'WARN' | 'BLOCK'

/** 来源类型：PAYMENT付款 / VOUCHER凭证 / MANUAL手工 */
export type BudgetUsageSourceType = 'PAYMENT' | 'VOUCHER' | 'MANUAL'

/* ==================== 预算方案 ==================== */

/** 方案列表/详情行 */
export interface BudgetPlanRow {
  id: number
  planCode: string
  planName: string
  budgetYear: number
  status: BudgetPlanStatus
  statusText: string
  totalAmount: number
  controlWay: BudgetControlWay | null
  overPercent: number | null
  prepareBy: number | null
  prepareByName: string | null
  remark: string | null
  createTime: string
}

/** 方案新增/修改提交体 */
export interface BudgetPlanSavePayload {
  planCode: string
  planName: string
  budgetYear: number
  remark?: string
}

/** 方案分页查询参数 */
export interface BudgetPlanPageParams {
  pageNum: number
  pageSize: number
  keyword?: string
  budgetYear?: number
  status?: BudgetPlanStatus
}

/** 明细矩阵行（部门×科目×12月，行合计） */
export interface MatrixRowVO {
  deptId: number
  deptName: string
  subjectId: number
  subjectCode: string
  subjectName: string
  m1: number | null
  m2: number | null
  m3: number | null
  m4: number | null
  m5: number | null
  m6: number | null
  m7: number | null
  m8: number | null
  m9: number | null
  m10: number | null
  m11: number | null
  m12: number | null
  rowTotal: number
}

/** 明细矩阵（含列合计） */
export interface BudgetMatrixVO {
  planId: number
  budgetYear: number
  status: BudgetPlanStatus
  rows: MatrixRowVO[]
  columnTotals: {
    m1: number
    m2: number
    m3: number
    m4: number
    m5: number
    m6: number
    m7: number
    m8: number
    m9: number
    m10: number
    m11: number
    m12: number
    grandTotal: number
  }
}

/** 矩阵保存行提交体 */
export interface MatrixRowInput {
  deptId: number
  subjectId: number
  m1: number
  m2: number
  m3: number
  m4: number
  m5: number
  m6: number
  m7: number
  m8: number
  m9: number
  m10: number
  m11: number
  m12: number
}

/** 控制配置提交体 */
export interface ControlConfigPayload {
  controlWay: BudgetControlWay
  overPercent: number
}

export const planApi = {
  page: (params: BudgetPlanPageParams) => get<PageResult<BudgetPlanRow>>('/budget/plans', { ...params }),
  get: (id: number) => get<BudgetPlanRow>(`/budget/plans/${id}`),
  create: (payload: BudgetPlanSavePayload) => post<number>('/budget/plans', payload),
  update: (id: number, payload: BudgetPlanSavePayload) => put<void>(`/budget/plans/${id}`, payload),
  remove: (id: number) => del<void>(`/budget/plans/${id}`),
  /** 下达（草稿→已下达，明细锁定禁改） */
  release: (id: number) => post<void>(`/budget/plans/${id}/release`),
  /** 关闭（已下达→已关闭） */
  close: (id: number) => post<void>(`/budget/plans/${id}/close`),
  /** 复制（默认为下一年度草稿，含明细与控制配置） */
  copy: (id: number, targetYear?: number) =>
    post<number>(`/budget/plans/${id}/copy${targetYear ? `?targetYear=${targetYear}` : ''}`),
  /** 明细矩阵（部门×科目×12月 + 行列合计） */
  matrix: (id: number) => get<BudgetMatrixVO>(`/budget/plans/${id}/matrix`),
  /** 保存矩阵（仅草稿） */
  saveMatrix: (id: number, rows: MatrixRowInput[]) => put<void>(`/budget/plans/${id}/matrix`, { rows }),
  /** 保存控制配置 */
  saveConfig: (id: number, payload: ControlConfigPayload) =>
    put<void>(`/budget/plans/${id}/control-config`, payload),
}

/* ==================== 预算控制 ==================== */

/** 预算校验结果（OK预算内 / WARN超阈值警告放行 / BLOCK超阈值阻止后端抛错） */
export interface BudgetCheckResult {
  level: 'OK' | 'WARN' | 'BLOCK'
  passed: boolean
  deptId: number
  deptName: string
  subjectId: number
  subjectCode: string
  subjectName: string
  year: number
  month: number
  budgetAmount: number
  usedAmount: number
  checkAmount: number
  totalAfterCheck: number
  ratioPercent: number | null
  controlWay: BudgetControlWay | null
  thresholdPercent: number | null
  message: string
}

/** 预算校验提交体 */
export interface BudgetCheckPayload {
  deptId: number
  subjectId: number
  year: number
  month: number
  amount: number
}

/** 执行数登记提交体（付款接线/演示，冲销传负数） */
export interface UsageRecordPayload {
  deptId: number
  subjectId: number
  year: number
  month: number
  amount: number
  sourceType?: BudgetUsageSourceType
  sourceId?: number
  sourceNo?: string
  remark?: string
}

export const controlApi = {
  /** 预算占用校验（BLOCK 场景后端抛业务异常，拦截器统一弹错误） */
  check: (payload: BudgetCheckPayload) => post<BudgetCheckResult>('/budget/check', payload),
  /** 登记执行数 */
  recordUsage: (payload: UsageRecordPayload) => post<number>('/budget/usage', payload),
}

/* ==================== 执行分析 ==================== */

/** 部门×科目执行分析行 */
export interface AnalysisRow {
  deptId: number
  deptName: string
  subjectId: number
  subjectCode: string
  subjectName: string
  budgetAmount: number
  usedAmount: number
  balanceAmount: number
  /** 执行率%（预算为 0 时为 null） */
  executionRate: number | null
}

/** 月度执行曲线数据点 */
export interface MonthlyCurvePoint {
  month: number
  budgetAmount: number
  usedAmount: number
  executionRate: number | null
}

export const analysisApi = {
  rows: (planId: number, deptId?: number) =>
    get<AnalysisRow[]>('/budget/analysis', { planId, deptId: deptId ?? undefined }),
  monthly: (planId: number, deptId?: number, subjectId?: number) =>
    get<MonthlyCurvePoint[]>('/budget/analysis/monthly', {
      planId,
      deptId: deptId ?? undefined,
      subjectId: subjectId ?? undefined,
    }),
}

/* ==================== 编制基础选项 ==================== */

/** 部门选项节点（sys_dept 只读） */
export interface DeptOption {
  id: number
  parentId: number
  name: string
  status: number
}

/** 末级费用/成本类科目选项节点（fin_subject 只读） */
export interface SubjectOption {
  id: number
  code: string
  name: string
}

export interface BudgetOptions {
  depts: DeptOption[]
  subjects: SubjectOption[]
}

export const optionsApi = {
  get: () => get<BudgetOptions>('/budget/options'),
}
