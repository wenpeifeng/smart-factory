import { ArrowDownOutlined, ArrowUpOutlined, MinusOutlined } from '@ant-design/icons'
import { Empty, Skeleton, Tag } from 'antd'

/**
 * 报表中心共用工具：金额/数量格式化、环比徽标、图表空态、加载骨架。
 * 金额单位为元（后端 BigDecimal 直传），展示千分位 + 2 位小数。
 */

/** 金额格式化：12345.6 → '12,345.60'；null/NaN → '-' */
export function fmtMoney(v: number | null | undefined): string {
  if (v == null || Number.isNaN(v)) return '-'
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 数量格式化：最多 4 位小数、去除尾零 */
export function fmtQty(v: number | null | undefined): string {
  if (v == null || Number.isNaN(v)) return '-'
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

/** 占比格式化（0~1 → 百分比） */
export function fmtRatio(ratio: number | null | undefined): string {
  if (ratio == null || Number.isNaN(ratio)) return '-'
  return `${(ratio * 100).toFixed(2)}%`
}

/** 环比涨跌幅文案：上期为 0 时本期也为 0 → 持平；本期有数 → '新增'；否则 ±x.x% */
export function momText(cur: number, prev: number): { text: string; up: boolean | null } {
  if (prev === 0) {
    return cur === 0 ? { text: '持平', up: null } : { text: '新增', up: true }
  }
  const pct = ((cur - prev) / prev) * 100
  if (Math.abs(pct) < 0.05) return { text: '持平', up: null }
  return { text: `${Math.abs(pct).toFixed(1)}%`, up: pct > 0 }
}

/** 环比徽标：涨绿跌红（与原驾驶舱演示风格一致），持平灰 */
export function DeltaTag({ cur, prev }: { cur: number; prev: number }) {
  const { text, up } = momText(cur, prev)
  const color = up == null ? '#8c939c' : up ? '#52c41a' : '#ff4d4f'
  const icon = up == null ? <MinusOutlined /> : up ? <ArrowUpOutlined /> : <ArrowDownOutlined />
  return (
    <span style={{ fontSize: 12, marginLeft: 8, color, whiteSpace: 'nowrap' }}>
      {icon} {text}
    </span>
  )
}

/** 图表区空态：无数据时用 Empty 替代空坐标系 */
export function ChartEmpty({ description = '暂无数据' }: { description?: string }) {
  return (
    <div style={{ height: 300, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={description} />
    </div>
  )
}

/** 报表加载骨架（指标卡/图表区域通用） */
export function ReportSkeleton({ cards = 4 }: { cards?: number }) {
  return (
    <div>
      <Skeleton.Button active block style={{ height: 90, marginBottom: 16 }} />
      <Skeleton active paragraph={{ rows: cards > 4 ? 8 : 6 }} />
    </div>
  )
}

/** 业务口径来源 Tag（财务汇总：凭证/业务） */
export function SourceTag({ source }: { source: 'VOUCHER' | 'BIZ' | string }) {
  return source === 'VOUCHER' ? (
    <Tag color="blue">总账凭证口径</Tag>
  ) : (
    <Tag color="orange">业务口径（期间无过账凭证）</Tag>
  )
}
