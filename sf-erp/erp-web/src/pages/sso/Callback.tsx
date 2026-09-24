import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Alert, Button, Card, Result, Spin } from 'antd'
import { SafetyCertificateOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { ssoCallback, fetchMe } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

/**
 * 统一认证（SSO）回调页（/sso/callback?code=xxx&state=xxx）。
 *
 * 流程：统一登录页授权后浏览器重定向到本页 → 取 code/state
 * POST /auth/sso/callback（后端换令牌 + JWKS 验签 + 工号匹配本地用户 +
 * 签发本地 ERP JWT）→ setAuth + 拉取权限上下文 → 进门户。
 * 失败（state 过期/用户未同步/已禁用）展示错误与返回登录入口，
 * 本地账密登录保留作降级通道。
 */
export default function SsoCallback() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const setAuth = useAuthStore((s) => s.setAuth)
  const setContext = useAuthStore((s) => s.setContext)
  const [error, setError] = useState<string | null>(null)
  /** React18 StrictMode 双挂载防护：授权码一次性，重复提交必然失败 */
  const exchanged = useRef(false)

  useEffect(() => {
    if (exchanged.current) return
    const code = params.get('code')
    const state = params.get('state')
    if (!code || !state) {
      setError(t('login.ssoCallbackFailed'))
      return
    }
    exchanged.current = true
    ;(async () => {
      try {
        const resp = await ssoCallback({ code, state })
        setAuth(resp.token, resp.user, {
          tenantId: resp.tenantId,
          orgId: resp.orgId,
          orgs: resp.orgs ?? [],
        })
        try {
          const me = await fetchMe()
          setContext(me.roles ?? [], me.perms ?? [])
        } catch {
          // 权限上下文拉取失败不阻断登录，MainLayout 挂载时会重试补拉
        }
        navigate('/dashboard', { replace: true })
      } catch (e) {
        // axios 拦截器已弹统一错误提示；此处留存文案供页面展示
        setError(e instanceof Error ? e.message : t('login.ssoCallbackFailed'))
      }
    })()
  }, [params, navigate, setAuth, setContext, t])

  if (error) {
    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'linear-gradient(135deg,#f5f8fc,#eef3fa)',
        }}
      >
        <Card style={{ width: 420, borderRadius: 16 }}>
          <Result
            status="warning"
            icon={<SafetyCertificateOutlined />}
            title={t('login.ssoCallbackFailed')}
            subTitle={error}
            extra={
              <Button type="primary" onClick={() => navigate('/login', { replace: true })}>
                {t('login.ssoBackLogin')}
              </Button>
            }
          />
        </Card>
      </div>
    )
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 20,
        background: 'linear-gradient(135deg,#f5f8fc,#eef3fa)',
      }}
    >
      <Spin size="large" />
      <div style={{ color: '#5b6b7c', fontSize: 15 }}>{t('login.ssoCallbackProcessing')}</div>
      <Alert type="info" showIcon message={t('login.ssoHint')} style={{ maxWidth: 420 }} />
    </div>
  )
}
