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
  level: string
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
  level?: string
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

// ===================== 价格中心（V131） =====================

/** 采购价格流水 */
export interface PriceRecord {
  id: number
  supplierId: number
  supplierName: string | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  price: number
  sourceType: string
  sourceTypeLabel: string
  sourceNo: string
  priceDate: string
  remark: string | null
  createTime: string
}

/** 录单参照（合同价优先 + 最近成交价） */
export interface PriceRef {
  supplierId: number
  materialId: number
  contract: {
    id: number
    price: number
    effectiveFrom: string
    effectiveTo: string
    remark: string | null
  } | null
  last: {
    id: number
    price: number
    sourceType: string
    sourceTypeLabel: string
    sourceNo: string
    priceDate: string
  } | null
}

/** 多供应商价格对比行 */
export interface PriceCompareRow {
  supplierId: number
  supplierName: string | null
  times: number
  minPrice: number
  maxPrice: number
  avgPrice: number
  lastPrice: number
  lastDate: string | null
  contractPrice: number | null
}

/** 采购合同价 */
export interface PriceContract {
  id: number
  supplierId: number
  supplierName: string | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  contractPrice: number
  effectiveFrom: string
  effectiveTo: string
  status: number
  remark: string | null
}

/** 销售等级价 */
export interface PriceLevel {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  level: string
  price: number
  status: number
  remark: string | null
}

/** 销售批量价 */
export interface PriceQty {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  minQty: number
  price: number
  status: number
  remark: string | null
}

/** 特价审批申请 */
export interface PriceSpecial {
  id: number
  customerId: number
  customerCode: string | null
  customerName: string | null
  customerLevel: string | null
  materialId: number
  materialCode: string | null
  materialName: string | null
  specialPrice: number
  applyReason: string | null
  validFrom: string | null
  validTo: string | null
  status: number
  wfStarted: number
  auditBy: number | null
  auditTime: string | null
  auditRemark: string | null
  remark: string | null
  createTime: string
}

/** 定价引擎报价参照条目 */
export interface PricingRefItem {
  strategy: string
  label: string
  price: number
  hit: boolean
}

/** 定价引擎报价结果 */
export interface PricingQuote {
  customerId: number
  customerLevel: string
  materialId: number
  materialCode: string | null
  materialName: string | null
  qty: number
  unitPrice: number | null
  hitStrategy: string
  hitStrategyLabel: string
  hitReason: string
  references: PricingRefItem[]
}

/** 价格流水补录表单 */
export interface PriceRecordPayload {
  supplierId: number
  materialId: number
  price: number
  priceDate: string
  sourceNo?: string
  remark?: string
}

/** 合同价表单 */
export interface PriceContractPayload {
  supplierId: number
  materialId: number
  contractPrice: number
  effectiveFrom: string
  effectiveTo: string
  status: number
  remark?: string
}

/** 等级价表单 */
export interface PriceLevelPayload {
  materialId: number
  level: string
  price: number
  status: number
  remark?: string
}

/** 批量价表单 */
export interface PriceQtyPayload {
  materialId: number
  minQty: number
  price: number
  status: number
  remark?: string
}

/** 特价申请表单 */
export interface PriceSpecialPayload {
  customerId: number
  materialId: number
  specialPrice: number
  applyReason?: string
  validFrom?: string | null
  validTo?: string | null
  remark?: string
}

// ---- 采购价格库（录单参照接口，登录即可） ----

/** 价格流水分页（价格中心） */
export const getPriceRecords = (params: {
  pageNum: number
  pageSize: number
  supplierId?: number
  materialId?: number
  sourceType?: string
}) =>
  get<import('@/api/request').PageResult<PriceRecord>>('/basedata/prices/records', params)

/** 录单参照：合同价优先 + 最近一次成交价 */
export const getPriceRef = (supplierId: number, materialId: number) =>
  get<PriceRef>('/basedata/prices/last', { supplierId, materialId })

/** 历史价分页（日期倒序；supplierId 空=全部供应商） */
export const getPriceHistory = (materialId: number, supplierId: number | undefined, pageSize = 3) =>
  get<import('@/api/request').PageResult<PriceRecord>>('/basedata/prices/history', {
    materialId,
    supplierId,
    pageNum: 1,
    pageSize,
  })

/** 多供应商同物料价格对比 */
export const getPriceCompare = (materialId: number) =>
  get<PriceCompareRow[]>('/basedata/prices/compare', { materialId })

