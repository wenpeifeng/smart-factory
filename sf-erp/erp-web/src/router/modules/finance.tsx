/**
 * 财务管理模块路由表。
 * 约定：path 不带首斜杠（由主路由挂载到布局 children 下），
 * 页面全部懒加载按需分割。主线程在 router/index.tsx 中合并各模块路由。
 */
export const financeRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  {
    path: 'finance/receivable',
    lazy: () => import('@/pages/finance/receivable/ReceivableList'),
  },
  {
    path: 'finance/payable',
    lazy: () => import('@/pages/finance/payable/PayableList'),
  },
  {
    path: 'finance/receipts',
    lazy: () => import('@/pages/finance/receipt/ReceiptList'),
  },
  {
    path: 'finance/payments',
    lazy: () => import('@/pages/finance/payment/PaymentList'),
  },
  {
    path: 'finance/voucher',
    lazy: () => import('@/pages/finance/voucher/VoucherList'),
  },
  {
    path: 'finance/periods',
    lazy: () => import('@/pages/finance/periods/PeriodList'),
  },
  {
    path: 'finance/books',
    lazy: () => import('@/pages/finance/books/BookList'),
  },
]
