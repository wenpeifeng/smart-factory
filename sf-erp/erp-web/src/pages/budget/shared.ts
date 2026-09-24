import type { BudgetPlanStatus, BudgetControlWay } from '@/api/budget'
import type { StatusSemantic } from '@/components/StatusTag'

/**
 * 预算管理模块页面公共工具（金额格式化/状态语义/月份工具）。
 * 金额格式全站统一：千分位 + 2 位小数。
 */

/** 金额格式化：千分位 + 2 位小数；空值显示 - */
export function fmtMoney(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 方案状态 → StatusTag 语义（全站颜色只能来自语义表） */
export const PLAN_STATUS_SEMANTIC: Record<BudgetPlanStatus, StatusSemantic> = {
  DRAFT: 'default',
  RELEASED: 'success',
  CLOSED: 'default',
}

/** 控制方式文案 */
export const CONTROL_WAY_TEXT: Record<BudgetControlWay, string> = {
  WARN: '超预算警告放行',
  BLOCK: '超预算阻止',
}

/** 月份下标（0-11 → 1-12 月） */
export const MONTH_INDEXES = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11] as const

/** 月份列标题（1月..12月） */
export const MONTH_TITLES = MONTH_INDEXES.map((i) => `${i + 1}月`)
