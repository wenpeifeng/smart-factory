import type { ComponentType } from 'react'

/**
 * 生产制造模块路由表（由主路由在集成时合并，path 无首斜杠）。
 */
export const mrpRoutes: { path: string; lazy: () => Promise<{ default: ComponentType }> }[] = [
  {
    path: 'mrp/bom',
    lazy: () => import('@/pages/mrp/bom/BomList'),
  },
  {
    path: 'mrp/mrp',
    lazy: () => import('@/pages/mrp/mrp/MrpPage'),
  },
  {
    path: 'mrp/routings',
    lazy: () => import('@/pages/mrp/routings/RoutingPage'),
  },
  {
    path: 'mrp/dispatch',
    lazy: () => import('@/pages/mrp/dispatch/DispatchPage'),
  },
  {
    path: 'mrp/workorders',
    lazy: () => import('@/pages/mrp/workorders/WorkOrderPage'),
  },
]
