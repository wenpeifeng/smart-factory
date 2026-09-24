import { Alert, Card, Col, Empty, Row, Statistic, Tag, Typography } from 'antd'
import {
  AccountBookOutlined,
  AlertOutlined,
  AuditOutlined,
  InboxOutlined,
  ExperimentOutlined,
  MoneyCollectOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
} from '@ant-design/icons'
import { motion } from 'framer-motion'
import { useQuery } from '@tanstack/react-query'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { getDashboard } from '@/api/report'
import { ChartEmpty, DeltaTag, fmtMoney, fmtQty, ReportSkeleton } from '@/pages/report/shared'

const { Title } = Typography

/** 指标卡定义（环比卡：cur/prev 成对；时点卡：只有 cur） */
interface KpiDef {
  title: string
  icon: React.ReactNode
  color: string
  value: number
  prev?: number
  /** 展示格式：money=金额（默认） / qty=数量 / count=整数计数 */
  fmt?: 'money' | 'qty' | 'count'
}

/** 指标卡：错峰入场 + hover 上浮（erp-hover-card 全局样式） */
function KpiCard({ kpi, index }: { kpi: KpiDef; index: number }) {
  const format = (v: number): string =>
    kpi.fmt === 'count' ? String(v) : kpi.fmt === 'qty' ? fmtQty(v) : fmtMoney(v)
  return (
    <motion.div
      initial={{ opacity: 0, y: 18 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.08, duration: 0.35, ease: 'easeOut' }}
    >
      <Card className="erp-hover-card">
        <Statistic
          title={
            <span style={{ color: '#5b6b7c' }}>
              {kpi.icon} {kpi.title}
            </span>
          }
          value={kpi.value}
          formatter={(v) => format(Number(v))}
          valueStyle={{ color: kpi.color, fontWeight: 700 }}
          suffix={
            kpi.prev !== undefined ? (
              <DeltaTag cur={kpi.value} prev={kpi.prev} />
            ) : (
              <span style={{ fontSize: 12, marginLeft: 8, color: '#8c939c' }}>时点值</span>
            )
          }
        />
      </Card>
    </motion.div>
  )
}

/** 单卡片入场动画包裹 */
function MotionCard({ delay, children }: { delay: number; children: React.ReactNode }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 18 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.35, ease: 'easeOut' }}
    >
      <Card className="erp-hover-card">{children}</Card>
    </motion.div>
  )
}

/**
 * 经营驾驶舱（真实数据版）。
 * 数据来自 GET /report/dashboard（跨模块只读聚合）；
 * 保留原演示驾驶舱的视觉语言：错峰入场 + hover 上浮 + Recharts 渐变面积图；
 * 全空数据时给 AntD Empty 引导（先录入业务单据）。
 */
