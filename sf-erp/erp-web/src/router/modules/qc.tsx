import type { ComponentType } from 'react'

/**
 * 质量管理模块路由片段（由主路由在集成时合并，path 无首斜杠）。
 * 页面路径与 MainLayout 质量管理菜单保持一致：
 * qc/inspects | qc/rejects | qc/templates。
 */
export const qcRoutes: { path: string; lazy: () => Promise<{ default: ComponentType }> }[] = [
  {
    path: 'qc/inspects',
    lazy: () => import('@/pages/qc/inspects/InspectList'),
  },
  {
    path: 'qc/rejects',
    lazy: () => import('@/pages/qc/rejects/RejectList'),
  },
  {
    path: 'qc/templates',
    lazy: () => import('@/pages/qc/templates/TemplateList'),
  },
]
