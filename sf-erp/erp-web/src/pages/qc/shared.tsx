import { Tag } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { getEnabledMaterials, getEnabledSuppliers, getCategoryTree, type MaterialCategory } from '@/api/basedata'
import type { Material, Supplier } from '@/api/basedata'
import {
  INSPECT_TYPE_OPTIONS,
  BIZ_TYPE_OPTIONS,
  type InspectType,
  type InspectStatus,
  type InspectConclusion,
  type RejectDecision,
  type QcBizType,
} from '@/api/qc'

/**
 * 质量页面共享件：检验类型/状态/结论/处置决定的标签映射、基础资料选项 hooks、数量格式化。
 * 仅供 pages/qc/* 使用，不对外暴露。
 */

/** 检验类型 -> 标签（与后端 qc_template.inspect_type 取值一致） */
const INSPECT_TYPE_MAP: Record<InspectType, { text: string; color: string }> = {
  IQC: { text: 'IQC 来料', color: 'blue' },
  IPQC: { text: 'IPQC 过程', color: 'cyan' },
  FQC: { text: 'FQC 成品', color: 'geekblue' },
  OQC: { text: 'OQC 出货', color: 'purple' },
}

/** 检验单状态（0草稿 10已检验） */
const INSPECT_STATUS_MAP: Record<InspectStatus, { text: string; color: string }> = {
  0: { text: '草稿', color: 'default' },
  10: { text: '已检验', color: 'success' },
}

/** 检验结论（1合格 2让步接收 3退货） */
const CONCLUSION_MAP: Record<InspectConclusion, { text: string; color: string }> = {
  1: { text: '合格', color: 'success' },
  2: { text: '让步接收', color: 'orange' },
  3: { text: '退货', color: 'error' },
}

/** 处置决定（1退货 2返工 3让步接收 4报废） */
const DECISION_MAP: Record<RejectDecision, { text: string; color: string }> = {
  1: { text: '退货', color: 'error' },
  2: { text: '返工', color: 'processing' },
  3: { text: '让步接收', color: 'orange' },
  4: { text: '报废', color: 'default' },
}

/** 来源单据类型 -> 文案 */
const BIZ_TYPE_MAP: Record<QcBizType, string> = {
  pur_instock: '采购入库',
  mrp_work_order: '生产工单',
  sal_return: '销售退货',
}

export function InspectTypeTag({ type }: { type: InspectType }) {
  const cfg = INSPECT_TYPE_MAP[type] ?? { text: type, color: 'default' }
  return <Tag color={cfg.color}>{cfg.text}</Tag>
}

export function InspectStatusTag({ status }: { status: InspectStatus }) {
  const cfg = INSPECT_STATUS_MAP[status] ?? { text: `未知(${status})`, color: 'default' }
  return <Tag color={cfg.color}>{cfg.text}</Tag>
}

export function ConclusionTag({ conclusion }: { conclusion: InspectConclusion | null }) {
  if (conclusion == null) return <Tag>-</Tag>
  const cfg = CONCLUSION_MAP[conclusion] ?? { text: `未知(${conclusion})`, color: 'default' }
  return <Tag color={cfg.color}>{cfg.text}</Tag>
}

export function DecisionTag({ decision }: { decision: RejectDecision }) {
  const cfg = DECISION_MAP[decision] ?? { text: `未知(${decision})`, color: 'default' }
  return <Tag color={cfg.color}>{cfg.text}</Tag>
}

export function bizTypeText(type: QcBizType | null): string {
  if (!type) return '手工'
  return BIZ_TYPE_MAP[type] ?? type
}

/** 检验类型筛选选项 */
export const INSPECT_TYPE_FILTER = INSPECT_TYPE_OPTIONS

/** 来源单据类型选项（透出给页面复用，含"手工"由空值表达，不单列） */
export { BIZ_TYPE_OPTIONS }

/** 检验单状态筛选选项 */
export const INSPECT_STATUS_OPTIONS = Object.entries(INSPECT_STATUS_MAP).map(([value, cfg]) => ({
  value: Number(value),
  label: cfg.text,
}))

/** 检验结论筛选选项 */
export const CONCLUSION_OPTIONS = Object.entries(CONCLUSION_MAP).map(([value, cfg]) => ({
  value: Number(value),
  label: cfg.text,
}))

/** 处置决定筛选选项 */
export const DECISION_OPTIONS = Object.entries(DECISION_MAP).map(([value, cfg]) => ({
  value: Number(value),
  label: cfg.text,
}))

/** 数量显示：最多 4 位小数（与后端 DECIMAL(18,4) 口径一致） */
export function fmtQty(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

/** 物料选项 hook（检验单手工新增/筛选） */
export function useMaterialOptions() {
  return useQuery({
    queryKey: ['qc', 'options', 'materials'],
    queryFn: () => getEnabledMaterials(),
    select: (list: Material[]) => list.map((m) => ({ value: m.id, label: `${m.code} ${m.name}` })),
    staleTime: 5 * 60 * 1000,
  })
}

/** 供应商选项 hook（IQC 检验单手工新增） */
export function useSupplierOptions() {
  return useQuery({
    queryKey: ['qc', 'options', 'suppliers'],
    queryFn: () => getEnabledSuppliers(),
    select: (list: Supplier[]) => list.map((s) => ({ value: s.id, label: `${s.code} ${s.name}` })),
    staleTime: 5 * 60 * 1000,
  })
}

/** 物料分类树节点（TreeSelect 数据） */
interface CategoryTreeNode {
  title: string
  value: number
  children?: CategoryTreeNode[]
}

/** 物料分类树 → AntD TreeSelect 数据 */
export function categoryToTreeData(nodes: MaterialCategory[]): CategoryTreeNode[] {
  return nodes.map((n) => ({
    title: n.name,
    value: n.id,
    children: n.children.length ? categoryToTreeData(n.children) : undefined,
  }))
}

/** 物料分类树 hook（模板适用分类选择） */
export function useCategoryTree() {
  return useQuery({
    queryKey: ['qc', 'options', 'material-categories'],
    queryFn: getCategoryTree,
    staleTime: 5 * 60 * 1000,
  })
}