/** 手工补录价格流水 */
export const createPriceRecord = (data: PriceRecordPayload) =>
  post<number>('/basedata/prices/records', data)

/** 删除价格流水 */
export const deletePriceRecord = (id: number) =>
  del<void>(`/basedata/prices/records/${id}`)

// ---- 合同价 ----

/** 合同价分页 */
export const getPriceContracts = (params: {
  pageNum: number
  pageSize: number
  supplierId?: number
  materialId?: number
  status?: number
}) =>
  get<import('@/api/request').PageResult<PriceContract>>('/basedata/price-contracts', params)

export const createPriceContract = (data: PriceContractPayload) =>
  post<number>('/basedata/price-contracts', data)

export const updatePriceContract = (id: number, data: PriceContractPayload) =>
  put<void>(`/basedata/price-contracts/${id}`, data)

export const deletePriceContract = (id: number) =>
  del<void>(`/basedata/price-contracts/${id}`)

// ---- 等级价 ----

/** 等级价分页 */
export const getPriceLevels = (params: {
  pageNum: number
  pageSize: number
  materialId?: number
  level?: string
  status?: number
}) =>
  get<import('@/api/request').PageResult<PriceLevel>>('/basedata/price-levels', params)

export const createPriceLevel = (data: PriceLevelPayload) =>
  post<number>('/basedata/price-levels', data)

export const updatePriceLevel = (id: number, data: PriceLevelPayload) =>
  put<void>(`/basedata/price-levels/${id}`, data)

export const deletePriceLevel = (id: number) => del<void>(`/basedata/price-levels/${id}`)

// ---- 批量价 ----

/** 批量价分页 */
export const getPriceQtys = (params: {
  pageNum: number
  pageSize: number
  materialId?: number
  status?: number
}) =>
  get<import('@/api/request').PageResult<PriceQty>>('/basedata/price-qtys', params)

export const createPriceQty = (data: PriceQtyPayload) =>
  post<number>('/basedata/price-qtys', data)

export const updatePriceQty = (id: number, data: PriceQtyPayload) =>
  put<void>(`/basedata/price-qtys/${id}`, data)

export const deletePriceQty = (id: number) => del<void>(`/basedata/price-qtys/${id}`)

// ---- 特价审批 ----

/** 特价申请分页 */
export const getPriceSpecials = (params: {
  pageNum: number
  pageSize: number
  customerId?: number
  materialId?: number
  status?: number
}) =>
  get<import('@/api/request').PageResult<PriceSpecial>>('/basedata/price-special', params)

export const applyPriceSpecial = (data: PriceSpecialPayload) =>
  post<number>('/basedata/price-special', data)

export const approvePriceSpecial = (id: number, remark?: string) =>
  put<void>(`/basedata/price-special/${id}/approve`, { remark })

export const rejectPriceSpecial = (id: number, remark?: string) =>
  put<void>(`/basedata/price-special/${id}/reject`, { remark })

export const deletePriceSpecial = (id: number) =>
  del<void>(`/basedata/price-special/${id}`)

// ---- 定价引擎（录单参照，登录即可） ----

/** 报价：客户+物料+数量 → 建议单价 + 命中策略 */
export const getPricingQuote = (customerId: number, materialId: number, qty?: number) =>
  get<PricingQuote>('/basedata/pricing/quote', { customerId, materialId, qty })

// ---- 供应商准入（V141） ----

/** 供应商准入申请（对应后端 SupplierAdmitVO） */
export interface SupplierAdmit {
  id: number
  supplierId: number | null
  supplierCode: string | null
  supplierName: string
  creditCode: string | null
  contact: string | null
  phone: string | null
  address: string | null
  licenseFile: string | null
  certFiles: string | null
  bankInfo: string | null
  applyReason: string | null
  status: number
  wfStarted: number
  auditBy: number | null
  auditTime: string | null
  auditRemark: string | null
  blacklistTime: string | null
  blacklistReason: string | null
  remark: string | null
  createTime: string | null
}

/** 准入登记/编辑表单（对应后端 SupplierAdmitForm） */
export interface SupplierAdmitPayload {
  id?: number
  supplierName: string
  creditCode?: string
  contact?: string
  phone?: string
  address?: string
  licenseFile?: string
  certFiles?: string
  bankInfo?: string
  applyReason?: string
  remark?: string
  submit?: boolean
}

/** 准入申请分页 */
export const getSupplierAdmits = (params: {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number
}) => get<import('@/api/request').PageResult<SupplierAdmit>>('/basedata/supplier-admit', params)

