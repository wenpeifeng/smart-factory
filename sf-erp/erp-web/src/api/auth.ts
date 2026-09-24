import { put, get, post } from './request'
import type { LoginUser, OrgInfo } from '@/stores/auth'

/** 登录/切换组织的完整响应（后端 LoginVO） */
export interface AuthResp {
  token: string
  user: LoginUser
  tenantId: number
  /** 令牌内组织上下文（切换组织后为新组织） */
  orgId: number
  /** 可切换组织列表 */
  orgs: OrgInfo[]
}

/** GET /auth/me 响应（权限上下文 + 令牌内租户/组织） */
export interface MeResp {
  user: LoginUser
  roles: string[]
  perms: string[]
  tenantId: number
  orgId: number
}

/** 登录（tenantId 可选，暂不开放租户选择，默认令牌内租户） */
export function login(body: { username: string; password: string; tenantId?: number }) {
  return post<AuthResp>('/auth/login', body)
}

/** 当前用户权限上下文（角色 + 按钮权限码） */
export function fetchMe() {
  return get<MeResp>('/auth/me')
}

/**
 * 切换组织：后端按 orgId 重签 JWT（tenantId/orgId 写入 claims），
 * 返回新 token 与新 orgId，页面数据按新令牌自动过滤。
 */
export function switchOrg(orgId: number) {
  return get<AuthResp>(`/auth/switch-org/${orgId}`)
}

/** GET /auth/sso/authorize 响应：统一登录页跳转 URL + state */
export interface SsoAuthorizeResp {
  authorizeUrl: string
  state: string
}

/** 发起统一登录：取跳转 URL（含 state + PKCE，后端 Redis 暂存 5 分钟） */
export function ssoAuthorize() {
  return get<SsoAuthorizeResp>('/auth/sso/authorize')
}

/** SSO 回调：授权码换本地 ERP 会话（返回结构与 /auth/login 同构） */
export function ssoCallback(body: { code: string; state: string }) {
  return post<AuthResp>('/auth/sso/callback', body)
}
/** 手动同步认证中心用户（SCIM 全量对账，返回增/改/禁/孤儿计数） */
export function ssoSyncUsers() {
  return post<{ added: number; updated: number; disabled: number; orphanDisabled: number; total: number }>(
    '/auth/sso/sync',
  )
}

/** 应用级启停用户（调认证中心，仅影响本系统；无工号账号本地启停） */
export function ssoAppStatus(userId: number, enabled: boolean) {
  return put<void>(`/auth/sso/users/${userId}/app-status`, { enabled })
}

