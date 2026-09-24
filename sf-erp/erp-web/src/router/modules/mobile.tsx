/**
 * 移动端 H5 路由片段（二期扫码作业）。
 * <p>
 * 页面在 src/mobile/（桌面端布局之外独立轻页面），path 不带首斜杠。
 * 按并行开发约定：本文件只导出 mobileRoutes，由主线程挂载到主布局之外
 * （无侧边栏的顶层路由，需自行包登录守卫），模块内不 import router/index.tsx。
 */
export const mobileRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  {
    path: 'm/login',
    lazy: () => import('@/mobile/MobileLogin'),
  },
  {
    path: 'm/scan',
    lazy: () => import('@/mobile/MobileScan'),
  },
  {
    path: 'm/approve/:taskId',
    lazy: () => import('@/mobile/ApprovePage'),
  },
]
