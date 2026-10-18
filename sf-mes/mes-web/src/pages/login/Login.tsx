import { useEffect, useState, type ReactNode } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { Alert, Button, Checkbox, Form, Input, Tag, Tooltip } from 'antd'
import {
  UserOutlined,
  LockOutlined,
  BankOutlined,
  SafetyCertificateOutlined,
  GlobalOutlined,
  CheckCircleFilled,
  ToolOutlined,
  DashboardOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons'
import { AnimatePresence, motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import { passwordLogin } from '@/api/oidc'
import { login, fetchMe, switchOrg, type AuthResp } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { changeLang, type Lang } from '@/locales'

interface LoginForm {
  username: string
  password: string
}

/** 记住账号的 localStorage key */
const REMEMBER_KEY = 'mes-login-username'

/** 窄屏断点：<768px 分屏降为单列（品牌区隐藏，登录卡全宽） */
function useIsNarrow() {
  const [narrow, setNarrow] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(max-width: 768px)').matches,
  )
  useEffect(() => {
    const mq = window.matchMedia('(max-width: 768px)')
    const fn = (e: MediaQueryListEvent) => setNarrow(e.matches)
    mq.addEventListener('change', fn)
    return () => mq.removeEventListener('change', fn)
  }, [])
  return narrow
}

/* ---------------- 入场编排（错峰渐入上浮） ---------------- */

const stagger = {
  hidden: {},
  show: { transition: { staggerChildren: 0.09, delayChildren: 0.15 } },
}
const riseIn = {
  hidden: { opacity: 0, y: 18 },
  show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: 'easeOut' as const } },
}

/* ---------------- 左侧品牌区：工位流转线 + 现场指标卡 + 价值点轮播 ---------------- */

/** 直角工位流转线：区别于 ERP 的经营数据曲线，表达车间过站与工序衔接。 */
function ProductionLines() {
  const paths = [
    { d: 'M-40,210 H190 V300 H500 V205 H850 V330 H1240', color: '#ffb547', dur: 14 },
    { d: 'M-40,455 H270 V385 H620 V515 H970 V430 H1240', color: '#7fe3c8', dur: 20 },
    { d: 'M-40,650 H160 V575 H470 V665 H780 V560 H1080 V640 H1240', color: '#2da680', dur: 26 },
  ]
  const stations = [
    [190, 300], [500, 205], [850, 330], [270, 385], [620, 515], [970, 430],
  ]
  return (
    <svg
      viewBox="0 0 1200 800"
      preserveAspectRatio="none"
      style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', opacity: 0.48 }}
    >
      {paths.map((p, i) => (
        <motion.path
          key={i}
          d={p.d}
          fill="none"
          stroke={p.color}
          strokeWidth={1.6}
          strokeDasharray="5 16"
          strokeLinecap="round"
          style={{ filter: `drop-shadow(0 0 6px ${p.color})` }}
          initial={{ pathLength: 0, opacity: 0 }}
          animate={{ pathLength: 1, opacity: [0.25, 0.8, 0.25], strokeDashoffset: [0, -420] }}
          transition={{
            pathLength: { duration: 1.6, delay: 0.3 + i * 0.25, ease: 'easeOut' },
            opacity: { duration: 3.5, repeat: Infinity, delay: i * 0.6 },
            strokeDashoffset: { duration: p.dur, repeat: Infinity, ease: 'linear' },
          }}
        />
      ))}
      {stations.map(([cx, cy], index) => (
        <motion.circle
          key={`${cx}-${cy}`}
          cx={cx}
          cy={cy}
          r="6"
          fill={index % 2 === 0 ? '#ffb547' : '#7fe3c8'}
          initial={{ opacity: 0, scale: 0.5 }}
          animate={{ opacity: [0.45, 1, 0.45], scale: [0.8, 1.2, 0.8] }}
          transition={{ duration: 2.8, repeat: Infinity, delay: index * 0.3 }}
        />
      ))}
    </svg>
  )
}

