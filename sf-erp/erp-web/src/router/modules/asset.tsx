/**
 * 固定资产模块路由片段。
 *
 * 约定：path 不带前导斜杠（挂在主路由 "/" 布局下）；
 * 页面路径与 MainLayout 固定资产菜单保持一致：
 * asset/cards | asset/depreciation。
 * 本文件由主线程合并进 router/index.tsx（路由表属公共文件，避免并行冲突）。
 */
export const assetRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'asset/cards', lazy: () => import('@/pages/asset/cards/AssetCardList') },
  { path: 'asset/depreciation', lazy: () => import('@/pages/asset/depreciation/DepreciationPage') },
]
