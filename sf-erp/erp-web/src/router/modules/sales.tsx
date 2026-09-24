/**
 * 销售管理模块路由表。
 * <p>
 * 由主线程在 src/router/index.tsx 中合并挂载到 MainLayout 子路由，
 * path 不带首斜杠（相对 "/" 布局路由），与 MainLayout 菜单路径一致：
 * /sales/orders、/sales/outstock、/sales/returns、/sales/quotations、/sales/notices。
 */
export const salesRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  {
    path: 'sales/orders',
    lazy: () => import('@/pages/sales/orders/OrderList'),
  },
  {
    path: 'sales/outstock',
    lazy: () => import('@/pages/sales/outstock/OutstockList'),
  },
  {
    path: 'sales/returns',
    lazy: () => import('@/pages/sales/returns/ReturnList'),
  },
  {
    path: 'sales/quotations',
    lazy: () => import('@/pages/sales/quotations/QuotationList'),
  },
  {
    path: 'sales/notices',
    lazy: () => import('@/pages/sales/notices/NoticeList'),
  },
]
