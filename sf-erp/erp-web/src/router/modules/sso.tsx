/**
 * 统一认证（SSO）路由片段。
 *
 * 挂载点（主线程接线，见 docs/modules/sso-authcenter.md）：
 * router/index.tsx 顶层路由表 /login 同级处追加展开：
 *
 *   ...toRouteChildren(ssoRoutes),
 *
 * 说明：SSO 回调页必须挂在顶层（无 RequireAuth 包裹、无布局），
 * 与 /login 一致；path 带前导斜杠时同样兼容 toRouteChildren（子路径合并）。
 */
export const ssoRoutes: { path: string; lazy: () => Promise<{ default: any }> }[] = [
  { path: '/sso/callback', lazy: () => import('@/pages/sso/Callback') },
]