export const getSupplierAdmit = (id: number) =>
  get<SupplierAdmit>(`/basedata/supplier-admit/${id}`)

export const saveSupplierAdmit = (data: SupplierAdmitPayload) =>
  post<number>('/basedata/supplier-admit', data)

export const updateSupplierAdmit = (id: number, data: SupplierAdmitPayload) =>
  put<void>(`/basedata/supplier-admit/${id}`, data)

export const submitSupplierAdmit = (id: number) =>
  put<void>(`/basedata/supplier-admit/${id}/submit`)

export const approveSupplierAdmit = (id: number, remark?: string) =>
  put<void>(`/basedata/supplier-admit/${id}/approve`, { remark })

export const rejectSupplierAdmit = (id: number, remark?: string) =>
  put<void>(`/basedata/supplier-admit/${id}/reject`, { remark })

export const blacklistSupplierAdmit = (id: number, reason?: string) =>
  put<void>(`/basedata/supplier-admit/${id}/blacklist`, { reason })

export const unblacklistSupplierAdmit = (id: number) =>
  put<void>(`/basedata/supplier-admit/${id}/unblacklist`)

export const deleteSupplierAdmit = (id: number) =>
  del<void>(`/basedata/supplier-admit/${id}`)

// ===================== T7 单据联查 / 通用红冲 =====================

/** 联查链节点（后端 DocChainNodeVO） */
export interface DocChainNode {
  docType: string
  docId: number
  docNo: string
  docName: string | null
  bizDate: string | null
  amount: number | null
  status: string | null
  reversed: boolean
}

/** 联查结果（后端 DocChainVO） */
export interface DocChain {
  anchor: DocChainNode
  upstream: DocChainNode[]
  downstream: DocChainNode[]
}

/** 红冲结果（后端 ReverseResultVO） */
export interface ReverseResult {
  docType: string
  reverseDocId: number
  reverseDocNo: string
  reverseAmount: number
}

/** 联查支持的 bizType 选项（与后端 DocTypes 常量对齐） */
export const DOC_CHAIN_TYPES: { value: string; label: string }[] = [
  { value: 'SAL_QUOTATION', label: '销售报价单' },
  { value: 'SAL_ORDER', label: '销售订单' },
  { value: 'SAL_OUTSTOCK', label: '销售出库单' },
  { value: 'SAL_RETURN', label: '销售退货单' },
  { value: 'PUR_ORDER', label: '采购订单' },
  { value: 'PUR_INSTOCK', label: '采购入库单' },
  { value: 'PUR_RETURN', label: '采购退货单' },
  { value: 'SALES_INVOICE', label: '销售发票' },
  { value: 'PURCHASE_INVOICE', label: '采购发票' },
  { value: 'RECEIVABLE', label: '应收单' },
  { value: 'PAYABLE', label: '应付单' },
  { value: 'RECEIPT', label: '收款单' },
  { value: 'PAYMENT', label: '付款单' },
  { value: 'VOUCHER', label: '记账凭证' },
]

/** 支持红冲的 docType → REST 路径映射（POST /api/...） */
const REVERSE_PATH: Record<string, string> = {
  PUR_INSTOCK: '/purchase/instock',
  PUR_RETURN: '/purchase/return',
  SAL_OUTSTOCK: '/sales/outstock',
  SAL_RETURN: '/sales/return',
  RECEIPT: '/finance/receipt',
  PAYMENT: '/finance/payment',
}

/** 红冲按钮的权限码映射（与 sys_menu V541 种子一致） */
export const REVERSE_PERM: Record<string, string> = {
  PUR_INSTOCK: 'purchase:instock:reverse',
  PUR_RETURN: 'purchase:return:reverse',
  SAL_OUTSTOCK: 'sales:outstock:reverse',
  SAL_RETURN: 'sales:return:reverse',
  RECEIPT: 'finance:receipt:reverse',
  PAYMENT: 'finance:payment:reverse',
}

/** 单据联查：GET /api/basedata/doc-chain?bizType=&bizId= */
export const fetchDocChain = (bizType: string, bizId: number) =>
  get<DocChain>('/basedata/doc-chain', { bizType, bizId })

/** 通用红冲：POST /api/{module}/{docType}/{id}/reverse */
export const reverseDoc = (docType: string, id: number, reason?: string) =>
  post<ReverseResult>(`${REVERSE_PATH[docType]}/${id}/reverse`, { reason: reason ?? '' })

/** docType 是否可发起红冲 */
export const isReversible = (docType: string) => docType in REVERSE_PATH