/** 浮动现场指标卡。 */
function MetricCard({
  icon,
  value,
  label,
  style,
  delay,
}: {
  icon: ReactNode
  value: string
  label: string
  style: React.CSSProperties
  delay: number
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 30, scale: 0.9 }}
      animate={{ opacity: 1, y: [0, -9, 0], scale: 1 }}
      transition={{
        opacity: { duration: 0.6, delay },
        scale: { duration: 0.5, delay },
        y: { duration: 5, repeat: Infinity, ease: 'easeInOut', delay: delay + 0.5 },
      }}
      style={{
        position: 'absolute',
        padding: '14px 18px',
        borderRadius: 10,
        background: 'rgba(255,255,255,0.72)',
        border: '1px solid rgba(22,118,92,0.16)',
        backdropFilter: 'blur(12px)',
        WebkitBackdropFilter: 'blur(12px)',
        color: '#173d33',
        boxShadow: '0 10px 28px rgba(35,91,72,0.14)',
        ...style,
      }}
    >
      <div style={{ fontSize: 20, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ color: '#ffb547' }}>{icon}</span>
        {value}
      </div>
      <div style={{ fontSize: 12, color: '#5f796f', marginTop: 4 }}>{label}</div>
    </motion.div>
  )
}

/** 品牌区（左 45%）：深色渐变 + 动态元素 + 价值点轮播 */
function BrandPanel({
  title,
  subtitle,
  values,
  metrics,
}: {
  title: string
  subtitle: string
  values: { title: string; desc: string }[]
  metrics: { wip: string; quality: string; equipment: string }
}) {
  const [idx, setIdx] = useState(0)
  useEffect(() => {
    const timer = setInterval(() => setIdx((i) => (i + 1) % values.length), 4200)
    return () => clearInterval(timer)
  }, [values.length])

  return (
    <div
      style={{
        width: '45%',
        height: '100%',
        position: 'relative',
        overflow: 'hidden',
        background: 'linear-gradient(155deg, #f1f8f3 0%, #e3f1e9 58%, #d5eadf 130%)',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        padding: '48px 56px',
      }}
    >
      {/* 柔光斑 + 工位流转线 */}
      <div
        style={{
          position: 'absolute',
          width: 520,
          height: 520,
          left: -140,
          top: -120,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(255,181,71,0.16) 0%, transparent 65%)',
        }}
      />
      <div
        style={{
          position: 'absolute',
          width: 460,
          height: 460,
          right: -120,
          bottom: -140,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(45,166,128,0.16) 0%, transparent 65%)',
        }}
      />
      <ProductionLines />

      {/* 浮动指标卡 */}
      <MetricCard
        icon={<ToolOutlined />}
        value="WIP"
        label={metrics.wip}
        style={{ left: '16%', top: '24%' }}
        delay={0.9}
      />
      <MetricCard
        icon={<CheckCircleOutlined />}
        value="FPY"
        label={metrics.quality}
        style={{ right: '12%', top: '38%' }}
        delay={1.15}
      />
      <MetricCard
        icon={<DashboardOutlined />}
        value="OEE"
        label={metrics.equipment}
        style={{ left: '22%', bottom: '30%' }}
        delay={1.4}
      />

      {/* 品牌 Logo */}
      <motion.div variants={riseIn} style={{ display: 'flex', alignItems: 'center', gap: 12, zIndex: 2 }}>
        <div
          style={{
            width: 42,
            height: 42,
            borderRadius: 9,
            background: 'linear-gradient(135deg,#16765c,#ffb547)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 20,
            fontWeight: 800,
            color: '#fff',
            boxShadow: '0 6px 20px rgba(22,118,92,0.52)',
          }}
        >
          M
        </div>
        <div>
          <div style={{ color: '#173d33', fontSize: 19, fontWeight: 700 }}>{title}</div>
          <div style={{ color: '#5f796f', fontSize: 12 }}>{subtitle}</div>
        </div>
      </motion.div>

      {/* 价值点轮播 */}
      <div style={{ zIndex: 2, maxWidth: 460 }}>
        <AnimatePresence mode="wait">
          <motion.div
            key={idx}
            initial={{ opacity: 0, y: 22 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -16 }}
            transition={{ duration: 0.45, ease: 'easeOut' }}
          >
            <div style={{ height: 4, width: 42, borderRadius: 2, background: '#ffb547', marginBottom: 18 }} />
            <div style={{ color: '#173d33', fontSize: 27, fontWeight: 700, lineHeight: 1.35 }}>
              {values[idx]?.title}
            </div>
            <div style={{ color: '#526f65', fontSize: 14.5, marginTop: 12, lineHeight: 1.7 }}>
              {values[idx]?.desc}
            </div>
          </motion.div>
        </AnimatePresence>
        {/* 轮播指示器 */}
        <div style={{ display: 'flex', gap: 8, marginTop: 26 }}>
          {values.map((_, i) => (
            <div
              key={i}
              style={{
                width: i === idx ? 22 : 8,
                height: 4,
                borderRadius: 2,
                background: i === idx ? '#d99125' : 'rgba(22,118,92,0.20)',
                transition: 'all .3s',
              }}
            />
          ))}
        </div>
      </div>

      <div style={{ color: '#71877e', fontSize: 12, zIndex: 2 }}>
        © 2026 MES · Shop-floor execution & traceability
      </div>
    </div>
  )
}

