/**
 * 成本管理模块路由片段。
 *
 * 约定：path 不带前导斜杠（挂在主路由 "/" 布局下）；
 * 页面路径与 MainLayout 成本管理菜单保持一致：
 * cost/pricing | cost/settle | cost/analysis。
 * 本文件由主线程合并进 router/index.tsx（路由表属公共文件，避免并行冲突）。
 */
export const costRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'cost/pricing', lazy: () => import('@/pages/cost/pricing/CostFlowPage') },
  { path: 'cost/settle', lazy: () => import('@/pages/cost/settle/SettlePage') },
  { path: 'cost/analysis', lazy: () => import('@/pages/cost/analysis/AnalysisPage') },
]
