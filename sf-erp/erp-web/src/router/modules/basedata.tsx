/**
 * 基础资料模块路由片段。
 * <p>
 * 按并行开发约定：各模块独立维护自己的路由片段，由主线程合并进 router/index.tsx。
 * path 不带首斜杠（挂载在主布局 children 下）；页面统一懒加载。
 */
export const basedataRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  {
    path: 'basedata/materials',
    lazy: () => import('@/pages/basedata/materials/MaterialList'),
  },
  {
    path: 'basedata/customers',
    lazy: () => import('@/pages/basedata/customers/CustomerList'),
  },
  {
    path: 'basedata/suppliers',
    lazy: () => import('@/pages/basedata/suppliers/SupplierList'),
  },
  {
    path: 'basedata/warehouses',
    lazy: () => import('@/pages/basedata/warehouses/WarehouseList'),
  },
  {
    path: 'basedata/prices',
    lazy: () => import('@/pages/basedata/prices/PriceCenter'),
  },
  // V141 供应商准入（登记/审批/黑名单）；菜单由主线程统一挂载。
  {
    path: 'basedata/supplier-admit',
    lazy: () => import('@/pages/basedata/suppliers/SupplierAdmit'),
  },
  // T7 单据联查（跨模块只读链路 + 通用红冲入口）；菜单种子 V541。
  {
    path: 'basedata/doc-chain',
    lazy: () => import('@/pages/basedata/DocChain'),
  },
]
