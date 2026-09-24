/**
 * 设备管理模块路由片段。
 *
 * 约定：path 不带前导斜杠（挂在主路由 "/" 布局下）；
 * 页面路径与 MainLayout 设备管理菜单保持一致：
 * equipment/devices（设备台账）| equipment/maintains（保养维修）。
 * 本文件由主线程合并进 router/index.tsx（路由表属公共文件，避免并行冲突）。
 */
export const equipmentRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'equipment/devices', lazy: () => import('@/pages/equipment/devices/DeviceList') },
  { path: 'equipment/maintains', lazy: () => import('@/pages/equipment/maintains/MaintainPage') },
]
