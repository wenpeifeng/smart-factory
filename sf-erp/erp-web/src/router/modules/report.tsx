/**
 * 报表中心模块路由片段。
 *
 * 约定：path 不带前导斜杠（挂在主路由 "/" 布局下）；
 * 页面路径与 MainLayout 报表中心菜单保持一致：
 * report/dashboard（经营驾驶舱）| report/center（多维报表）。
 * 本文件由主线程合并进 router/index.tsx（路由表属公共文件，避免并行冲突）。
 */
export const reportRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'report/dashboard', lazy: () => import('@/pages/report/dashboard/ReportDashboard') },
  { path: 'report/center', lazy: () => import('@/pages/report/center/ReportCenter') },
]
