/**
 * 系统管理模块路由片段。
 *
 * 约定：path 不带前导斜杠（挂在主路由 "/" 布局下）；
 * 页面路径与 MainLayout 系统管理菜单保持一致：
 * system/users | system/roles | system/menus | system/depts | system/dicts | system/logs。
 * 本文件由主线程合并进 router/index.tsx（路由表属公共文件，避免并行冲突）。
 */
export const systemRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'system/users', lazy: () => import('@/pages/system/users/UserList') },
  { path: 'system/roles', lazy: () => import('@/pages/system/roles/RoleList') },
  { path: 'system/menus', lazy: () => import('@/pages/system/menus/MenuList') },
  { path: 'system/depts', lazy: () => import('@/pages/system/depts/DeptList') },
  { path: 'system/dicts', lazy: () => import('@/pages/system/dicts/DictPage') },
  { path: 'system/logs', lazy: () => import('@/pages/system/logs/OperLogList') },
]
