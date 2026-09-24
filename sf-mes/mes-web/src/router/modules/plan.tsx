/** 计划模块路由片段（生产工单/齐套分析） */
export const planRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'plan/workorders', lazy: () => import('@/pages/plan/workorders/WorkorderList') },
  { path: 'plan/kit-checks', lazy: () => import('@/pages/plan/kitchecks/KitCheckList') },
  { path: 'plan/schedules', lazy: () => import('@/pages/plan/schedules/ScheduleGantt') },
]
