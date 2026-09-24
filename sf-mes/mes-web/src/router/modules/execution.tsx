/** 执行模块路由片段（过站报工/WIP/返工报废） */
export const executionRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'execution/stations', lazy: () => import('@/pages/execution/stations/StationList') },
  { path: 'execution/wips', lazy: () => import('@/pages/execution/wips/WipList') },
  { path: 'execution/rework-scraps', lazy: () => import('@/pages/execution/reworkscrap/ReworkScrapList') },
]
