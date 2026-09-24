import { get, post, put, del } from '@/api/request'

/**
 * 基础资料模块 API：物料/物料分类/计量单位/客户/供应商/仓库/库位/结算方式。
 * 类型与后端 com.erp.basedata 的 VO/Form 对齐；分页响应结构见 api/request.ts。
 */

// ===================== 类型定义（与后端 VO 对齐） =====================

/** 计量单位 */
export interface BaseUnit {
  id: number
  groupCode: string
  name: string
  rate: number
  isBase: number
  status: number
  remark: string | null
  createTime: string
}

/** 物料分类（树节点） */
export interface MaterialCategory {
  id: number
  parentId: number
  code: string
  name: string
  sort: number
  status: number
  remark: string | null
  children: MaterialCategory[]
  createTime: string
}

/** 物料 */
export interface Material {
  id: number
  code: string
  name: string
  spec: string | null
  categoryId: number | null
  categoryName: string | null
  unitId: number | null
  unitName: string | null
  barcode: string | null
  batchManaged: number
  serialManaged: number
  safetyStock: number
  refCost: number
  status: number
  remark: string | null
  createTime: string
}

/** 客户 */
export interface Customer {
  id: number
  code: string
  name: string
  contact: string | null
  phone: string | null
  address: string | null
  creditCode: string | null
  settleTypeId: number | null
  settleTypeName: string | null
  creditLimit: number
  initReceivable: number
  status: number
  remark: string | null
  createTime: string
}

/** 供应商 */
export interface Supplier {
  id: number
  code: string
  name: string
  contact: string | null
  phone: string | null
  address: string | null
  creditCode: string | null
  settleTypeId: number | null
  settleTypeName: string | null
  initPayable: number
  status: number
  remark: string | null
  createTime: string
}

/** 仓库 */
export interface Warehouse {
  id: number
  code: string
  name: string
  manager: string | null
  address: string | null
  status: number
  remark: string | null
  createTime: string
}

/** 库位 */
export interface BaseLocation {
  id: number
  warehouseId: number
  code: string
  name: string
  capacity: number
  status: number
  remark: string | null
  createTime: string
}

/** 结算方式 */
export interface SettleType {
  id: number
  code: string
  name: string
  days: number
  status: number
  remark: string | null
  createTime: string
}

/** 物料 CSV 导入结果 */
export interface MaterialImportResult {
  total: number
  success: number
  fail: number
  errors: string[]
}

// ===================== 提交负载类型（与后端 Form 对齐） =====================

/** 计量单位表单 */
export interface UnitPayload {
  groupCode: string
  name: string
  rate: number
  isBase: number
  status: number
  remark?: string
}

/** 物料分类表单 */
export interface CategoryPayload {
  parentId: number
  code: string
  name: string
  sort: number
  status: number
  remark?: string
}

/** 物料表单（编码留空后端自动生成 WL+日期+流水） */
export interface MaterialPayload {
  code?: string
  name: string
  spec?: string
  categoryId?: number | null
  unitId?: number | null
  barcode?: string
  batchManaged: number
  serialManaged: number
  safetyStock?: number
  refCost?: number
  status: number
  remark?: string
}

/** 客户表单（编码留空后端自动生成 KH+日期+流水） */
export interface CustomerPayload {
  code?: string
  name: string
  contact?: string
  phone?: string
  address?: string
  creditCode?: string
  settleTypeId?: number | null
  creditLimit?: number
  initReceivable?: number
  status: number
  remark?: string
}

/** 供应商表单（编码留空后端自动生成 GYS+日期+流水） */
export interface SupplierPayload {
  code?: string
  name: string
  contact?: string
  phone?: string
  address?: string
  creditCode?: string
  settleTypeId?: number | null
  initPayable?: number
  status: number
  remark?: string
}

/** 仓库表单（编码留空后端自动生成 CK+日期+流水） */
export interface WarehousePayload {
  code?: string
  name: string
  manager?: string
  address?: string
  status: number
  remark?: string
}

/** 库位表单 */
export interface LocationPayload {
  warehouseId: number
  code: string
  name: string
  capacity?: number
  status: number
  remark?: string
}

/** 结算方式表单 */
export interface SettleTypePayload {
  code: string
  name: string
  days: number
  status: number
  remark?: string
}

// ===================== 计量单位 =====================

/** 分页查询计量单位 */
export const getUnits = (params: { pageNum: number; pageSize: number; keyword?: string }) =>
  get<import('@/api/request').PageResult<BaseUnit>>('/basedata/units', params)

/** 启用单位列表（下拉） */
export const getEnabledUnits = () => get<BaseUnit[]>('/basedata/units/enabled')

/** 新增单位 */
export const createUnit = (data: UnitPayload) => post<number>('/basedata/units', data)

/** 修改单位 */
export const updateUnit = (id: number, data: UnitPayload) => put<void>(`/basedata/units/${id}`, data)

/** 删除单位 */
export const deleteUnit = (id: number) => del<void>(`/basedata/units/${id}`)

// ===================== 物料分类 =====================

/** 分类树 */
export const getCategoryTree = () => get<MaterialCategory[]>('/basedata/material-categories/tree')

/** 新增分类 */
export const createCategory = (data: CategoryPayload) =>
  post<number>('/basedata/material-categories', data)

/** 修改分类 */
export const updateCategory = (id: number, data: CategoryPayload) =>
  put<void>(`/basedata/material-categories/${id}`, data)

