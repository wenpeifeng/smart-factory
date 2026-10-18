import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Layout, Menu, Dropdown, Avatar, Space, Button, theme, App } from 'antd'
import type { MenuProps } from 'antd'
import {
  DashboardOutlined,
  DatabaseOutlined,
  BellOutlined,
  ScheduleOutlined,
  RocketOutlined,
  ExperimentOutlined,
  InboxOutlined,
  ApiOutlined,
  AimOutlined,
  ToolOutlined,
  AlertOutlined,
  FundOutlined,
  FileTextOutlined,

  LogoutOutlined,
  UserOutlined,
  SwapOutlined,
  GlobalOutlined,
  ControlOutlined,
  CheckOutlined,
  BankOutlined,
} from '@ant-design/icons'
import { AnimatePresence, motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/auth'
import { fetchMe, switchOrg } from '@/api/auth'
import { useAdminAccess } from '@/hooks/usePerm'
import { changeLang, LANGS } from '@/locales'
import IdentityNoticeBell from '@/components/IdentityNoticeBell'

const { Header, Sider, Content } = Layout

/** 菜单叶子/分组节点（label 走 i18n key，语言切换即时生效） */
interface MenuNode {
  key: string
  i18nKey: string
  icon?: ReactNode
  children?: MenuNode[]
}

/**
 * 业务门户菜单配置：key 即路由路径，label 用 i18nKey 经 t() 渲染。
 * 系统管理类菜单已迁往管理门户（AdminLayout，/admin/*）。
 */
/**
 * MES 业务门户菜单配置：key 即路由路径，label 用 i18nKey 经 t() 渲染。
 * 系统管理类菜单已迁往管理门户（AdminLayout，/admin/*）。
 * 计划/执行/质量/物料/追溯/设备/看板等模块按 P2/P3 分期逐个启用。
 */
const MENUS: MenuNode[] = [
  { key: '/workbench', icon: <DashboardOutlined />, i18nKey: 'menu.workbench' },
  {
    key: 'basedata',
    icon: <DatabaseOutlined />,
    i18nKey: 'menu.basedata.root',
    children: [
      { key: '/basedata/materials', i18nKey: 'menu.basedata.materials' },
      { key: '/basedata/customers', i18nKey: 'menu.basedata.customers' },
      { key: '/basedata/suppliers', i18nKey: 'menu.basedata.suppliers' },
      { key: '/basedata/warehouses', i18nKey: 'menu.basedata.warehouses' },
    ],
  },
  {
    key: 'plan',
    icon: <ScheduleOutlined />,
    i18nKey: 'menu.plan.root',
    children: [
      { key: '/plan/workorders', i18nKey: 'menu.plan.workorders' },
      { key: '/plan/kit-checks', i18nKey: 'menu.plan.kitchecks' },
      { key: '/plan/schedules', i18nKey: 'menu.plan.schedules' },
    ],
  },
  {
    key: 'execution',
    icon: <RocketOutlined />,
    i18nKey: 'menu.execution.root',
    children: [
      { key: '/execution/stations', i18nKey: 'menu.execution.stations' },
      { key: '/execution/wips', i18nKey: 'menu.execution.wips' },
      { key: '/execution/rework-scraps', i18nKey: 'menu.execution.reworkscrap' },
    ],
  },
  {
    key: 'material',
    icon: <InboxOutlined />,
    i18nKey: 'menu.material.root',
    children: [
      { key: '/material/pickings', i18nKey: 'menu.material.pickings' },
      { key: '/material/returns', i18nKey: 'menu.material.returns' },
      { key: '/material/shop-stocks', i18nKey: 'menu.material.shopstocks' },
      { key: '/material/batch-sns', i18nKey: 'menu.material.batchsns' },
    ],
  },
  {
    key: 'quality',
    icon: <ExperimentOutlined />,
    i18nKey: 'menu.quality.root',
    children: [
      { key: '/quality/templates', i18nKey: 'menu.quality.templates' },
      { key: '/quality/inspects', i18nKey: 'menu.quality.inspects' },
      { key: '/quality/rejects', i18nKey: 'menu.quality.rejects' },
      { key: '/quality/spc', i18nKey: 'menu.quality.spc' },
    ],
  },
  {
    key: 'integration',
    icon: <ApiOutlined />,
    i18nKey: 'menu.integration.root',
    children: [{ key: '/integration/push-logs', i18nKey: 'menu.integration.pushlogs' }],
  },
  {
    key: 'cockpit',
    icon: <FundOutlined />,
    i18nKey: 'menu.cockpit',
  },
  {
    key: 'trace',
    icon: <AimOutlined />,
    i18nKey: 'menu.trace.root',
    children: [
      { key: '/trace/links', i18nKey: 'menu.trace.links' },
      { key: '/trace/ebrs', i18nKey: 'menu.trace.ebrs' },
    ],
  },
  {
    key: 'equipment',
    icon: <ToolOutlined />,
    i18nKey: 'menu.equipment.root',
    children: [
      { key: '/equipment/devices', i18nKey: 'menu.equipment.devices' },
      { key: '/equipment/checks', i18nKey: 'menu.equipment.checks' },
      { key: '/equipment/maintains', i18nKey: 'menu.equipment.maintains' },
      { key: '/equipment/oee', i18nKey: 'menu.equipment.oee' },
    ],
  },
  {
    key: 'kanban',
    icon: <AlertOutlined />,
    i18nKey: 'menu.kanban.root',
    children: [{ key: '/kanban/andons', i18nKey: 'menu.kanban.andons' }],
  },
  {
    key: 'report',
    icon: <FileTextOutlined />,
    i18nKey: 'menu.report.root',
    children: [
      { key: '/report/workorders', i18nKey: 'menu.report.workorders' },
      { key: '/report/stations', i18nKey: 'menu.report.stations' },
      { key: '/report/quality', i18nKey: 'menu.report.quality' },
    ],
  },
  {
    key: 'workflow',
    icon: <BellOutlined />,
    i18nKey: 'menu.workflow.root',
    children: [
      { key: '/workflow/todo', i18nKey: 'menu.workflow.todo' },
      { key: '/workflow/defs', i18nKey: 'menu.workflow.defs' },
      { key: '/workflow/msgs', i18nKey: 'menu.workflow.msgs' },
    ],
  },
]

/**
 * 业务门户主布局：左侧菜单(可折叠) + 顶栏 + 内容区。
 * 顶栏：组织切换器（多组织用户）+ 语言切换器 + 管理门户入口（管理员）+ 用户菜单。
 * 动感设计：内容区路由切换使用 framer-motion 渐入+上移动画（AnimatePresence）。
 */
export default function MainLayout() {
  const [switching, setSwitching] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout, token: authToken, perms, roles, setContext, orgs, orgId, setAuth } =
    useAuthStore()
  const canAdmin = useAdminAccess()
  const { t, i18n } = useTranslation()
  const { message } = App.useApp()
  const { token } = theme.useToken()

  // 菜单 label 走 t()：语言切换即时生效
  const menuItems: MenuProps['items'] = useMemo(
    () =>
      MENUS.map((m) => ({
        key: m.key,
        icon: m.icon,
        label: t(m.i18nKey),
        children: m.children?.map((c) => ({ key: c.key, label: t(c.i18nKey) })),
      })),
    [t],
  )

  // 页面刷新（权限上下文丢失）时自动补拉 /auth/me，保证按钮级显隐可用
  useEffect(() => {
    if (authToken && perms.length === 0 && roles.length === 0) {
      fetchMe()
        .then((ctx) => setContext(ctx.roles ?? [], ctx.perms ?? []))
        .catch(() => undefined)
    }
  }, [authToken, perms.length, roles.length, setContext])

  /** 组织切换：重签 token → 更新上下文 → 整页刷新（全部业务数据按新组织过滤） */
  const onSwitchOrg = async (targetOrgId: number) => {
    if (targetOrgId === orgId || switching) return
    setSwitching(true)
    const hide = message.loading(t('topbar.orgSwitching'), 0)
    try {
      const resp = await switchOrg(targetOrgId)
      setAuth(resp.token, resp.user, {
        tenantId: resp.tenantId,
        orgId: resp.orgId,
        orgs: resp.orgs ?? [],
      })
      hide()
      // 整页刷新：清空 React Query 缓存与所有模块页面状态，数据随新令牌重新拉取
      window.location.reload()
    } catch {
      hide()
      setSwitching(false)
    }
  }

  const orgMenu: MenuProps['items'] = (orgs ?? []).map((o) => ({
    key: String(o.id),
    icon: <BankOutlined />,
    label: (
      <span style={{ fontWeight: o.id === orgId ? 650 : 400, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
        {o.name}
        {o.id === orgId && <CheckOutlined style={{ color: '#16765c' }} />}
      </span>
    ),
    onClick: () => onSwitchOrg(o.id),
  }))

  const langMenu: MenuProps['items'] = LANGS.map((l) => ({
    key: l.value,
    label: (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
        {l.label}
        {i18n.language === l.value && <CheckOutlined style={{ color: '#16765c' }} />}
      </span>
    ),
    onClick: () => changeLang(l.value),
  }))

  const userMenu: MenuProps['items'] = [
    { key: 'profile', icon: <UserOutlined />, label: t('topbar.profile') },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: t('topbar.logout'),
      onClick: () => {
        logout()
        navigate('/login', { replace: true })
      },
    },
  ]

  const currentOrgName = orgs?.find((o) => o.id === orgId)?.name

  return (
    <Layout style={{ height: '100vh' }}>
      <Sider
        width={220}
        theme="light"
        style={{
          overflow: 'auto',
          background: '#f8faf9',
          borderRight: '1px solid #dce5e1',
          boxShadow: '4px 0 16px rgba(31, 58, 49, 0.04)',
        }}
      >
        {/* 品牌区固定展示，避免业务导航退化为只有图标的悬浮菜单。 */}
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            color: '#172b26',
            fontSize: 17,
            fontWeight: 600,
          }}
        >
          <Avatar
            shape="square"
            size={30}
            style={{ background: 'linear-gradient(135deg,#16765c,#ffb547)' }}
          >
            M
          </Avatar>
          <span>{t('app.title')}</span>
        </div>
        <Menu
          theme="light"
          mode="inline"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={[]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ background: 'transparent' }}
        />
      </Sider>

      <Layout>
        <Header
          style={{
            background: token.colorBgContainer,
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            borderBottom: '1px solid #e0e7e4',
            boxShadow: '0 2px 10px rgba(30, 55, 47, 0.035)',
          }}
        >
          <Space size={4}>
            <IdentityNoticeBell />
            {/* 组织切换器：多组织用户可见（切换重签令牌并整页刷新数据） */}
            {(orgs?.length ?? 0) > 1 && (
              <Dropdown menu={{ items: orgMenu }} placement="bottomRight" disabled={switching}>
                <Button type="text" icon={<SwapOutlined spin={switching} />} style={{ display: 'inline-flex', alignItems: 'center' }}>
                  <span style={{ maxWidth: 140, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {currentOrgName ?? t('topbar.switchOrg')}
                  </span>
                </Button>
              </Dropdown>
            )}

            {/* 语言切换器：中/EN 即时生效 */}
            <Dropdown menu={{ items: langMenu }} placement="bottomRight">
              <Button type="text" icon={<GlobalOutlined />}>
                {i18n.language === 'zh' ? '中文' : 'EN'}
              </Button>
            </Dropdown>

            {/* 管理门户入口：super_admin 或持有 system:* 权限 */}
            {canAdmin && (
              <Button type="link" icon={<ControlOutlined />} onClick={() => navigate('/admin')}>
                {t('topbar.adminPortal')}
              </Button>
            )}

            <Dropdown menu={{ items: userMenu }} placement="bottomRight">
              <Space style={{ cursor: 'pointer', paddingLeft: 8 }}>
                <Avatar size={30} icon={<UserOutlined />} />
                <span>{user?.nickname ?? user?.username ?? t('topbar.notLoggedIn')}</span>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content style={{ overflow: 'auto', background: '#f4f7f6' }}>
          {/* 路由转场动画：渐入 + 12px 上移，240ms 出入场 */}
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              className={location.pathname === '/kanban/screen' ? 'route-page route-page--flush' : 'route-page'}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.24, ease: 'easeOut' }}
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </Content>
      </Layout>
    </Layout>
  )
}
