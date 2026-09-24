/**
 * 采购管理模块路由（path 无首斜杠，由主路由表挂载到 '/' 的 children）。
 * 菜单入口见 layouts/MainLayout.tsx：
 * /purchase/requisitions、/purchase/rfqs、/purchase/orders、/purchase/instock、/purchase/returns。
 */
export const purchaseRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  {
    path: 'purchase/requisitions',
    lazy: () => import('@/pages/purchase/requisitions/RequisitionList'),
  },
  {
    path: 'purchase/rfqs',
    lazy: () => import('@/pages/purchase/rfqs/RfqList'),
  },
  {
    path: 'purchase/orders',
    lazy: () => import('@/pages/purchase/orders/OrderList'),
  },
  {
    path: 'purchase/instock',
    lazy: () => import('@/pages/purchase/instock/InstockList'),
  },
  {
    path: 'purchase/returns',
    lazy: () => import('@/pages/purchase/returns/ReturnList'),
  },
  // V231 暂估应付（货到票未到）：列表+冲回 Modal+汇总卡片。
  // 菜单入口（MainLayout 采购管理组 + locales）由菜单协调者统一挂载。
  {
    path: 'purchase/estimations',
    lazy: () => import('@/pages/purchase/estimations/EstimationList'),
  },
  // V141 供应商评级报表（评分明细+等级分布汇总+手工重算）；菜单由主线程统一挂载。
  {
    path: 'purchase/supplier-score',
    lazy: () => import('@/pages/purchase/suppliers/SupplierScoreReport'),
  },
]
