import { lazy, Suspense, useState } from 'react'
import type { ReactNode } from 'react'
import { Empty, Layout, Menu, Spin, Typography } from 'antd'
import {
  AppstoreOutlined,
  ClusterOutlined,
  ContainerOutlined,
  DashboardOutlined,
  FundOutlined,
  ShopOutlined,
  TeamOutlined,
  ToolOutlined,
} from '@ant-design/icons'

const Sider = Layout.Sider
const Content = Layout.Content

/** 既有报表（懒加载分包） */
const SalesPane = lazy(() => import('@/pages/report/center/SalesPane'))
const PurchasePane = lazy(() => import('@/pages/report/center/PurchasePane'))
const AgingPane = lazy(() => import('@/pages/report/center/AgingPane'))
const FinancePane = lazy(() => import('@/pages/report/center/FinancePane'))
/** 新增制造业标准报表（懒加载分包） */
const MonthlySummaryPane = lazy(() => import('@/pages/report/center/MonthlySummaryPane'))
const MarginPane = lazy(() => import('@/pages/report/center/MarginPane'))
const SupplierPerformancePane = lazy(() => import('@/pages/report/center/SupplierPerformancePane'))
const InventoryMonthlyPane = lazy(() => import('@/pages/report/center/InventoryMonthlyPane'))
const SluggishPane = lazy(() => import('@/pages/report/center/SluggishPane'))
const ExecutionPane = lazy(() => import('@/pages/report/center/ExecutionPane'))
const WorkhoursPane = lazy(() => import('@/pages/report/center/WorkhoursPane'))
const MaterialLossPane = lazy(() => import('@/pages/report/center/MaterialLossPane'))
const ExpensePane = lazy(() => import('@/pages/report/center/ExpensePane'))
const PiecewagePane = lazy(() => import('@/pages/report/center/PiecewagePane'))
/** 财务三大报表深化（erp-biz-finance 后端，科目余额取数 + 应收应付账龄） */
const BalanceSheetPane = lazy(() => import('@/pages/report/center/BalanceSheetPane'))
const IncomeStatementPane = lazy(() => import('@/pages/report/center/IncomeStatementPane'))
const ArAgingPane = lazy(() => import('@/pages/report/center/ArAgingPane'))
const ApAgingPane = lazy(() => import('@/pages/report/center/ApAgingPane'))

/** 报表项：key = 路由内选中态，label = 目录名，pane = 右侧内容 */
interface ReportEntry {
  key: string
  label: string
  pane: ReactNode
}

/** 报表分组目录（对标金蝶/用友/帆软制造业标准报表分组） */
const REPORT_GROUPS: { key: string; label: string; icon: ReactNode; reports: ReportEntry[] }[] = [
  {
    key: 'biz',
    label: '经营',
    icon: <DashboardOutlined />,
    reports: [{ key: 'monthly-summary', label: '经营月报', pane: <MonthlySummaryPane /> }],
  },
  {
    key: 'sales',
    label: '销售',
    icon: <ShopOutlined />,
    reports: [{ key: 'margin', label: '销售毛利分析', pane: <MarginPane /> }],
  },
  {
    key: 'purchase',
    label: '采购',
    icon: <ContainerOutlined />,
    reports: [{ key: 'supplier-performance', label: '供应商绩效', pane: <SupplierPerformancePane /> }],
  },
  {
    key: 'inventory',
    label: '库存',
    icon: <AppstoreOutlined />,
    reports: [
      { key: 'inventory-monthly', label: '收发存月报', pane: <InventoryMonthlyPane /> },
      { key: 'sluggish', label: '呆滞物料', pane: <SluggishPane /> },
      { key: 'aging', label: '库存库龄', pane: <AgingPane /> },
    ],
  },
  {
    key: 'production',
    label: '生产',
    icon: <ToolOutlined />,
    reports: [
      { key: 'execution', label: '订单执行跟踪', pane: <ExecutionPane /> },
      { key: 'workhours', label: '报工工时统计', pane: <WorkhoursPane /> },
      { key: 'material-loss', label: '材料损耗分析', pane: <MaterialLossPane /> },
    ],
  },
  {
    key: 'finance',
    label: '财务',
    icon: <FundOutlined />,
    reports: [
      { key: 'finance-summary', label: '财务汇总', pane: <FinancePane /> },
      { key: 'expense', label: '费用分析', pane: <ExpensePane /> },
      { key: 'balance-sheet', label: '资产负债表', pane: <BalanceSheetPane /> },
      { key: 'income-statement', label: '利润表', pane: <IncomeStatementPane /> },
      { key: 'ar-aging', label: '应收账龄分析', pane: <ArAgingPane /> },
      { key: 'ap-aging', label: '应付账龄分析', pane: <ApAgingPane /> },
    ],
  },
  {
    key: 'hr',
    label: '人事',
    icon: <TeamOutlined />,
    reports: [{ key: 'piecewage', label: '计件工资汇总', pane: <PiecewagePane /> }],
  },
  {
    key: 'multi',
    label: '多维',
    icon: <ClusterOutlined />,
    reports: [
      { key: 'sales-detail', label: '销售明细', pane: <SalesPane /> },
      { key: 'purchase-detail', label: '采购明细', pane: <PurchasePane /> },
    ],
  },
]

/** 全部报表项索引（key → entry） */
const ALL_REPORTS = new Map(REPORT_GROUPS.flatMap((g) => g.reports).map((r) => [r.key, r]))

/** 分组目录菜单项（Menu type=group：分组标签 + 子报表项） */
const MENU_ITEMS = REPORT_GROUPS.map((g) => ({
  key: g.key,
  label: (
    <span>
      {g.icon} {g.label}
    </span>
  ),
  type: 'group' as const,
  children: g.reports.map((r) => ({ key: r.key, label: r.label })),
}))

/** 面板懒加载 loading 态（嵌套模式让 tip 文案生效） */
function PaneLoading() {
  return (
    <Spin tip="报表加载中...">
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 240 }} />
    </Spin>
  )
}

/**
 * 报表中心：左侧分组目录（经营/销售/采购/库存/生产/财务/人事/多维）
 * + 右侧参数面板 + Table/图表；全部报表带 CSV 导出，口径见各面板脚注。
 * 路由不变（report/center 单页内分组切换，默认展示经营月报）。
 */
export default function ReportCenter() {
  const [activeKey, setActiveKey] = useState('monthly-summary')
  const entry = ALL_REPORTS.get(activeKey)

  return (
    <div className="erp-page">
      <Layout hasSider style={{ background: 'transparent' }}>
        <Sider width={200} theme="light" style={{ borderRight: '1px solid #f0f0f0', background: 'transparent' }}>
          <Typography.Text strong style={{ display: 'block', padding: '4px 16px 8px' }}>
            报表目录
          </Typography.Text>
          <Menu
            mode="inline"
            items={MENU_ITEMS}
            selectedKeys={[activeKey]}
            onClick={(e) => setActiveKey(e.key)}
            style={{ borderInlineEnd: 'none', background: 'transparent' }}
          />
        </Sider>
        <Content style={{ paddingLeft: 16, minWidth: 0 }}>
          {entry ? (
            <Suspense key={activeKey} fallback={<PaneLoading />}>
              {entry.pane}
            </Suspense>
          ) : (
            <Empty description="请选择左侧报表" style={{ marginTop: 80 }} />
          )}
        </Content>
      </Layout>
    </div>
  )
}