export default function ReportDashboard() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'dashboard'],
    queryFn: getDashboard,
  })

  if (isLoading) {
    return (
      <div className="erp-page">
        <Title level={4} style={{ marginTop: 0 }}>
          经营驾驶舱
        </Title>
        <ReportSkeleton />
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className="erp-page">
        <Title level={4} style={{ marginTop: 0 }}>
          经营驾驶舱
        </Title>
        <Alert type="error" showIcon message="驾驶舱数据加载失败" description={String((error as Error)?.message ?? '')} />
      </div>
    )
  }

  // 全空判断：核心指标全为 0 且无任何走势/TOP 数据 → 空态引导
  const allZero =
    data.salesAmount === 0 &&
    data.purchaseAmount === 0 &&
    data.inventoryValue === 0 &&
    data.receivableBalance === 0 &&
    data.payableBalance === 0 &&
    data.topMaterials.length === 0 &&
    data.trends.every((t) => t.salesAmount === 0 && t.purchaseAmount === 0)

  if (allZero) {
    return (
      <div className="erp-page">
        <Title level={4} style={{ marginTop: 0 }}>
          经营驾驶舱
        </Title>
        <Card>
          <Empty
            description={
              <span>
                暂无经营数据 —— 先录入业务单据
                <br />
                <span style={{ color: '#8c939c', fontSize: 12 }}>
                  销售出库 / 采购入库等单据审核后，驾驶舱指标将自动汇总
                </span>
              </span>
            }
            style={{ padding: '48px 0' }}
          />
        </Card>
      </div>
    )
  }

  const mainKpis: KpiDef[] = [
    { title: '本月销售额(元)', icon: <ShopOutlined />, color: '#1668dc', value: data.salesAmount, prev: data.salesAmountPrev },
    { title: '本月采购额(元)', icon: <ShoppingCartOutlined />, color: '#36cfc9', value: data.purchaseAmount, prev: data.purchaseAmountPrev },
    { title: '库存总值(元)', icon: <InboxOutlined />, color: '#722ed1', value: data.inventoryValue },
    { title: '应收余额(元)', icon: <AccountBookOutlined />, color: '#fa8c16', value: data.receivableBalance },
  ]

  const subKpis: KpiDef[] = [
    { title: '应付余额(元)', icon: <MoneyCollectOutlined />, color: '#13c2c2', value: data.payableBalance },
    { title: '待办审批数', icon: <AuditOutlined />, color: '#1668dc', value: data.pendingApprovalCount, fmt: 'count' },
    { title: '库存预警数', icon: <AlertOutlined />, color: '#ff4d4f', value: data.stockWarningCount, fmt: 'count' },
    { title: '本月生产完工数', icon: <ExperimentOutlined />, color: '#52c41a', value: data.productionFinishQty, prev: data.productionFinishQtyPrev, fmt: 'qty' },
  ]

  const trendData = data.trends.map((t) => ({
    month: `${t.month.slice(5)}月`,
    销售: t.salesAmount,
    采购: t.purchaseAmount,
  }))
  const warehouseData = data.warehouseValues.map((w) => ({
    name: w.warehouseName ?? `仓库#${w.warehouseId}`,
    库存价值: w.value,
  }))
  const topData = data.topMaterials.map((t) => ({
    name: t.materialName ?? t.materialCode ?? `物料#${t.materialId}`,
    销售额: t.amount,
  }))

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        经营驾驶舱 <Tag color="blue">{data.month}</Tag>
      </Title>

      {/* 主指标卡：依次错峰入场（销售额/采购额带环比） */}
      <Row gutter={[16, 16]}>
        {mainKpis.map((k, i) => (
          <Col xs={24} sm={12} lg={6} key={k.title}>
            <KpiCard kpi={k} index={i} />
          </Col>
        ))}
      </Row>

      {/* 次指标卡：应付/待办/预警/完工 */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        {subKpis.map((k, i) => (
          <Col xs={12} sm={6} key={k.title}>
            <KpiCard kpi={k} index={4 + i} />
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={14}>
          <MotionCard delay={0.35}>
            <Card.Meta title={`销售 / 采购走势（近 6 个月，${data.trends[0]?.month ?? ''} ~ ${data.month}）`} />
            {trendData.length === 0 ? (
              <ChartEmpty description="暂无销售 / 采购数据" />
            ) : (
              <ResponsiveContainer width="100%" height={300}>
                <AreaChart data={trendData}>
                  <defs>
                    <linearGradient id="g-sales" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#1668dc" stopOpacity={0.35} />
                      <stop offset="100%" stopColor="#1668dc" stopOpacity={0.02} />
                    </linearGradient>
                    <linearGradient id="g-purchase" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#36cfc9" stopOpacity={0.35} />
                      <stop offset="100%" stopColor="#36cfc9" stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#eef1f5" />
                  <XAxis dataKey="month" stroke="#8c939c" />
                  <YAxis stroke="#8c939c" />
                  <Tooltip formatter={(v) => fmtMoney(Number(v))} />
                  <Legend />
                  <Area type="monotone" dataKey="销售" stroke="#1668dc" fill="url(#g-sales)" strokeWidth={2} />
                  <Area type="monotone" dataKey="采购" stroke="#36cfc9" fill="url(#g-purchase)" strokeWidth={2} />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </MotionCard>
        </Col>

        <Col xs={24} lg={10}>
          <MotionCard delay={0.45}>
            <Card.Meta title="各仓库库存价值分布（元）" />
            {warehouseData.length === 0 ? (
              <ChartEmpty description="暂无库存数据" />
            ) : (
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={warehouseData} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" stroke="#eef1f5" />
                  <XAxis type="number" stroke="#8c939c" tickFormatter={(v) => fmtMoney(Number(v))} />
                  <YAxis type="category" dataKey="name" stroke="#8c939c" width={70} />
                  <Tooltip formatter={(v) => fmtMoney(Number(v))} />
                  <Bar dataKey="库存价值" fill="#1668dc" radius={[0, 6, 6, 0]} barSize={18} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </MotionCard>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24}>
          <MotionCard delay={0.55}>
            <Card.Meta title={`当月销售 TOP${data.topMaterials.length || 5} 物料（元）`} />
            {topData.length === 0 ? (
              <ChartEmpty description="本月暂无销售出库" />
            ) : (
              <ResponsiveContainer width="100%" height={Math.max(180, topData.length * 56)}>
                <BarChart data={topData} layout="vertical" margin={{ left: 24, right: 24 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#eef1f5" />
                  <XAxis type="number" stroke="#8c939c" tickFormatter={(v) => fmtMoney(Number(v))} />
                  <YAxis type="category" dataKey="name" stroke="#8c939c" width={160} />
                  <Tooltip formatter={(v) => fmtMoney(Number(v))} />
                  <Bar dataKey="销售额" fill="#722ed1" radius={[0, 6, 6, 0]} barSize={16} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </MotionCard>
        </Col>
      </Row>
    </div>
  )
}
