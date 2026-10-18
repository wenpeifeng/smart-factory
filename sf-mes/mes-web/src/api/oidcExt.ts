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

export interface EmployeeProfileForm {
  realName: string
  position?: string
  email?: string
  phone?: string
}

export function updateAuthUserProfile(employeeNo: string, body: EmployeeProfileForm) {
  return put<{ fetched: number; inserted: number; updated: number; disabled: number }>(
    `/integration/auth/users/${encodeURIComponent(employeeNo)}/profile`,
    body,
  )
}

export function resetAuthUserPassword(employeeNo: string, password: string) {
  return put<void>(`/integration/auth/users/${encodeURIComponent(employeeNo)}/password`, { password })
}
