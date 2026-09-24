import { useEffect, useState, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuthStore } from '@/stores/auth'
import { fetchMe } from '@/api/auth'
import { SUPER_ROLE } from '@/hooks/usePerm'

/**
 * 登录守卫：未登录访问受保护路由时重定向到 /login，并记住来源路径，
 * 登录成功后回跳。
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token)
  const location = useLocation()

  if (!token) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }
  return <>{children}</>
}

/** 居中 loading（权限上下文补拉期间） */
function CenterLoading() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
      <Spin size="large" />
    </div>
  )
}

/**
 * 管理门户守卫（/admin/*）：super_admin 角色或持有任意 system:* 权限码
 * （口径与 useAdminAccess / MainLayout 管理门户入口一致）。
 * 页面刷新导致权限上下文丢失时先补拉 /auth/me 再判定；
 * 无管理权限一律跳回业务门户工作台。
 */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token)
  const roles = useAuthStore((s) => s.roles)
  const perms = useAuthStore((s) => s.perms)
  const setContext = useAuthStore((s) => s.setContext)
  const location = useLocation()
  // store 里已有权限上下文则无需补拉（从业务门户跳转的常见路径）
  const [resolved, setResolved] = useState(roles.length > 0 || perms.length > 0)

  useEffect(() => {
    if (!token || resolved) return
    let alive = true
    fetchMe()
      .then((me) => {
        if (!alive) return
        setContext(me.roles ?? [], me.perms ?? [])
        setResolved(true)
      })
      .catch(() => {
        // 拉取失败按已解析处理（无权限 → 跳回工作台，避免卡 loading）
        if (alive) setResolved(true)
      })
    return () => {
      alive = false
    }
  }, [token, resolved, setContext])

  if (!token) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }
  if (!resolved) return <CenterLoading />

  const canAdmin = roles.includes(SUPER_ROLE) || perms.some((p) => p.startsWith('system:'))
  if (!canAdmin) return <Navigate to="/dashboard" replace />
  return <>{children}</>
}
