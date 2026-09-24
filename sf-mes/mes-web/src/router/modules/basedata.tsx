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
]
