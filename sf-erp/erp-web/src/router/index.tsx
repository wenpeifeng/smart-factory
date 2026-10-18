import { lazy, Suspense, type ComponentType } from 'react'
import { createBrowserRouter, Navigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { RequireAuth, RequireAdmin } from '@/router/guards'
import MainLayout from '@/layouts/MainLayout'
import AdminLayout from '@/layouts/AdminLayout'
// 登录页属于认证关键路径，同步加载可避免退出跳转时触发 Suspense。
import Login from '@/pages/login/Login'

// ---- 各业务模块路由片段（模块内独立维护，此处统一合并挂载） ----
import { basedataRoutes } from '@/router/modules/basedata'
import { purchaseRoutes } from '@/router/modules/purchase'
import { salesRoutes } from '@/router/modules/sales'
import { inventoryRoutes } from '@/router/modules/inventory'
import { financeRoutes } from '@/router/modules/finance'
import { ssoRoutes } from '@/router/modules/sso'
import { mrpRoutes } from '@/router/modules/mrp'
import { reportRoutes } from '@/router/modules/report'
import { qcRoutes } from '@/router/modules/qc'
import { costRoutes } from '@/router/modules/cost'
import { workflowRoutes } from '@/router/modules/workflow'
import { assetRoutes } from '@/router/modules/asset'
import { hrRoutes } from '@/router/modules/hr'
import { equipmentRoutes } from '@/router/modules/equipment'
import { invoiceRoutes } from '@/router/modules/invoice'
import { orgRoutes } from '@/router/modules/org'
import { budgetRoutes } from '@/router/modules/budget'
import { printRoutes } from '@/router/modules/print'
import { mobileRoutes } from '@/router/modules/mobile'
import { wmsMobileRoutes } from '@/router/modules/wmsMobile'
import { systemRoutes } from '@/router/modules/system'
import { integrationRoutes } from '@/router/modules/integration'

/** 业务首页仍按需加载，由路由级 Suspense 提供加载态。 */
const Dashboard = lazy(() => import('@/pages/dashboard/Dashboard'))

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
  // ---- 统一认证中心 OIDC 回调（顶层无守卫挂载） ----
  ...toRouteChildren(ssoRoutes),
  // ---- 移动端 H5（扫码作业，独立布局） ----
  ...toMobileRoutes(mobileRoutes),
  // ---- 移动端 H5（WMS 库内作业：任务/按波拣选/序列号） ----
  ...toMobileRoutes(wmsMobileRoutes),
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
        // 组织工厂（复用业务门户工厂档案页）
        { path: 'org/factories', lazy: () => import('@/pages/org/factories/FactoryList') },
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
      { index: true, element: <Navigate to="/dashboard" replace /> },
      {
        path: 'dashboard',
        element: (
          <Suspense fallback={<RouteLoading />}>
            <Dashboard />
          </Suspense>
        ),
      },
      // ---- 基础资料（物料/客户/供应商/仓库） ----
      ...toRouteChildren(basedataRoutes),
      // ---- 采购（订单/入库/退货） ----
      ...toRouteChildren(purchaseRoutes),
      // ---- 销售（订单/出库/退货） ----
      ...toRouteChildren(salesRoutes),
      // ---- 库存（即时库存/调拨/盘点/批次追溯） ----
      ...toRouteChildren(inventoryRoutes),
      // ---- 质检（检验单/不合格品/检验模板） ----
      ...toRouteChildren(qcRoutes),
      // ---- 财务（应收/应付/凭证） ----
      ...toRouteChildren(financeRoutes),
      // ---- 成本（存货计价/月末核算/成本分析） ----
      ...toRouteChildren(costRoutes),
      // ---- 生产制造（BOM/MRP/工艺路线/生产订单/派工报工） ----
      ...toRouteChildren(mrpRoutes),
      // ---- 固定资产（资产卡片/折旧） ----
      ...toRouteChildren(assetRoutes),
      // ---- 审批中心（待办/流程定义/消息） ----
      ...toRouteChildren(workflowRoutes),
      // ---- 人力资源（员工/考勤/计件工资） ----
      ...toRouteChildren(hrRoutes),
      // ---- 设备管理（台账/保养维修） ----
      ...toRouteChildren(equipmentRoutes),
      // ---- 发票税务（销项/进项） ----
      ...toRouteChildren(invoiceRoutes),
      // ---- 报表中心（经营驾驶舱/多维报表） ----
      ...toRouteChildren(reportRoutes),
      // ---- 组织管理（工厂/组织间调拨） ----
      ...toRouteChildren(orgRoutes),
      // ---- 预算管理（编制/执行分析） ----
      ...toRouteChildren(budgetRoutes),
      // ---- 单据打印中心 ----
      ...toRouteChildren(printRoutes),
      // ---- 系统集成（ERP-MES 集成监控） ----
      ...toRouteChildren(integrationRoutes),
    ],
  },
  { path: '*', element: <Navigate to="/dashboard" replace /> },
])
