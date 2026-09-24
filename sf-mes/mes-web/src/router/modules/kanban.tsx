/** 看板模块路由片段 */
export const kanbanRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'kanban/andons', lazy: () => import('@/pages/kanban/andons/AndonList') },
]
