/**
 * 单据打印中心路由片段。
 *
 * 约定：path 不带前导斜杠（挂在主路由 "/" 布局下）；
 * 页面路径与 MainLayout「单据打印→打印中心」菜单一致：
 * print（打印中心）| print/preview/:bizType/:id（A4 打印预览）。
 * 本文件由主线程合并进 router/index.tsx（路由表属公共文件，避免并行冲突）。
 */
export const printRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'print', lazy: () => import('@/pages/print/PrintCenter') },
  { path: 'print/preview/:bizType/:id', lazy: () => import('@/pages/print/PrintPreview') },
]
