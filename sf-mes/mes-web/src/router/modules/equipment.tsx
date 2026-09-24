/** 设备模块路由片段 */
export const equipmentRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'equipment/devices', lazy: () => import('@/pages/equipment/devices/DeviceList') },
  { path: 'equipment/checks', lazy: () => import('@/pages/equipment/checks/CheckList') },
  { path: 'equipment/maintains', lazy: () => import('@/pages/equipment/maintains/MaintainList') },
  { path: 'equipment/oee', lazy: () => import('@/pages/equipment/oee/OeePage') },
]
