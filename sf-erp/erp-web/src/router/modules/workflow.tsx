/**
 * 审批工作流模块路由片段。
 *
 * 约定：path 不带前导斜杠（挂在主路由 "/" 布局下）；
 * 页面路径与 MainLayout 工作流菜单保持一致：
 * workflow/todo | workflow/defs | workflow/msgs。
 * 本文件由主线程合并进 router/index.tsx（路由表属公共文件，避免并行冲突）。
 */
export const workflowRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: 'workflow/todo', lazy: () => import('@/pages/workflow/todo/TodoList') },
  { path: 'workflow/defs', lazy: () => import('@/pages/workflow/defs/DefList') },
  { path: 'workflow/msgs', lazy: () => import('@/pages/workflow/msgs/MsgCenter') },
]
