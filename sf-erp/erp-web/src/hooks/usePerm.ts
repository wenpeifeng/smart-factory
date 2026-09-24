import { useAuthStore } from '@/stores/auth'

/** 内置超管角色编码：持有全部权限（与后端 SysMenuServiceImpl 同口径） */
export const SUPER_ROLE = 'super_admin'

/**
 * 权限判断 Hook —— 按钮级显隐的标准入口。
 * <p>
 * 与后端 @RequirePerm 使用同一套权限码（模块:资源:操作）：
 * 前端控制"看不看得见"，后端控制"调不调得通"，双层防线。
 *
 * @example
 * const { hasPerm } = usePerm()
 * hasPerm('purchase:order:add')  // true/false
 */
export function usePerm() {
  const roles = useAuthStore((s) => s.roles)
  const perms = useAuthStore((s) => s.perms)

  /** 是否拥有指定权限码（超管恒真） */
  const hasPerm = (code?: string | null): boolean => {
    if (!code) return true // 未指定权限码的元素不做控制
    if (roles.includes(SUPER_ROLE)) return true
    return perms.includes(code)
  }

  /** 是否拥有任一权限码 */
  const hasAnyPerm = (...codes: string[]): boolean =>
    codes.length === 0 || codes.some((c) => hasPerm(c))

  return { hasPerm, hasAnyPerm }
}

/**
 * 管理门户访问判定（/admin/* 守卫与业务门户顶栏入口共用同一口径）：
 * super_admin 角色或持有任意 system:* 权限码即可进入管理门户。
 */
export function useAdminAccess(): boolean {
  const roles = useAuthStore((s) => s.roles)
  const perms = useAuthStore((s) => s.perms)
  return roles.includes(SUPER_ROLE) || perms.some((p) => p.startsWith('system:'))
}
