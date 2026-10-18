import { post } from '@/api/request'
import type { AuthResp } from '@/api/auth'

/**
 * OIDC SSO API（认证中心统一登录）。
 */

/** MES 原生企业账号登录：后端无感完成认证中心 OIDC 授权码流程 */
export function passwordLogin(body: { username: string; password: string }) {
  return post<AuthResp>('/integration/oidc/password-login', body)
}

export function ssoLogin(code: string, state: string) {
  const qs = `code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}`
  return post<AuthResp>(`/integration/oidc/login?${qs}`)
}
