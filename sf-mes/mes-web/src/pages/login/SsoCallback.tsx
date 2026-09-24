import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Card, Result, Spin, Typography } from 'antd'
import { useAuthStore } from '@/stores/auth'
import { ssoLogin } from '@/api/oidc'

/**
 * SSO 回调页（/sso/callback，公开路由）。
 * <p>统一认证中心授权后重定向回此页（带 code/state），
 * 调后端换取本系统会话后进入工作台；失败给出回本地登录入口。
 */
export default function SsoCallback() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)
  const [error, setError] = useState<string | null>(null)
  const attempted = useRef(false)

  useEffect(() => {
    if (attempted.current) return
    attempted.current = true
    const code = params.get('code')
    const state = params.get('state')
    if (!code || !state) {
      setError('回调缺少授权码参数')
      return
    }
    ssoLogin(code, state)
      .then((resp) => {
        setAuth(resp.token, resp.user, {
          tenantId: resp.tenantId,
          orgId: resp.orgId,
          orgs: resp.orgs ?? [],
        })
        navigate('/workbench', { replace: true })
      })
      .catch((e: Error) => setError(e.message || 'SSO 登录失败'))
  }, [params, navigate, setAuth])

  if (error) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', background: '#f5f7fa' }}>
        <Card style={{ width: 420 }}>
          <Result status="error" title="统一认证登录失败" subTitle={error} />
          <Typography.Paragraph style={{ textAlign: 'center' }}>
            <a onClick={() => navigate('/login', { replace: true })}>返回本地登录</a>
          </Typography.Paragraph>
        </Card>
      </div>
    )
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', height: '100vh', gap: 16 }}>
      <Spin size="large" />
      <Typography.Text type="secondary">统一认证登录中…</Typography.Text>
    </div>
  )
}