/* ---------------- 聚焦高亮输入域 ---------------- */

/** 输入框聚焦动效：使用 MES 石墨绿与安全橙。 */
function FocusField({ children }: { children: ReactNode }) {
  const [focused, setFocused] = useState(false)
  return (
    <div
      onFocus={() => setFocused(true)}
      onBlur={() => setFocused(false)}
      style={{ position: 'relative' }}
    >
      <motion.div
        animate={{
          boxShadow: focused
            ? '0 4px 18px rgba(22,118,92,0.16)'
            : '0 0 0 0 rgba(22,118,92,0)',
          borderColor: focused ? 'rgba(22,118,92,0.55)' : 'rgba(0,0,0,0)',
        }}
        style={{ borderRadius: 10 }}
      >
        {children}
      </motion.div>
      {/* 底部高亮线：scaleX 0→1 */}
      <motion.div
        animate={{ scaleX: focused ? 1 : 0, opacity: focused ? 1 : 0 }}
        transition={{ duration: 0.28, ease: 'easeOut' }}
        style={{
          position: 'absolute',
          left: '12%',
          right: '12%',
          bottom: 4,
          height: 2,
          borderRadius: 1,
          background: 'linear-gradient(90deg,#16765c,#ffb547)',
          transformOrigin: 'center',
          boxShadow: '0 0 8px rgba(22,118,92,0.45)',
        }}
      />
    </div>
  )
}

/* ---------------- 登录页主体 ---------------- */

/**
 * MES 登录页：石墨绿车间基调 + 安全橙重点色。
 * 左 45% 品牌区使用工位流转线、现场指标卡和制造执行价值点，
 * 右玻璃拟态登录卡；两步式：账号密码 →（多组织时）选择进入的组织 → switch-org 重签 → 进门户。
 * 微交互：错峰入场、输入聚焦高亮、按钮 loading、密码错误卡片左右抖动。
 * <768px 窄屏自动降为单列（品牌区隐藏）。
 */
