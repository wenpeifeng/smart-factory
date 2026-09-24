/**
 * WMS 移动端 H5 路由片段（三期库内作业深化）。
 * <p>
 * 页面在 src/mobile/wms/（桌面布局之外独立轻页面），path 不带首斜杠。
 * 按并行开发约定：本文件只导出 wmsMobileRoutes，由主线程挂载到主布局之外
 * （无侧边栏的顶层路由，需自行包登录守卫），模块内不 import router/index.tsx；
 * 与 mobile.tsx（二期扫码作业，通知任务在途改）互不冲突。
 */
export const wmsMobileRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  {
    path: 'm/wms/tasks',
    lazy: () => import('@/mobile/wms/TaskList'),
  },
  {
    path: 'm/wms/waves',
    lazy: () => import('@/mobile/wms/PickByWave'),
  },
  {
    path: 'm/wms/waves/:id',
    lazy: () => import('@/mobile/wms/PickByWave'),
  },
  {
    path: 'm/wms/serials',
    lazy: () => import('@/mobile/wms/SerialScan'),
  },
]
