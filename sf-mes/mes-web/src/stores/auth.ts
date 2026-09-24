import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/** 登录用户信息（后端 /auth/login 返回） */
export interface LoginUser {
  id: number
  username: string
  nickname: string
}

/** 可切换组织（后端 /auth/login、/auth/switch-org 返回） */
export interface OrgInfo {
  id: number
  code: string
  name: string
}

/** 登录/切换组织返回的租户与组织上下文 */
export interface OrgContext {
  tenantId: number | null
  orgId: number | null
  orgs: OrgInfo[]
}

/**
 * 登录态全局 store（zustand + localStorage 持久化）。
 * token 供 axios 拦截器统一读取（见 api/request.ts）；
 * roles/perms 来自 GET /auth/me，用于按钮级显隐（HasPerm/usePerm）；
 * tenantId/orgId/orgs 为租户与组织上下文（组织切换器/登录两步式使用）。
 */
interface AuthState {
  token: string | null
  user: LoginUser | null
  /** 角色编码列表（如 super_admin） */
  roles: string[]
  /** 按钮权限码列表（如 purchase:order:add） */
  perms: string[]
  /** 当前租户（登录/切换组织时写入） */
  tenantId: number | null
  /** 当前组织（令牌内上下文，切换组织后更新） */
  orgId: number | null
  /** 可切换组织列表（长度>1 时顶栏展示组织切换器） */
  orgs: OrgInfo[]
  /** 登录成功后写入（ctx 可选：移动端旧调用只传 token/user） */
  setAuth: (token: string, user: LoginUser, ctx?: Partial<OrgContext>) => void
  /** 登录后/刷新页面时从 /auth/me 回填权限上下文 */
  setContext: (roles: string[], perms: string[]) => void
  /** 切换组织后更新令牌内组织上下文（token 由调用方另行 setAuth） */
  setOrgContext: (orgId: number, orgs?: OrgInfo[]) => void
  /** 退出登录 / 401 强制登出 */
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      roles: [],
      perms: [],
      tenantId: null,
      orgId: null,
      orgs: [],
      setAuth: (token, user, ctx) =>
        set({
          token,
          user,
          roles: [],
          perms: [],
          tenantId: ctx?.tenantId ?? null,
          orgId: ctx?.orgId ?? null,
          orgs: ctx?.orgs ?? [],
        }),
      setContext: (roles, perms) => set({ roles, perms }),
      setOrgContext: (orgId, orgs) => set({ orgId, ...(orgs ? { orgs } : {}) }),
      logout: () =>
        set({
          token: null,
          user: null,
          roles: [],
          perms: [],
          tenantId: null,
          orgId: null,
          orgs: [],
        }),
    }),
    { name: 'erp-auth' },
  ),
)
