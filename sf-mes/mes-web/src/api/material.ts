import { get, post, put, del } from '@/api/request'
import type { PageResult } from '@/api/request'

/**
 * 物料模块 API：领料/退料/线边仓/批次SN/标签。类型与后端 com.mes.material VO/Form 对齐。
 */

/** 领料单 */
export interface Picking {
  id: number
  pickNo: string
  workorderId: number
  woNo: string
  warehouseId: number
  status: string
  pickType: string
  auditTime: string | null
  auditor: string | null
  erpPushed: number
  remark: string | null
  createTime: string
  items?: PickingItem[]
}

export interface PickingItem {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  batchNo: string | null
  qty: number
}

export interface PickingForm {
  workorderId: number
  warehouseId: number
  pickType?: string
  remark?: string
  items: { materialId: number; batchNo: string; qty: number; remark?: string }[]
}

/** 退料单 */
export interface MaterialReturn {
  id: number
  returnNo: string
  workorderId: number | null
  woNo: string | null
  returnType: string
  warehouseId: number
  status: string
  erpPushed: number
  remark: string | null
  createTime: string
  items?: { id: number; materialId: number; materialCode: string | null; materialName: string | null; batchNo: string | null; qty: number }[]
}

export interface ReturnForm {
  workorderId?: number | null
  returnType?: string
  warehouseId: number
  remark?: string
  items: { materialId: number; batchNo?: string; qty: number; remark?: string }[]
}

/** 线边仓库存 */
export interface ShopStock {
  id: number
  materialId: number
  materialCode: string | null
  materialName: string | null
  batchNo: string
  workcenterId: number | null
  qty: number
}

/** 批次/SN 档案 */
export interface BatchSn {
  id: number
  objType: string
  objNo: string
  materialId: number
  materialCode: string | null
  materialName: string | null
  sourceType: string | null
  sourceNo: string | null
  qty: number
  status: string
  parentSn: string | null
  createTime: string
}

export function getPickings(params: Record<string, unknown>) {
  return get<PageResult<Picking>>('/material/pickings', params)
}

export function getPickingDetail(id: number) {
  return get<Picking>(`/material/pickings/${id}`)
}

export function createPicking(form: PickingForm) {
  return post<number>('/material/pickings', form)
}

export function deletePicking(id: number) {
  return del<void>(`/material/pickings/${id}`)
}

export function auditPicking(id: number) {
  return put<void>(`/material/pickings/${id}/audit`)
}

export function getReturns(params: Record<string, unknown>) {
  return get<PageResult<MaterialReturn>>('/material/returns', params)
}

export function getReturnDetail(id: number) {
  return get<MaterialReturn>(`/material/returns/${id}`)
}

export function createReturn(form: ReturnForm) {
  return post<number>('/material/returns', form)
}

export function deleteReturn(id: number) {
  return del<void>(`/material/returns/${id}`)
}

export function auditReturn(id: number) {
  return put<void>(`/material/returns/${id}/audit`)
}

export function getShopStocks(params: Record<string, unknown>) {
  return get<PageResult<ShopStock>>('/material/shop-stocks', params)
}

export function getBatchSns(params: Record<string, unknown>) {
  return get<PageResult<BatchSn>>('/material/batch-sns', params)
}

export function registerBatchSn(form: { objType: string; objNo?: string; materialId: number; qty: number; sourceType?: string; sourceNo?: string; workorderId?: number; supplierId?: number; parentSn?: string }) {
  return post<number>('/material/batch-sns', form)
}

export function printLabel(form: { objType: string; objId: number; objNo: string; template: string; copies?: number; printer?: string }) {
  return post<number>('/material/labels', form)
}
