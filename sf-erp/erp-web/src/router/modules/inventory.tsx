/**
 * 库存管理模块路由片段。
 * <p>
 * 按并行开发约定：各模块独立维护自己的路由片段，由主线程合并进 router/index.tsx。
 * path 不带首斜杠（挂载在主布局 children 下）；页面统一懒加载。
 * 菜单路径对应 MainLayout：/inventory/stock、/inventory/move、/inventory/check、/inventory/batch、
 * /inventory/tasks（WMS 作业任务）、/inventory/waves（波次管理）、/inventory/serials（序列号）。
 */
export const inventoryRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  {
    path: 'inventory/stock',
    lazy: () => import('@/pages/inventory/stock/StockList'),
  },
  {
    path: 'inventory/move',
    lazy: () => import('@/pages/inventory/move/MovePage'),
  },
  {
    path: 'inventory/check',
    lazy: () => import('@/pages/inventory/check/CheckList'),
  },
  {
    path: 'inventory/batch',
    lazy: () => import('@/pages/inventory/batch/BatchPage'),
  },
  {
    path: 'inventory/tasks',
    lazy: () => import('@/pages/inventory/tasks/TaskList'),
  },
  {
    path: 'inventory/waves',
    lazy: () => import('@/pages/inventory/waves/WaveList'),
  },
  {
    path: 'inventory/serials',
    lazy: () => import('@/pages/inventory/serials/SerialList'),
  },
]