export default function Login() {
  const { t, i18n } = useTranslation()
  const [form] = Form.useForm<LoginForm>()
  const [loading, setLoading] = useState(false)
  const [shake, setShake] = useState(false)
  const [remember, setRemember] = useState(() => !!localStorage.getItem(REMEMBER_KEY))
  const [showEmergency, setShowEmergency] = useState(false)
  /** 两步式：form=账号密码，org=选择组织 */
  const [step, setStep] = useState<'form' | 'org'>('form')
  const [authResp, setAuthResp] = useState<AuthResp | null>(null)
  const [enteringOrg, setEnteringOrg] = useState<number | null>(null)
  const navigate = useNavigate()
  const location = useLocation()
  const setAuth = useAuthStore((s) => s.setAuth)
  const setContext = useAuthStore((s) => s.setContext)
  const isNarrow = useIsNarrow()

  const values = t('login.values', { returnObjects: true }) as unknown as {
    title: string
    desc: string
  }[]

  /** 回跳来源页（守卫重定向时记录），默认工作台 */
  const from = (location.state as { from?: string })?.from ?? '/dashboard'

  /** 第一步：企业账号默认走认证中心；应急模式才使用 MES 本地账号。 */
  const onFinish = async (formValue: LoginForm) => {
    setLoading(true)
    try {
      const resp = showEmergency ? await login(formValue) : await passwordLogin(formValue)
      setAuth(resp.token, resp.user, {
        tenantId: resp.tenantId,
        orgId: resp.orgId,
        orgs: resp.orgs ?? [],
      })
      if (remember) localStorage.setItem(REMEMBER_KEY, formValue.username)
      else localStorage.removeItem(REMEMBER_KEY)
      // 登录即拉取权限上下文（角色+按钮权限码），供 HasPerm/usePerm 做按钮级显隐
      try {
        const me = await fetchMe()
        setContext(me.roles ?? [], me.perms ?? [])
      } catch {
        // 权限上下文拉取失败不阻断登录，MainLayout 挂载时会重试补拉
      }
      if ((resp.orgs?.length ?? 0) > 1) {
        // 多组织：进入第二步选择组织（token 已带默认组织上下文）
        setAuthResp(resp)
        setStep('org')
      } else {
        navigate(from, { replace: true })
      }
    } catch {
      // 错误提示已由 axios 拦截器统一弹出；此处触发卡片抖动
      setShake(true)
    } finally {
      setLoading(false)
    }
  }

  /** 第二步：选择组织 → switch-org 重签令牌 → 进门户 */
  const enterOrg = async (orgId: number) => {
    if (!authResp || enteringOrg !== null) return
    setEnteringOrg(orgId)
    try {
      const resp = await switchOrg(orgId)
      setAuth(resp.token, resp.user, {
        tenantId: resp.tenantId,
        orgId: resp.orgId,
        orgs: resp.orgs ?? [],
      })
      navigate(from, { replace: true })
    } catch {
      setEnteringOrg(null)
    }
  }

  /** 玻璃拟态卡片（light） */
  const cardBase: React.CSSProperties = {
    width: isNarrow ? 'calc(100vw - 40px)' : 420,
    maxWidth: 420,
    padding: isNarrow ? '30px 26px' : '40px 40px',
    borderRadius: 16,
    background: 'rgba(255,255,255,0.86)',
    backdropFilter: 'blur(18px) saturate(1.2)',
    WebkitBackdropFilter: 'blur(18px) saturate(1.2)',
    border: '1px solid rgba(22,118,92,0.12)',
    borderTop: '3px solid #ffb547',
    boxShadow: '0 24px 64px rgba(13,45,36,0.14), inset 0 1px 0 rgba(255,255,255,0.9)',
  }

  const tenantLabel = (id: number | null | undefined) =>
    id === 1000 ? t('login.tenantDefault') : `${t('login.tenantPrefix')} ${id ?? '-'}`

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden', background: '#f5f7fa' }}>
      {/* 左：品牌区（窄屏隐藏，降为单列） */}
      {!isNarrow && (
        <BrandPanel
          title={t('login.brand.title')}
          subtitle={t('login.brand.subtitle')}
          values={values}
          metrics={{
            wip: t('login.metrics.wip'),
            quality: t('login.metrics.quality'),
            equipment: t('login.metrics.equipment'),
          }}
        />
      )}

      {/* 右：登录区（浅色底 + 光晕） */}
      <div
        style={{
          flex: 1,
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background:
            'linear-gradient(rgba(22,118,92,0.035) 1px, transparent 1px),' +
            'linear-gradient(90deg, rgba(22,118,92,0.035) 1px, transparent 1px),' +
            'radial-gradient(800px 520px at 85% 8%, rgba(255,181,71,0.12), transparent 62%),' +
            '#f3f6f2',
          backgroundSize: '32px 32px, 32px 32px, auto, auto',
        }}
      >
        {/* 语言切换（右上角悬浮） */}
        <Tooltip title={t('topbar.language')} placement="left">
          <Button
            type="text"
            icon={<GlobalOutlined />}
            onClick={() => changeLang((i18n.language.startsWith('zh') ? 'en' : 'zh') as Lang)}
            style={{ position: 'absolute', top: 18, right: 22 }}
          >
            {i18n.language.startsWith('zh') ? 'EN' : '中文'}
          </Button>
        </Tooltip>

        {/* 登录卡：入场编排 + 密码错误抖动（x keyframes） */}
        <motion.div
          initial={{ opacity: 0, y: 28, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.5, ease: 'easeOut' }}
        >
          <motion.div
            animate={shake ? 'shake' : 'stable'}
            variants={{
              stable: { x: 0 },
              shake: { x: [0, -14, 14, -10, 10, -6, 6, -3, 3, 0] },
            }}
            transition={{ duration: 0.55 }}
            onAnimationComplete={() => setShake(false)}
            style={cardBase}
          >
            <motion.div variants={stagger} initial="hidden" animate="show">
              {/* 窄屏时补一个品牌头（品牌区已隐藏） */}
              {isNarrow && (
                <motion.div variants={riseIn} style={{ textAlign: 'center', marginBottom: 18 }}>
                  <div
                    style={{
                      width: 40,
                      height: 40,
                      margin: '0 auto 10px',
                      borderRadius: 9,
                      background: 'linear-gradient(135deg,#16765c,#ffb547)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: 19,
                      fontWeight: 800,
                      color: '#fff',
                    }}
                  >
                    M
                  </div>
                  <div style={{ fontSize: 17, fontWeight: 700, color: '#0a1929' }}>
                    {t('login.brand.title')}
                  </div>
                </motion.div>
              )}

              {step === 'form' ? (
                <>
                  <motion.div variants={riseIn} style={{ marginBottom: 26 }}>
                    <div
                      style={{
                        color: '#16765c',
                        fontSize: 11,
                        fontWeight: 750,
                        letterSpacing: 1.8,
                        marginBottom: 8,
                      }}
                    >
                      MES · SHOP FLOOR EXECUTION
                    </div>
                    <div style={{ fontSize: 24, fontWeight: 750, color: '#0a1929' }}>
                      {showEmergency ? t('login.emergencyTitle') : t('login.welcome')}
                    </div>
                    <div style={{ color: '#7b8794', fontSize: 13.5, marginTop: 6 }}>
                      {showEmergency ? t('login.emergencySubtitle') : t('login.subtitle')}
                    </div>
                  </motion.div>

                  {showEmergency && (
                    <motion.div variants={riseIn} style={{ marginBottom: 18 }}>
                      <Alert type="warning" showIcon message={t('login.emergencyHint')} />
                    </motion.div>
                  )}
                  <Form<LoginForm>
                    form={form}
                    onFinish={onFinish}
                    initialValues={{ username: localStorage.getItem(REMEMBER_KEY) ?? '' }}
                  >
                    <motion.div variants={riseIn}>
                      <FocusField>
                        <Form.Item
                          name="username"
                          rules={[{ required: true, message: t('login.usernameRequired') }]}
                        >
                          <Input
                            size="large"
                            prefix={<UserOutlined style={{ color: '#9aa5b1' }} />}
                            placeholder={t('login.usernamePlaceholder')}
                            autoComplete="username"
                          />
                        </Form.Item>
                      </FocusField>
                    </motion.div>
                    <motion.div variants={riseIn}>
                      <FocusField>
                        <Form.Item
                          name="password"
                          rules={[{ required: true, message: t('login.passwordRequired') }]}
                        >
                          <Input.Password
                            size="large"
                            prefix={<LockOutlined style={{ color: '#9aa5b1' }} />}
                            placeholder={t('login.passwordPlaceholder')}
                            autoComplete="current-password"
                          />
                        </Form.Item>
                      </FocusField>
                    </motion.div>
                    <motion.div variants={riseIn} style={{ marginBottom: 18 }}>
                      <Checkbox checked={remember} onChange={(e) => setRemember(e.target.checked)}>
                        {t('login.remember')}
                      </Checkbox>
                    </motion.div>
                    <motion.div variants={riseIn}>
                      <Button
                        type="primary"
                        size="large"
                        block
                        htmlType="submit"
                        loading={loading}
                        style={{ height: 46, fontWeight: 650, background: '#16765c', borderColor: '#16765c' }}
                      >
                        {loading
                          ? t('login.submitting')
                          : showEmergency
                            ? t('login.emergencySubmit')
                            : t('login.submit')}
                      </Button>
                    </motion.div>
                  </Form>
                  {!showEmergency && (
                    <motion.div
                      variants={riseIn}
                      style={{ color: '#71877e', fontSize: 12, textAlign: 'center', marginTop: 10 }}
                    >
                      <SafetyCertificateOutlined style={{ marginRight: 6, color: '#16765c' }} />
                      {t('login.ssoHint')}
                    </motion.div>
                  )}
                  <motion.div variants={riseIn}>
                    <div style={{ borderTop: '1px solid #e3ebe6', margin: '22px 0 10px' }} />
                    <div style={{ textAlign: 'center' }}>
                      <Button
                        type="link"
                        onClick={() => {
                          setShowEmergency((value) => !value)
                          form.setFieldValue('password', '')
                        }}
                        style={{ color: '#58736a' }}
                      >
                        {showEmergency ? t('login.backToSso') : t('login.emergencyEntry')}
                      </Button>
                    </div>
                  </motion.div>
                </>
              ) : (
                /* ---------- 第二步：选择进入的组织 ---------- */
                <>
                  <motion.div variants={riseIn} style={{ marginBottom: 22 }}>
                    <div style={{ fontSize: 21, fontWeight: 750, color: '#0a1929' }}>
                      {t('login.step2Title')}
                    </div>
                    <div style={{ display: 'flex', gap: 8, marginTop: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                      <Tag icon={<SafetyCertificateOutlined />} color="green">
                        {tenantLabel(authResp?.tenantId)}
                      </Tag>
                      <span style={{ color: '#7b8794', fontSize: 13 }}>{t('login.step2Subtitle')}</span>
                    </div>
                  </motion.div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                    {(authResp?.orgs ?? []).map((org, i) => {
                      const isCurrent = authResp?.orgId === org.id
                      return (
                        <motion.div
                          key={org.id}
                          initial={{ opacity: 0, y: 16 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ delay: 0.1 + i * 0.08, duration: 0.4, ease: 'easeOut' }}
                          whileHover={{ y: -5, boxShadow: '0 14px 36px rgba(22,118,92,0.20)' }}
                          whileTap={{ scale: 0.985 }}
                          onClick={() => enterOrg(org.id)}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 14,
                            padding: '14px 18px',
                            borderRadius: 14,
                            border: isCurrent
                              ? '1.5px solid rgba(22,118,92,0.65)'
                              : '1px solid rgba(22,118,92,0.14)',
                            background: isCurrent ? 'rgba(22,118,92,0.06)' : 'rgba(255,255,255,0.8)',
                            cursor: enteringOrg === null ? 'pointer' : 'wait',
                            boxShadow: '0 4px 16px rgba(10,25,41,0.06)',
                          }}
                        >
                          <div
                            style={{
                              width: 42,
                              height: 42,
                              borderRadius: 12,
                              background: 'linear-gradient(135deg,rgba(22,118,92,0.13),rgba(255,181,71,0.16))',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              color: '#16765c',
                              fontSize: 19,
                            }}
                          >
                            <BankOutlined />
                          </div>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ fontWeight: 650, color: '#0a1929', fontSize: 15 }}>
                              {org.name}
                            </div>
                            <div style={{ color: '#8c939c', fontSize: 12.5, marginTop: 2 }}>
                              {org.code}
                            </div>
                          </div>
                          {isCurrent && (
                            <Tag color="green" icon={<CheckCircleFilled />} style={{ marginRight: 0 }}>
                              {t('login.currentOrg')}
                            </Tag>
                          )}
                          {enteringOrg === org.id && (
                            <span style={{ color: '#16765c', fontSize: 13 }}>{t('login.entering')}</span>
                          )}
                        </motion.div>
                      )
                    })}
                  </div>

                  <motion.div variants={riseIn} style={{ marginTop: 18, textAlign: 'center' }}>
                    <Button type="link" onClick={() => setStep('form')} style={{ padding: 0 }}>
                      {t('login.backToStep1')}
                    </Button>
                  </motion.div>
                </>
              )}
            </motion.div>
          </motion.div>
        </motion.div>
      </div>
    </div>
  )
}
