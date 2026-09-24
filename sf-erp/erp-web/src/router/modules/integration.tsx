import type { ComponentType } from 'react'

/**
 * 系统集成模块路由片段（由主路由在集成时合并，path 无首斜杠）。
 * 页面路径与「系统集成-集成监控」菜单保持一致：integration/monitor。
 */
export const integrationRoutes: { path: string; lazy: () => Promise<{ default: ComponentType }> }[] = [
  {
    path: 'integration/monitor',
    lazy: () => import('@/pages/integration/monitor/IntegrationMonitor'),
  },
]
