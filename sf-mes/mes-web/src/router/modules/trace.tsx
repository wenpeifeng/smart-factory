/** 追溯模块路由片段 */
export const traceRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'trace/links', lazy: () => import('@/pages/trace/links/TraceSearch') },
  { path: 'trace/ebrs', lazy: () => import('@/pages/trace/ebrs/EbrList') },
]
