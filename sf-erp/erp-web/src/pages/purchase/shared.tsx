import { Tag } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { optionsApi, type BaseOption, type DocStatus, type OrderStatus } from '@/api/purchase'

/**
 * 采购页面共享件：状态标签映射、基础资料选项 hooks、金额/数量格式化。
 * 仅供 pages/purchase/* 使用，不对外暴露。
 */

/** 采购订单状态 -> 标签文案/颜色（与后端 pur_order.status 注释一致） */
const ORDER_STATUS_MAP: Record<OrderStatus, { text: string; color: string }> = {
  0: { text: '草稿', color: 'default' },
  10: { text: '已审核', color: 'processing' },
  20: { text: '部分入库', color: 'orange' },
  30: { text: '已完成', color: 'success' },
  90: { text: '已关闭', color: 'error' },
}

/** 入库/退货单状态（0草稿 10已审核） */
const DOC_STATUS_MAP: Record<DocStatus, { text: string; color: string }> = {
  0: { text: '草稿', color: 'default' },
  10: { text: '已审核', color: 'processing' },
}

/** 采购订单状态标签 */
export function OrderStatusTag({ status }: { status: OrderStatus }) {
  const cfg = ORDER_STATUS_MAP[status] ?? { text: `未知(${status})`, color: 'default' }
  return <Tag color={cfg.color}>{cfg.text}</Tag>
}

/** 入库/退货单状态标签 */
export function DocStatusTag({ status }: { status: DocStatus }) {
  const cfg = DOC_STATUS_MAP[status] ?? { text: `未知(${status})`, color: 'default' }
  return <Tag color={cfg.color}>{cfg.text}</Tag>
}

/** 订单状态筛选选项（下拉） */
export const ORDER_STATUS_OPTIONS = Object.entries(ORDER_STATUS_MAP).map(([value, cfg]) => ({
  value: Number(value),
  label: cfg.text,
}))

/** 入库/退货状态筛选选项 */
export const DOC_STATUS_OPTIONS = Object.entries(DOC_STATUS_MAP).map(([value, cfg]) => ({
  value: Number(value),
  label: cfg.text,
}))

/** 金额显示：千分位 + 2 位小数（与后端 HALF_UP 2 位口径一致） */
export function fmtAmount(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 数量显示：最多 4 位小数（与后端 DECIMAL(18,4) 口径一致） */
export function fmtQty(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

/** 供应商下拉选项（基于资料，供筛选与单据表单复用） */
export function useSupplierOptions() {
  return useQuery({
    queryKey: ['purchase', 'options', 'suppliers'],
    queryFn: () => optionsApi.suppliers(),
    staleTime: 5 * 60 * 1000,
  })
}

/** 物料下拉选项 */
export function useMaterialOptions() {
  return useQuery({
    queryKey: ['purchase', 'options', 'materials'],
    queryFn: () => optionsApi.materials(),
    staleTime: 5 * 60 * 1000,
  })
}

/** 仓库下拉选项 */
export function useWarehouseOptions() {
  return useQuery({
    queryKey: ['purchase', 'options', 'warehouses'],
    queryFn: () => optionsApi.warehouses(),
    staleTime: 5 * 60 * 1000,
  })
}

/** 选项下拉的展示文案：编码 + 名称（ERP 习惯双码显示） */
export function optionLabel(o: BaseOption): string {
  return `${o.code} ${o.name}`
}

/** 行内数量×单价 -> 金额（仅用于表单实时预览，落库金额以服务端计算为准） */
export function lineAmount(qty: number | null | undefined, price: number | null | undefined): number {
  if (qty == null || price == null) return 0
  return Math.round(qty * price * 100) / 100
}
