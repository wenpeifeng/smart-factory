/** P4 呈现层路由片段：驾驶舱/看板大屏/报表中心 */
export const reportRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'cockpit', lazy: () => import('@/pages/cockpit/Cockpit') },
  { path: 'kanban/screen', lazy: () => import('@/pages/kanban/screen/ScreenBoard') },
  { path: 'report/workorders', lazy: () => import('@/pages/report/workorders/WoAchievement') },
  { path: 'report/stations', lazy: () => import('@/pages/report/stations/StationDetailReport') },
  { path: 'report/quality', lazy: () => import('@/pages/report/quality/QualityReport') },
]
