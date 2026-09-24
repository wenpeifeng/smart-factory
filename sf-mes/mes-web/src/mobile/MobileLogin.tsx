import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Card, Form, Input } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { post } from '@/api/request'
import { useAuthStore, type LoginUser } from '@/stores/auth'

interface LoginForm {
  username: string
  password: string
}

/** 后端 /auth/login 返回体（与桌面端 Login.tsx 一致） */
interface LoginResp {
  token: string
  user: LoginUser
}

const TABS = [
  { type: 'report', label: '过站报工' },
  { type: 'check', label: '首检' },
  { type: 'andon', label: '安灯' },
]

/**
 * 移动端简化登录（/m/login）。
 * 复用桌面端 POST /auth/login 与同一份 zustand 登录态（token 进 localStorage），
 * 登录后进入扫码报工作业页（默认过站报工，可切换作业类型）。
 */
export default function MobileLogin() {
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const setAuth = useAuthStore((s) => s.setAuth)

  const onFinish = async (form: LoginForm) => {
    setLoading(true)
    try {
      const resp = await post<LoginResp>('/auth/login', form)
      setAuth(resp.token, resp.user)
      const nextType = params.get('next') ?? 'report'
      navigate(`/m/scan?type=${nextType}`, { replace: true })
    } catch {
      /* 错误提示由 axios 拦截器统一弹出 */
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100 p-4">
      <div className="w-full" style={{ maxWidth: 400 }}>
        <Card>
          <div className="mb-6 text-center">
            <div className="text-xl font-bold" style={{ color: '#1668dc' }}>
              MES 移动作业
            </div>
            <div className="mt-1 text-xs text-gray-500">扫码过站报工 · 首检 · 安灯</div>
          </div>

          <Form<LoginForm> onFinish={onFinish} initialValues={{ username: 'admin', password: '' }}>
            <Form.Item name="username" rules={[{ required: true, message: '请输入账号' }]}>
              <Input size="large" prefix={<UserOutlined />} placeholder="账号" autoComplete="username" />
            </Form.Item>
            <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password size="large" prefix={<LockOutlined />} placeholder="密码" />
            </Form.Item>
            <Form.Item style={{ marginBottom: 8 }}>
              <Button type="primary" htmlType="submit" size="large" block loading={loading}>
                登 录
              </Button>
            </Form.Item>
          </Form>

          <div className="flex justify-center gap-2">
            {TABS.map((t) => (
              <Button key={t.type} size="small" type="link" onClick={() => navigate(`/m/login?next=${t.type}`)}>
                {t.label}作业
              </Button>
            ))}
          </div>
        </Card>
      </div>
    </div>
  )
}
