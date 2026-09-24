import { post, put } from '@/api/request'

/**
 * 认证中心用户管理扩展 API（用户主档案由 auth-center 统一维护）。
 */

export function syncAuthUsers() {
  return post<{ fetched: number; inserted: number; updated: number; disabled: number }>('/integration/auth/sync')
}

export function changeUserAppStatus(employeeNo: string, enabled: boolean) {
  return put<{ fetched: number; inserted: number; updated: number; disabled: number }>(
    `/integration/auth/users/${encodeURIComponent(employeeNo)}/status?enabled=${enabled}`,
  )
}
