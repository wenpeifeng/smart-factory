import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Layout, Menu, Dropdown, Avatar, Space, Button, theme, App } from 'antd'
import type { MenuProps } from 'antd'
import {
  DashboardOutlined,
  DatabaseOutlined,
  ShoppingCartOutlined,
  ShopOutlined,
  InboxOutlined,
  AccountBookOutlined,
  ExperimentOutlined,
  AuditOutlined,
  CalculatorOutlined,
  BellOutlined,
  WalletOutlined,
  UsergroupAddOutlined,
  ToolOutlined,
  FileProtectOutlined,
  FundProjectionScreenOutlined,
  PrinterOutlined,
  BarChartOutlined,
  ApartmentOutlined,
  LogoutOutlined,
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
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
const MENUS: MenuNode[] = [
  { key: '/dashboard', icon: <DashboardOutlined />, i18nKey: 'menu.dashboard' },
  {
    key: 'basedata',
    icon: <DatabaseOutlined />,
    i18nKey: 'menu.basedata.root',
    children: [
      { key: '/basedata/materials', i18nKey: 'menu.basedata.materials' },
      { key: '/basedata/customers', i18nKey: 'menu.basedata.customers' },
      { key: '/basedata/suppliers', i18nKey: 'menu.basedata.suppliers' },
      { key: '/basedata/warehouses', i18nKey: 'menu.basedata.warehouses' },
      { key: '/basedata/prices', i18nKey: 'menu.basedata.prices' },
      { key: '/basedata/supplier-admit', i18nKey: 'menu.basedata.supplierAdmit' },
      { key: '/basedata/doc-chain', i18nKey: 'menu.basedata.docChain' },
    ],
  },
  {
    key: 'purchase',
    icon: <ShoppingCartOutlined />,
    i18nKey: 'menu.purchase.root',
    children: [
      { key: '/purchase/requisitions', i18nKey: 'menu.purchase.requisitions' },
      { key: '/purchase/rfqs', i18nKey: 'menu.purchase.rfqs' },
      { key: '/purchase/orders', i18nKey: 'menu.purchase.orders' },
      { key: '/purchase/instock', i18nKey: 'menu.purchase.instock' },
      { key: '/purchase/returns', i18nKey: 'menu.purchase.returns' },
      { key: '/purchase/estimations', i18nKey: 'menu.purchase.estimations' },
      { key: '/purchase/supplier-score', i18nKey: 'menu.purchase.supplierScore' },
      { key: '/integration/monitor', i18nKey: 'menu.integration.monitor' },
    ],
  },
  {
    key: 'sales',
    icon: <ShopOutlined />,
    i18nKey: 'menu.sales.root',
    children: [
      { key: '/sales/quotations', i18nKey: 'menu.sales.quotations' },
      { key: '/sales/orders', i18nKey: 'menu.sales.orders' },
      { key: '/sales/notices', i18nKey: 'menu.sales.notices' },
      { key: '/sales/outstock', i18nKey: 'menu.sales.outstock' },
      { key: '/sales/returns', i18nKey: 'menu.sales.returns' },
    ],
  },
  {
    key: 'inventory',
    icon: <InboxOutlined />,
    i18nKey: 'menu.inventory.root',
    children: [
      { key: '/inventory/stock', i18nKey: 'menu.inventory.stock' },
      { key: '/inventory/tasks', i18nKey: 'menu.inventory.tasks' },
      { key: '/inventory/waves', i18nKey: 'menu.inventory.waves' },
      { key: '/inventory/move', i18nKey: 'menu.inventory.move' },
      { key: '/inventory/check', i18nKey: 'menu.inventory.check' },
      { key: '/inventory/batch', i18nKey: 'menu.inventory.batch' },
      { key: '/inventory/serials', i18nKey: 'menu.inventory.serials' },
    ],
  },
  {
    key: 'qc',
    icon: <AuditOutlined />,
    i18nKey: 'menu.qc.root',
    children: [
      { key: '/qc/inspects', i18nKey: 'menu.qc.inspects' },
      { key: '/qc/rejects', i18nKey: 'menu.qc.rejects' },
      { key: '/qc/templates', i18nKey: 'menu.qc.templates' },
    ],
  },
  {
    key: 'finance',
    icon: <AccountBookOutlined />,
    i18nKey: 'menu.finance.root',
    children: [
      { key: '/finance/receivable', i18nKey: 'menu.finance.receivable' },
      { key: '/finance/payable', i18nKey: 'menu.finance.payable' },
      { key: '/finance/receipts', i18nKey: 'menu.finance.receipts' },
      { key: '/finance/payments', i18nKey: 'menu.finance.payments' },
      { key: '/finance/voucher', i18nKey: 'menu.finance.voucher' },
      { key: '/finance/periods', i18nKey: 'menu.finance.periods' },
    ],
  },
  {
    key: 'cost',
    icon: <CalculatorOutlined />,
    i18nKey: 'menu.cost.root',
    children: [
      { key: '/cost/pricing', i18nKey: 'menu.cost.pricing' },
      { key: '/cost/settle', i18nKey: 'menu.cost.settle' },
      { key: '/cost/analysis', i18nKey: 'menu.cost.analysis' },
    ],
  },
  {
    key: 'mrp',
    icon: <ExperimentOutlined />,
    i18nKey: 'menu.mrp.root',
    children: [
      { key: '/mrp/bom', i18nKey: 'menu.mrp.bom' },
      { key: '/mrp/mrp', i18nKey: 'menu.mrp.mrp' },
      { key: '/mrp/routings', i18nKey: 'menu.mrp.routings' },
      { key: '/mrp/workorders', i18nKey: 'menu.mrp.workorders' },
      { key: '/mrp/dispatch', i18nKey: 'menu.mrp.dispatch' },
    ],
  },
  {
    key: 'asset',
    icon: <WalletOutlined />,
    i18nKey: 'menu.asset.root',
    children: [
      { key: '/asset/cards', i18nKey: 'menu.asset.cards' },
      { key: '/asset/depreciation', i18nKey: 'menu.asset.depreciation' },
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
  {
    key: 'hr',
    icon: <UsergroupAddOutlined />,
    i18nKey: 'menu.hr.root',
    children: [
      { key: '/hr/employees', i18nKey: 'menu.hr.employees' },
      { key: '/hr/attendance', i18nKey: 'menu.hr.attendance' },
      { key: '/hr/piecewage', i18nKey: 'menu.hr.piecewage' },
    ],
  },
  {
    key: 'equipment',
    icon: <ToolOutlined />,
    i18nKey: 'menu.equipment.root',
    children: [
      { key: '/equipment/devices', i18nKey: 'menu.equipment.devices' },
      { key: '/equipment/maintains', i18nKey: 'menu.equipment.maintains' },
    ],
  },
  {
    key: 'invoice',
    icon: <FileProtectOutlined />,
    i18nKey: 'menu.invoice.root',
    children: [
      { key: '/invoice/sales', i18nKey: 'menu.invoice.sales' },
      { key: '/invoice/purchases', i18nKey: 'menu.invoice.purchases' },
    ],
  },
  {
    key: 'budget',
    icon: <FundProjectionScreenOutlined />,
    i18nKey: 'menu.budget.root',
    children: [
      { key: '/budget/plans', i18nKey: 'menu.budget.plans' },
      { key: '/budget/analysis', i18nKey: 'menu.budget.analysis' },
    ],
  },
  {
    key: 'print',
    icon: <PrinterOutlined />,
    i18nKey: 'menu.print.root',
    children: [{ key: '/print', i18nKey: 'menu.print.center' }],
  },
  {
    key: 'report',
    icon: <BarChartOutlined />,
    i18nKey: 'menu.report.root',
    children: [
      { key: '/report/dashboard', i18nKey: 'menu.report.dashboard' },
      { key: '/report/center', i18nKey: 'menu.report.center' },
    ],
  },
  {
    key: 'org',
    icon: <ApartmentOutlined />,
    i18nKey: 'menu.org.root',
    children: [
      { key: '/org/factories', i18nKey: 'menu.org.factories' },
      { key: '/org/transfers', i18nKey: 'menu.org.transfers' },
    ],
  },
]

/**
 * 业务门户主布局：左侧菜单(可折叠) + 顶栏 + 内容区。
 * 顶栏：组织切换器（多组织用户）+ 语言切换器 + 管理门户入口（管理员）+ 用户菜单。
 * 动感设计：内容区路由切换使用 framer-motion 渐入+上移动画（AnimatePresence）。
 */
export default function MainLayout() {
  const [collapsed, setCollapsed] = useState(false)
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
        {o.id === orgId && <CheckOutlined style={{ color: '#1668dc' }} />}
      </span>
    ),
    onClick: () => onSwitchOrg(o.id),
  }))

  const langMenu: MenuProps['items'] = LANGS.map((l) => ({
    key: l.value,
    label: (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
        {l.label}
        {i18n.language === l.value && <CheckOutlined style={{ color: '#1668dc' }} />}
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
      <Sider trigger={null} collapsible collapsed={collapsed} width={220} style={{ overflow: 'auto' }}>
        {/* 品牌 Logo 区：折叠时只显示首字母块 */}
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            color: '#fff',
            fontSize: 17,
            fontWeight: 600,
          }}
        >
          <Avatar
            shape="square"
            size={30}
            style={{ background: 'linear-gradient(135deg,#1668dc,#36cfc9)' }}
          >
            E
          </Avatar>
          {!collapsed && <span>{t('app.title')}</span>}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={['basedata', 'purchase', 'sales', 'inventory', 'qc', 'finance', 'cost', 'mrp', 'asset', 'workflow', 'hr', 'equipment', 'invoice', 'report', 'org', 'budget']}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>

      <Layout>
        <Header
          style={{
            background: token.colorBgContainer,
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid #eef1f5',
          }}
        >
          <span onClick={() => setCollapsed(!collapsed)} style={{ cursor: 'pointer', fontSize: 16 }}>
            {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          </span>

          <Space size={4}>
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

        <Content style={{ overflow: 'auto' }}>
          {/* 路由转场动画：渐入 + 12px 上移，240ms 出入场 */}
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.24, ease: 'easeOut' }}
              style={{ height: '100%' }}
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </Content>
      </Layout>
    </Layout>
  )
}
