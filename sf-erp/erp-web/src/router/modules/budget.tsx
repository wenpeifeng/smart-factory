import type { ComponentType } from 'react'

/**
 * 预算管理模块路由片段（由主路由在集成时合并，path 无首斜杠）。
 * 页面路径与 MainLayout 预算管理菜单保持一致：
 * budget/plans | budget/analysis。
 */
export const budgetRoutes: { path: string; lazy: () => Promise<{ default: ComponentType }> }[] = [
  {
    path: 'budget/plans',
    lazy: () => import('@/pages/budget/plans/PlanList'),
  },
  {
    path: 'budget/analysis',
    lazy: () => import('@/pages/budget/analysis/AnalysisPage'),
  },
]
