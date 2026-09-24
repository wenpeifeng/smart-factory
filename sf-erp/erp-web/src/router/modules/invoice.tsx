/**
 * 发票管理模块路由片段。
 *
 * 约定：path 不带前导斜杠（挂在主路由 "/" 布局下）；
 * 页面路径与 MainLayout 发票管理菜单保持一致：
 * invoice/sales | invoice/purchases。
 * 本文件由主线程合并进 router/index.tsx（路由表属公共文件，避免并行冲突）。
 */
export const invoiceRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'invoice/sales', lazy: () => import('@/pages/invoice/sales/SalesInvoiceList') },
  { path: 'invoice/purchases', lazy: () => import('@/pages/invoice/purchases/PurchaseInvoiceList') },
]
