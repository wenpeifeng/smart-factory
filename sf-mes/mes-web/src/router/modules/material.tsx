/** 物料模块路由片段（领料/退料/线边仓/批次SN） */
export const materialRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'material/pickings', lazy: () => import('@/pages/material/pickings/PickingList') },
  { path: 'material/returns', lazy: () => import('@/pages/material/returns/ReturnList') },
  { path: 'material/shop-stocks', lazy: () => import('@/pages/material/shopstocks/ShopStockList') },
  { path: 'material/batch-sns', lazy: () => import('@/pages/material/batchsns/BatchSnList') },
]
