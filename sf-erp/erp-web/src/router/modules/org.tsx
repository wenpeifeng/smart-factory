/**
 * 组织管理模块路由片段。
 *
 * 按并行开发约定：各模块独立维护自己的路由片段，由主线程合并进 router/index.tsx。
 * path 不带首导斜杠（挂载在主布局 children 下）；页面统一懒加载。
 * 菜单路径对应 MainLayout「组织管理」：org/factories、org/transfers。
 */
export const orgRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  {
    path: 'org/factories',
    lazy: () => import('@/pages/org/factories/FactoryList'),
  },
  {
    path: 'org/transfers',
    lazy: () => import('@/pages/org/transfers/TransferList'),
  },
]
