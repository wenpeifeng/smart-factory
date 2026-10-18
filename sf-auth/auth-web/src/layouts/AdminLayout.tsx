import { useMemo } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Breadcrumb, Dropdown, Layout, Menu } from 'antd'
import type { MenuProps } from 'antd'
import {
  ApartmentOutlined,
  AppstoreOutlined,
  FileSearchOutlined,
  KeyOutlined,
  LogoutOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '@/stores/auth'

const { Header, Sider, Content } = Layout

const ADMIN_MENUS: MenuProps['items'] = [
  {
    type: 'group',
    key: 'identity-access',
    label: '身份与访问',
    children: [
      { key: '/users', icon: <UserOutlined />, label: '主账号管理' },
      { key: '/orgs', icon: <ApartmentOutlined />, label: '组织架构' },
      { key: '/apps', icon: <AppstoreOutlined />, label: '应用管理' },
    ],
  },
  {
    type: 'group',
    key: 'security-audit',
    label: '安全与审计',
    children: [
      { key: '/audit/login-logs', icon: <KeyOutlined />, label: '登录日志' },
      { key: '/audit/op-logs', icon: <FileSearchOutlined />, label: '操作日志' },
    ],
  },
]

const PAGE_META: Record<string, { group: string; title: string }> = {
  '/users': { group: '身份与访问', title: '主账号管理' },
  '/orgs': { group: '身份与访问', title: '组织架构' },
  '/apps': { group: '身份与访问', title: '应用管理' },
  '/audit/login-logs': { group: '安全与审计', title: '登录日志' },
  '/audit/op-logs': { group: '安全与审计', title: '操作日志' },
}

/** 统一认证中心管理工作台。 */
export default function AdminLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, token, logout } = useAuthStore()
  const pageMeta = PAGE_META[location.pathname]

  const userMenu: MenuProps['items'] = useMemo(
    () => [
      {
        key: 'logout',
        icon: <LogoutOutlined />,
        label: '退出登录',
        onClick: () => {
          void fetch('/api/auth/logout', {
            method: 'POST',
            headers: { Authorization: `Bearer ${token}` },
          }).catch(() => undefined)
          logout()
          navigate('/login', { replace: true })
        },
      },
    ],
    [token, logout, navigate],
  )

  const displayName = user?.realName ?? user?.username ?? '未登录'

  return (
    <Layout className="admin-shell">
      <Sider
        width="clamp(220px, 18vw, 244px)"
        theme="light"
        className="admin-sider thin-scroll"
      >
        <div className="admin-brand">
          <span className="admin-brand-mark" aria-hidden="true">
            <SafetyCertificateOutlined />
          </span>
          <span className="admin-brand-copy">
            <b>统一认证中心</b>
            <small>IDENTITY CONSOLE</small>
          </span>
        </div>

        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={ADMIN_MENUS}
          onClick={({ key }) => navigate(key)}
          className="admin-nav"
        />

        <div className="admin-sider-foot">
          <span className="admin-sider-foot-dot" />
          身份服务运行中
        </div>
      </Sider>

      <Layout className="admin-main">
        <Header className="admin-header">
          <div className="admin-header-start">
            <Breadcrumb
              items={[
                { title: '认证中心' },
                ...(pageMeta ? [{ title: pageMeta.group }, { title: pageMeta.title }] : []),
              ]}
            />
          </div>

          <div className="admin-header-end">
            <span className="admin-environment">
              <span />
              身份服务在线
            </span>
            <Dropdown menu={{ items: userMenu }} placement="bottomRight">
              <button type="button" className="admin-user">
                <Avatar size={32} icon={<UserOutlined />} />
                <span>{displayName}</span>
              </button>
            </Dropdown>
          </div>
        </Header>

        <Content className="admin-content thin-scroll">
          <div key={location.pathname} className="admin-page-frame page-fade">
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  )
}
