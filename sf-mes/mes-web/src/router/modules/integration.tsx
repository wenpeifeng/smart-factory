/** 集成模块路由片段（ERP 回传推送日志对账） */
export const integrationRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'integration/push-logs', lazy: () => import('@/pages/integration/pushlogs/PushLogList') },
]
