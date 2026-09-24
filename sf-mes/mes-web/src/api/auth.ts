import { get, post } from './request'
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
