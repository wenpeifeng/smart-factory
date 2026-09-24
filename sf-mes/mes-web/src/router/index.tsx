import { lazy, Suspense, type ComponentType } from 'react'
import { createBrowserRouter, Navigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { RequireAuth, RequireAdmin } from '@/router/guards'
import MainLayout from '@/layouts/MainLayout'
import AdminLayout from '@/layouts/AdminLayout'

// ---- 各业务模块路由片段（模块内独立维护，此处统一合并挂载） ----
import { basedataRoutes } from '@/router/modules/basedata'
import { workflowRoutes } from '@/router/modules/workflow'
import { mobileRoutes } from '@/router/modules/mobile'
import { systemRoutes } from '@/router/modules/system'
import { planRoutes } from '@/router/modules/plan'
import { executionRoutes } from '@/router/modules/execution'
import { materialRoutes } from '@/router/modules/material'
import { qualityRoutes } from '@/router/modules/quality'
import { integrationRoutes } from '@/router/modules/integration'
import { traceRoutes } from '@/router/modules/trace'
import { equipmentRoutes } from '@/router/modules/equipment'
import { kanbanRoutes } from '@/router/modules/kanban'
import { reportRoutes } from '@/router/modules/report'

/** 懒加载页面：按需分割代码，首屏只加载布局与登录页 */
const Login = lazy(() => import('@/pages/login/Login'))
const Workbench = lazy(() => import('@/pages/dashboard/Workbench'))

/** 模块路由片段的统一形状（各模块 router/modules/*.tsx 按此导出） */
interface RouteFragment {
  path: string
  lazy: () => Promise<{ default: ComponentType }>
}

/** 懒加载路由的统一 loading 态（页面分包加载期间展示） */
function RouteLoading() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
      <Spin size="large" />
    </div>
  )
}

/**
 * 将模块路由片段转为路由 children（element 风格 + Suspense）。
 * 模块片段只需维护 path 与懒加载函数，类型转换收口在此处。
 */
function toRouteChildren(fragments: RouteFragment[]) {
  return fragments.map((f) => {
    const Component = lazy(f.lazy)
    return {
      path: f.path,
      element: (
        <Suspense fallback={<RouteLoading />}>
          <Component />
        </Suspense>
      ),
    }
  })
}

/**
 * 移动端路由（/m/*）：挂在主布局之外（无侧边栏/顶栏），仅登录守卫。
 * 面向仓库 PDA/手机扫码作业（收货/拣货/盘点）。
 */
function toMobileRoutes(fragments: RouteFragment[]) {
  return fragments.map((f) => {
    const Component = lazy(f.lazy)
    return {
      path: `/${f.path}`,
      element: (
        <RequireAuth>
          <Suspense fallback={<RouteLoading />}>
            <Component />
          </Suspense>
        </RequireAuth>
      ),
    }
  })
}

/**
 * 旧系统管理路径（/system/*）迁移到管理门户（/admin/system/*）：
 * 书签/外链平滑重定向，避免 404 直跳工作台。
 */
function LegacySystemRedirect() {
  const location = useLocation()
  const target =
    location.pathname === '/system'
      ? '/admin/system/users'
      : location.pathname.replace(/^\/system/, '/admin/system')
  return <Navigate to={target} replace />
}

/**
 * 路由表（三门户架构，见 docs/集团组织与数据权限设计.md 0.1）：
 * - /        业务门户 MainLayout（供应链/财务/生产等业务菜单）
 * - /admin/* 管理门户 AdminLayout（系统管理/租户/登录日志/组织工厂，RequireAdmin 守卫）
 * - /m/*     移动门户（PDA 扫码作业，独立轻页面）
 * 新增业务模块页面：在 router/modules/<模块>.tsx 追加片段，然后在 MainLayout children 展开。
 */
export const router = createBrowserRouter([
  { path: '/login', element: <Login /> },
  // SSO 统一认证回调（公开路由：授权码换本系统会话）
  {
    path: '/sso/callback',
    element: (
      <Suspense fallback={<RouteLoading />}>
        {(() => {
          const SsoCallback = lazy(() => import('@/pages/login/SsoCallback'))
          return <SsoCallback />
        })()}
      </Suspense>
    ),
  },
  // ---- 移动端 H5（扫码作业，独立布局） ----
  ...toMobileRoutes(mobileRoutes),
  // ---- 管理门户（平台管理员/集团 IT） ----
  {
    path: '/admin',
    element: (
      <RequireAuth>
        <RequireAdmin>
          <AdminLayout />
        </RequireAdmin>
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/admin/system/users" replace /> },
      // 系统管理（用户/角色/菜单/部门/字典/操作日志）
      ...toRouteChildren(systemRoutes),
      // 租户管理（sys_tenant，平台管理员跨租户维护）
      ...toRouteChildren([
        { path: 'system/tenants', lazy: () => import('@/pages/system/tenants/TenantList') },
        // 登录日志（占位，B 任务并行开发中）
        { path: 'system/login-logs', lazy: () => import('@/pages/system/logs/LoginLogList') },
        // 通知通道（统一通知中心：钉钉/企微/webhook 通道配置）
        { path: 'system/notify-channels', lazy: () => import('@/pages/system/notify/ChannelList') },
      ]),
    ],
  },
  // 旧 /system/* 路径 → 管理门户
  { path: '/system/*', element: <LegacySystemRedirect /> },
  // ---- 业务门户 ----
  {
    path: '/',
    element: (
      <RequireAuth>
        <MainLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/workbench" replace /> },
      { path: 'workbench', element: <Workbench /> },
      // ---- 基础资料（物料/客户/供应商/仓库） ----
      ...toRouteChildren(basedataRoutes),
      // ---- 审批中心（待办/流程定义/消息） ----
      ...toRouteChildren(workflowRoutes),
      // ---- P2 执行核心：计划/执行/质量/物料/集成 ----
      ...toRouteChildren(planRoutes),
      ...toRouteChildren(executionRoutes),
      ...toRouteChildren(materialRoutes),
      ...toRouteChildren(qualityRoutes),
      ...toRouteChildren(integrationRoutes),
      // ---- P3 深化：追溯/设备/看板 ----
      ...toRouteChildren(traceRoutes),
      ...toRouteChildren(equipmentRoutes),
      ...toRouteChildren(kanbanRoutes),
      // ---- P4 呈现层：驾驶舱/大屏/报表 ----
      ...toRouteChildren(reportRoutes),
      // ---- 追溯/设备/看板等模块路由片段按 P3 分期追加 ----
    ],
  },
  { path: '*', element: <Navigate to="/workbench" replace /> },
])
