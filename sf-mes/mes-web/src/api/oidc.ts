import { get, post } from '@/api/request'
import type { AuthResp } from '@/api/auth'

/**
 * OIDC SSO API（认证中心统一登录）。
 */

export function getAuthorizeUrl() {
  return get<{ authorizeUrl: string; state: string }>('/integration/oidc/authorize-url')
}

export function ssoLogin(code: string, state: string) {
  const qs = `code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}`
  return post<AuthResp>(`/integration/oidc/login?${qs}`)
}
