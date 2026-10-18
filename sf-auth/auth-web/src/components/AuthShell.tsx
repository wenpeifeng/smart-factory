import type { ReactNode } from 'react'
import {
  AppstoreOutlined,
  AuditOutlined,
  CheckCircleFilled,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons'

interface AuthShellProps {
  children: ReactNode
}

/**
 * 登录与 OIDC 授权页共用的品牌外壳。
 * 左侧“身份关系轨道”表达认证中心的真实职责：身份、应用访问、审计。
 */
export default function AuthShell({ children }: AuthShellProps) {
  return (
    <div className="auth-page">
      <div className="auth-shell">
        <aside className="auth-story" aria-label="统一认证中心能力概览">
          <div className="auth-brand">
            <div className="auth-brand-mark" aria-hidden="true">
              <SafetyCertificateOutlined />
            </div>
            <div>
              <div className="auth-brand-name">统一认证中心</div>
              <div className="auth-brand-meta">SMART FACTORY IDENTITY</div>
            </div>
          </div>

          <div className="auth-story-copy">
            <span className="auth-eyebrow">企业身份与访问控制</span>
            <h1>一个账号，连接每个工作现场</h1>
            <p>统一管理员工身份、系统访问和安全审计，让 ERP 与 MES 使用同一套可信身份。</p>
          </div>

          <div className="identity-rail" aria-label="身份、访问、审计链路">
            <div className="identity-node">
              <span className="identity-node-icon"><UserOutlined /></span>
              <span><b>身份</b><small>主账号与组织</small></span>
            </div>
            <span className="identity-connector" aria-hidden="true"><i /></span>
            <div className="identity-node">
              <span className="identity-node-icon"><AppstoreOutlined /></span>
              <span><b>访问</b><small>应用分配与角色</small></span>
            </div>
            <span className="identity-connector" aria-hidden="true"><i /></span>
            <div className="identity-node">
              <span className="identity-node-icon identity-node-icon--audit"><AuditOutlined /></span>
              <span><b>审计</b><small>登录与操作留痕</small></span>
            </div>
          </div>

          <div className="auth-story-foot">
            <CheckCircleFilled />
            <span>OIDC 授权码 + PKCE · RS256 签名</span>
          </div>
        </aside>

        <main className="auth-form-panel">{children}</main>
      </div>
      <div className="auth-page-footer">Smart Factory Identity Service</div>
    </div>
  )
}