/** 删除分类 */
export const deleteCategory = (id: number) => del<void>(`/basedata/material-categories/${id}`)

// ===================== 物料 =====================

/** 物料分页查询参数 */
export interface MaterialQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  categoryId?: number
  status?: number
}

/** 分页查询物料 */
export const getMaterials = (params: MaterialQuery) =>
  get<import('@/api/request').PageResult<Material>>(
    '/basedata/materials',
    params as unknown as Record<string, unknown>,
  )

/** 启用物料搜索（单据选择器） */
export const getEnabledMaterials = (keyword?: string) =>
  get<Material[]>('/basedata/materials/enabled', { keyword })

/** 新增物料 */
export const createMaterial = (data: MaterialPayload) => post<number>('/basedata/materials', data)

/** 修改物料 */
export const updateMaterial = (id: number, data: MaterialPayload) =>
  put<void>(`/basedata/materials/${id}`, data)

/** 启用/停用物料 */
export const changeMaterialStatus = (id: number, status: number) =>
  put<void>(`/basedata/materials/${id}/status?status=${status}`)

/** 删除物料 */
export const deleteMaterial = (id: number) => del<void>(`/basedata/materials/${id}`)

/** 导入物料 CSV（multipart） */
export const importMaterials = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return post<MaterialImportResult>('/basedata/materials/import', form)
}

/** 触发浏览器下载（CSV 导出/模板，后端直写流） */
export function downloadCsv(path: string, filename: string) {
  const a = document.createElement('a')
  a.href = `/api${path}`
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
}

// ===================== 客户 =====================

/** 分页查询客户 */
export const getCustomers = (params: { pageNum: number; pageSize: number; keyword?: string }) =>
  get<import('@/api/request').PageResult<Customer>>('/basedata/customers', params)

/** 启用客户搜索（单据选择器） */
export const getEnabledCustomers = (keyword?: string) =>
  get<Customer[]>('/basedata/customers/enabled', { keyword })

/** 新增客户 */
export const createCustomer = (data: CustomerPayload) => post<number>('/basedata/customers', data)

/** 修改客户 */
export const updateCustomer = (id: number, data: CustomerPayload) =>
  put<void>(`/basedata/customers/${id}`, data)

/** 删除客户 */
export const deleteCustomer = (id: number) => del<void>(`/basedata/customers/${id}`)

// ===================== 供应商 =====================

/** 分页查询供应商 */
export const getSuppliers = (params: { pageNum: number; pageSize: number; keyword?: string }) =>
  get<import('@/api/request').PageResult<Supplier>>('/basedata/suppliers', params)

/** 启用供应商搜索（单据选择器） */
export const getEnabledSuppliers = (keyword?: string) =>
  get<Supplier[]>('/basedata/suppliers/enabled', { keyword })

/** 新增供应商 */
export const createSupplier = (data: SupplierPayload) => post<number>('/basedata/suppliers', data)

/** 修改供应商 */
export const updateSupplier = (id: number, data: SupplierPayload) =>
  put<void>(`/basedata/suppliers/${id}`, data)

/** 删除供应商 */
export const deleteSupplier = (id: number) => del<void>(`/basedata/suppliers/${id}`)

// ===================== 仓库 / 库位 =====================

/** 分页查询仓库 */
export const getWarehouses = (params: { pageNum: number; pageSize: number; keyword?: string }) =>
  get<import('@/api/request').PageResult<Warehouse>>('/basedata/warehouses', params)

/** 启用仓库列表（下拉） */
export const getEnabledWarehouses = () => get<Warehouse[]>('/basedata/warehouses/enabled')

/** 新增仓库 */
export const createWarehouse = (data: WarehousePayload) => post<number>('/basedata/warehouses', data)

/** 修改仓库 */
export const updateWarehouse = (id: number, data: WarehousePayload) =>
  put<void>(`/basedata/warehouses/${id}`, data)

/** 删除仓库 */
export const deleteWarehouse = (id: number) => del<void>(`/basedata/warehouses/${id}`)

/** 查询仓库下库位列表 */
export const getLocations = (warehouseId: number) =>
  get<BaseLocation[]>(`/basedata/warehouses/${warehouseId}/locations`)

/** 新增库位 */
export const createLocation = (data: LocationPayload) =>
  post<number>('/basedata/warehouses/locations', data)

/** 修改库位 */
export const updateLocation = (id: number, data: LocationPayload) =>
  put<void>(`/basedata/warehouses/locations/${id}`, data)

/** 删除库位 */
export const deleteLocation = (id: number) => del<void>(`/basedata/warehouses/locations/${id}`)

// ===================== 结算方式 =====================

/** 全部结算方式（含停用） */
export const getSettleTypes = () => get<SettleType[]>('/basedata/settle-types')

/** 启用结算方式（下拉） */
export const getEnabledSettleTypes = () => get<SettleType[]>('/basedata/settle-types/enabled')

/** 新增结算方式 */
export const createSettleType = (data: SettleTypePayload) =>
  post<number>('/basedata/settle-types', data)

/** 修改结算方式 */
export const updateSettleType = (id: number, data: SettleTypePayload) =>
  put<void>(`/basedata/settle-types/${id}`, data)

/** 删除结算方式 */
export const deleteSettleType = (id: number) => del<void>(`/basedata/settle-types/${id}`)
