import { useEffect, useMemo, type ReactNode } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Layout, Menu, Dropdown, Avatar, Space, Button } from 'antd'
import type { MenuProps } from 'antd'
import {
  TeamOutlined,
  UserOutlined,
  MenuOutlined,
  ApartmentOutlined,
  BookOutlined,
  FileTextOutlined,
  KeyOutlined,

  BellOutlined,
  LogoutOutlined,
  GlobalOutlined,
  ArrowLeftOutlined,
  CheckOutlined,
} from '@ant-design/icons'
import { AnimatePresence, motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/auth'
import { changeLang, LANGS } from '@/locales'
import IdentityNoticeBell from '@/components/IdentityNoticeBell'

const { Header, Sider, Content } = Layout

/** 管理门户菜单节点（label 走 i18n key） */
interface AdminMenuNode {
  key: string
  i18nKey: string
  icon?: ReactNode
  children?: AdminMenuNode[]
}

/**
 * 管理门户菜单：系统管理（用户/角色/菜单/部门/字典/操作日志）
 * + 租户管理 + 登录日志 + 组织工厂。
 * key 即 /admin 下的相对路由路径。
 */
const ADMIN_MENUS: AdminMenuNode[] = [
  {
    key: 'admin-system',
    icon: <TeamOutlined />,
    i18nKey: 'admin.system',
    children: [
      { key: '/admin/system/users', icon: <UserOutlined />, i18nKey: 'admin.users' },
      { key: '/admin/system/roles', icon: <TeamOutlined />, i18nKey: 'admin.roles' },
      { key: '/admin/system/menus', icon: <MenuOutlined />, i18nKey: 'admin.menus' },
      { key: '/admin/system/depts', icon: <ApartmentOutlined />, i18nKey: 'admin.depts' },
      { key: '/admin/system/dicts', icon: <BookOutlined />, i18nKey: 'admin.dicts' },
      { key: '/admin/system/logs', icon: <FileTextOutlined />, i18nKey: 'admin.logs' },
    ],
  },
  { key: '/admin/system/login-logs', icon: <KeyOutlined />, i18nKey: 'admin.loginLogs' },
  { key: '/admin/system/notify-channels', icon: <BellOutlined />, i18nKey: 'admin.notifyChannels' },
]

/**
 * 管理门户布局（/admin/*）：与业务门户视觉区分 ——
 * 深海军蓝渐变侧边栏（#0a1929→#123055）+ 同色系深色顶栏 + 青色点缀，
 * 面向平台管理员/集团 IT（租户/组织/用户/角色/菜单/字典/审计）。
 */
export default function AdminLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout } = useAuthStore()
  const { t, i18n } = useTranslation()

  // 管理门户进入后提前加载菜单管理代码块，避免首次点击出现 2-3 秒空白。
  useEffect(() => { void import('@/pages/system/menus/MenuList') }, [])

  const menuItems: MenuProps['items'] = useMemo(
    () =>
      ADMIN_MENUS.map((m) => ({
        key: m.key,
        icon: m.icon,
        label: t(m.i18nKey),
        children: m.children?.map((c) => ({
          key: c.key,
          icon: c.icon,
          label: t(c.i18nKey),
        })),
      })),
    [t],
  )

  const langMenu: MenuProps['items'] = LANGS.map((l) => ({
    key: l.value,
    label: (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
        {l.label}
        {i18n.language === l.value && <CheckOutlined style={{ color: '#36cfc9' }} />}
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

  return (
    <Layout style={{ height: '100vh' }}>
      {/* 深色渐变侧边栏：与管理门户定位一致的平台级视觉 */}
      <Sider
        width={228}
        style={{
          overflow: 'auto',
          background: 'linear-gradient(180deg, #0a1929 0%, #0d2137 70%, #123055 100%)',
          borderRight: '1px solid rgba(54,207,201,0.14)',
        }}
      >
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            color: '#fff',
            fontSize: 16,
            fontWeight: 650,
          }}
        >
          <Avatar
            shape="square"
            size={30}
            style={{ background: 'linear-gradient(135deg,#36cfc9,#1668dc)' }}
          >
            A
          </Avatar>
          <span>{t('app.adminTitle')}</span>
        </div>
        <Menu
          theme="dark"
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
            background: '#0e2036',
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid rgba(54,207,201,0.16)',
          }}
        >
          <Space size={4}>
            <span style={{ color: '#36cfc9', fontSize: 12.5, fontWeight: 600, letterSpacing: 1 }}>
              {t('admin.portal').toUpperCase()}
            </span>
          </Space>

          <Space size={4}>
            <IdentityNoticeBell dark />
            {/* 语言切换器 */}
            <Dropdown menu={{ items: langMenu }} placement="bottomRight">
              <Button type="text" icon={<GlobalOutlined />} style={{ color: 'rgba(255,255,255,0.85)' }}>
                {i18n.language === 'zh' ? '中文' : 'EN'}
              </Button>
            </Dropdown>
            {/* 返回业务门户 */}
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              style={{ color: 'rgba(255,255,255,0.85)' }}
              onClick={() => navigate('/dashboard')}
            >
              {t('topbar.bizPortal')}
            </Button>
            <Dropdown menu={{ items: userMenu }} placement="bottomRight">
              <Space style={{ cursor: 'pointer', paddingLeft: 8 }}>
                <Avatar size={30} icon={<UserOutlined />} />
                <span style={{ color: 'rgba(255,255,255,0.88)' }}>
                  {user?.nickname ?? user?.username ?? t('topbar.notLoggedIn')}
                </span>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content style={{ overflow: 'auto', background: '#f2f5f9' }}>
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              className="route-page"
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
