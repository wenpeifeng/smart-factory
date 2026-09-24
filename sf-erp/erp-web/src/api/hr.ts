/**
 * 人力资源模块 API —— 类型定义与请求函数（与后端 VO 一一对应）。
 */
import { get, post, put, del, type PageResult } from '@/api/request'

// ==================== 公共类型 ====================

/** 部门简要（含 parentId 供组树） */
export interface DeptBrief {
  id: number
  parentId: number
  name: string
}

/** 物料简要（选择器） */
export interface MaterialBrief {
  id: number
  code: string
  name: string
  spec: string | null
}

/** 员工下拉选项 */
export interface EmployeeOption {
  id: number
  empNo: string
  name: string
  status: 'ON_JOB' | 'RESIGNED'
}

// ==================== 员工档案 ====================

/** 员工行（身份证脱敏） */
export interface EmployeeRow {
  id: number
  empNo: string
  name: string
  gender: number
  phone: string | null
  idCard: string | null
  deptId: number | null
  deptName: string | null
  position: string | null
  hireDate: string
  resignDate: string | null
  status: 'ON_JOB' | 'RESIGNED'
  baseSalary: number
  userId: number | null
  remark: string | null
  createTime: string
}

/** 员工表单 */
export interface EmployeeForm {
  name: string
  gender: number
  phone?: string
  idCard?: string
  deptId?: number
  position?: string
  hireDate: string
  baseSalary: number
  userId?: number
  remark?: string
}

export const employeeApi = {
  page: (params: {
    pageNum: number
    pageSize: number
    keyword?: string
    deptId?: number
    status?: string
  }) => get<PageResult<EmployeeRow>>('/hr/employees', params),

  detail: (id: number) => get<EmployeeRow>(`/hr/employees/${id}`),

  create: (data: EmployeeForm) => post<number>('/hr/employees', data),

  update: (id: number, data: EmployeeForm) => put<void>(`/hr/employees/${id}`, data),

  remove: (id: number) => del<void>(`/hr/employees/${id}`),

  resign: (id: number, resignDate: string) => put<void>(`/hr/employees/${id}/resign`, { resignDate }),
}

// ==================== 考勤 ====================

export type AttendanceStatus = 'NORMAL' | 'ABSENT' | 'LATE' | 'LEAVE' | 'OVERTIME'

/** 考勤明细行 */
export interface AttendanceRow {
  id: number
  employeeId: number
  empNo: string
  employeeName: string
  deptName: string | null
  attDate: string
  status: AttendanceStatus
  remark: string | null
  updateTime: string
}

/** 考勤月度汇总行 */
export interface AttendanceSummaryRow {
  employeeId: number
  empNo: string
  employeeName: string
  deptName: string | null
  normalDays: number
  absentDays: number
  lateDays: number
  leaveDays: number
  overtimeDays: number
  attendedDays: number
}

/** CSV 导入结果 */
export interface AttendanceImportResult {
  total: number
  success: number
  failed: number
  errors: string[]
}

export const attendanceApi = {
  page: (params: {
    pageNum: number
    pageSize: number
    employeeId?: number
    from?: string
    to?: string
  }) => get<PageResult<AttendanceRow>>('/hr/attendances', params),

  summary: (month: string) => get<AttendanceSummaryRow[]>('/hr/attendances/summary', { month }),

  /** CSV 导入：每行"工号,日期(yyyy-MM-dd),状态"，重复覆盖 */
  importCsv: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return post<AttendanceImportResult>('/hr/attendances/import', form)
  },
}

// ==================== 计件价目 ====================

/** 计件价目行 */
export interface PiecePriceRow {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  processName: string
  unitPrice: number
  status: 'ENABLED' | 'DISABLED'
  remark: string | null
  updateTime: string
}

export interface PiecePriceForm {
  materialId: number
  processName: string
  unitPrice: number
  status?: string
  remark?: string
}

export const piecePriceApi = {
  page: (params: {
    pageNum: number
    pageSize: number
    keyword?: string
    status?: string
  }) => get<PageResult<PiecePriceRow>>('/hr/piece-prices', params),

  detail: (id: number) => get<PiecePriceRow>(`/hr/piece-prices/${id}`),

  create: (data: PiecePriceForm) => post<number>('/hr/piece-prices', data),

  update: (id: number, data: PiecePriceForm) => put<void>(`/hr/piece-prices/${id}`, data),

  remove: (id: number) => del<void>(`/hr/piece-prices/${id}`),
}

// ==================== 计件工资 ====================

/** 计件期间行 */
export interface PieceWageRow {
  id: number
  period: string
  status: 'OPEN' | 'CLOSED'
  detailCount: number
  totalAmount: number
  remark: string | null
  createTime: string
}

/** 计件明细行 */
export interface PieceWageDetailRow {
  id: number
  periodId: number
  period: string
  employeeId: number
  empNo: string | null
  employeeName: string | null
  workOrderId: number
  workOrderNo: string
  materialId: number
  materialCode: string | null
  materialName: string | null
  processName: string
  qty: number
  unitPrice: number
  amount: number
  createTime: string
}

/** 计件按员工合计行 */
export interface PieceWageEmployeeSummaryRow {
  employeeId: number
  empNo: string | null
  employeeName: string | null
  detailCount: number
  totalQty: number
  totalAmount: number
}

/** 归集结果 */
export interface PieceWageCollectResult {
  periodId: number
  period: string
  candidateOrders: number
  detailCount: number
  totalAmount: number
  skippedNoPrice: number
  skippedNoEmployee: number
}

export const pieceWageApi = {
  page: (params: { pageNum: number; pageSize: number }) =>
    get<PageResult<PieceWageRow>>('/hr/piece-wages', params),

  details: (period: string, params: { pageNum: number; pageSize: number; employeeId?: number }) =>
    get<PageResult<PieceWageDetailRow>>(`/hr/piece-wages/${period}/details`, params),

  employeeSummary: (period: string) =>
    get<PieceWageEmployeeSummaryRow[]>(`/hr/piece-wages/${period}/summary`),

  collect: (period: string) => post<PieceWageCollectResult>(`/hr/piece-wages/${period}/collect`),

  close: (period: string) => post<void>(`/hr/piece-wages/${period}/close`),
}

// ==================== 工资汇总 ====================

/** 工资汇总行：计件 + 加班费 − 缺勤扣款 */
export interface WageSummaryRow {
  employeeId: number
  empNo: string
  employeeName: string
  deptName: string | null
  baseSalary: number
  pieceAmount: number
  overtimeDays: number
  overtimePay: number
  absentDays: number
  absentDeduct: number
  totalAmount: number
}

export const wageApi = {
  summary: (month: string) => get<WageSummaryRow[]>('/hr/wages/summary', { month }),
}

// ==================== 支撑数据 ====================

export const supportApi = {
  depts: () => get<DeptBrief[]>('/hr/support/depts'),
  materials: (keyword?: string) => get<MaterialBrief[]>('/hr/support/materials', { keyword }),
  employees: (keyword?: string) => get<EmployeeOption[]>('/hr/support/employees', { keyword }),
}
