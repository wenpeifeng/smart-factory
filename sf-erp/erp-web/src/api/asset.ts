import { get, post, put, del, type PageResult } from './request'

/** 资产状态：在用 / 闲置 / 处置 */
export type AssetStatus = 'IN_USE' | 'IDLE' | 'DISPOSED'

/** 折旧方法：平均年限法（工作量法预留） */
export type DepreciationMethod = 'AVG'

/** 折旧期间状态：开放 / 已计提关闭 */
export type DepreciationPeriodStatus = 'OPEN' | 'CLOSED'

/** 处置方式：出售 / 报废 / 捐赠 */
export type DisposeMethod = 'SELL' | 'SCRAP' | 'DONATE'

/** 资产类别（含科目映射回显） */
export interface AssetCategoryVO {
  id: number
  code: string
  name: string
  depreciationMethod: DepreciationMethod
  expectedLifeMonths: number
  residualRate: number
  assetSubjectId: number
  assetSubjectLabel: string | null
  accumulatedDepreciationSubjectId: number
  accumulatedSubjectLabel: string | null
  depreciationExpenseSubjectId: number
  expenseSubjectLabel: string | null
  remark: string | null
  createTime: string
}

/** 资产类别表单 */
export interface AssetCategoryForm {
  id?: number
  code: string
  name: string
  depreciationMethod: DepreciationMethod
  expectedLifeMonths: number
  residualRate: number
  assetSubjectId: number
  accumulatedDepreciationSubjectId: number
  depreciationExpenseSubjectId: number
  remark?: string
}

/** 资产卡片（净值由后端数据库生成列带出） */
export interface AssetVO {
  id: number
  assetCode: string
  name: string
  categoryId: number
  categoryName: string | null
  spec: string | null
  supplierId: number | null
  supplierName: string | null
  location: string | null
  deptId: number | null
  deptName: string | null
  keeper: string | null
  originalValue: number
  residualValue: number
  expectedLifeMonths: number
  depreciatedMonths: number
  accumulatedDepreciation: number
  netValue: number
  accountDate: string
  status: AssetStatus
  entryVoucherId: number | null
  disposeMethod: DisposeMethod | null
  disposeIncome: number | null
  disposeTime: string | null
  disposeVoucherId: number | null
  remark: string | null
  createTime: string
}

/** 资产卡片表单（新增时残值/年限可空，取类别默认；状态仅允许在用/闲置互转） */
export interface AssetForm {
  id?: number
  name: string
  categoryId: number
  spec?: string
  supplierId?: number
  location?: string
  deptId?: number
  keeper?: string
  originalValue: number
  residualValue?: number
  expectedLifeMonths?: number
  accountDate?: string
  status?: AssetStatus
  remark?: string
}

/** 资产处置表单 */
export interface DisposeForm {
  method: DisposeMethod
  income: number
  remark?: string
}

/** 处置结果 */
export interface DisposeResultVO {
  assetId: number
  assetCode: string
  netValue: number
  income: number
  voucherId: number
  voucherNo: string
}

/** 折旧期间（列表行） */
export interface DepreciationPeriodVO {
  id: number
  period: string
  status: DepreciationPeriodStatus
  voucherId: number | null
  voucherNo: string | null
  assetCount: number
  totalAmount: number
  createTime: string
  updateTime: string
}

/** 折旧明细行 */
export interface DepreciationDetailVO {
  id: number
  assetId: number
  assetCode: string | null
  assetName: string | null
  categoryName: string | null
  deptName: string | null
  monthAmount: number
  voucherId: number
}

/** 折旧期间详情（头 + 明细） */
export interface DepreciationPeriodDetailVO extends DepreciationPeriodVO {
  details: DepreciationDetailVO[]
}

/** 折旧计提执行结果 */
export interface DepreciationRunVO {
  period: string
  assetCount: number
  totalAmount: number
  voucherId: number | null
  voucherNo: string | null
  message: string | null
}

/** 统计单行（部门或类别维度） */
export interface StatisticsRowVO {
  dimensionId: number | null
  dimensionName: string
  originalValue: number
  accumulatedDepreciation: number
  netValue: number
}

/** 资产统计（截至期间，部门/类别双维度） */
export interface StatisticsVO {
  period: string
  byDept: StatisticsRowVO[]
  byCategory: StatisticsRowVO[]
  total: { originalValue: number; accumulatedDepreciation: number; netValue: number }
}

// ---------------- 资产类别 ----------------

/** 类别全量列表（下拉与维护共用） */
export const listCategories = () => get<AssetCategoryVO[]>('/asset/categories')

/** 新增类别 */
export const createCategory = (form: AssetCategoryForm) => post<number>('/asset/categories', form)

/** 修改类别 */
export const updateCategory = (id: number, form: AssetCategoryForm) =>
  put<void>(`/asset/categories/${id}`, form)

/** 删除类别（有资产引用时后端拒绝） */
export const deleteCategory = (id: number) => del<void>(`/asset/categories/${id}`)

// ---------------- 资产卡片 ----------------

/** 资产分页查询（类别/状态/部门过滤，编码/名称模糊） */
export const pageAssets = (params: {
  pageNum: number
  pageSize: number
  categoryId?: number
  status?: AssetStatus
  deptId?: number
  keyword?: string
}) => get<PageResult<AssetVO>>('/asset/assets', params)

/** 资产详情 */
export const getAsset = (id: number) => get<AssetVO>(`/asset/assets/${id}`)

/** 新增资产（自动生成 ZC 编码 + 入账凭证） */
export const createAsset = (form: AssetForm) => post<number>('/asset/assets', form)

/** 修改资产（财务字段计提后锁定） */
export const updateAsset = (id: number, form: AssetForm) => put<void>(`/asset/assets/${id}`, form)

/** 删除资产（已计提/已处置拒绝） */
export const deleteAsset = (id: number) => del<void>(`/asset/assets/${id}`)

/** 资产处置（净值转固定资产清理，自动平衡凭证） */
export const disposeAsset = (id: number, form: DisposeForm) =>
  post<DisposeResultVO>(`/asset/assets/${id}/dispose`, form)

// ---------------- 折旧计提 ----------------

/** 折旧期间分页（期间倒序） */
export const pageDepreciations = (params: { pageNum: number; pageSize: number }) =>
  get<PageResult<DepreciationPeriodVO>>('/asset/depreciations', params)

/** 期间计提明细 */
export const getDepreciationDetail = (period: string) =>
  get<DepreciationPeriodDetailVO>(`/asset/depreciations/${period}`)

/** 执行期间折旧计提（重复运行后端拒绝） */
export const runDepreciation = (period: string) =>
  post<DepreciationRunVO>(`/asset/depreciations/${period}/run`)

/** 资产统计：部门/类别维度原值/累计折旧/净值（period 缺省当月） */
export const getStatistics = (period?: string) =>
  get<StatisticsVO>('/asset/statistics', period ? { period } : undefined)
