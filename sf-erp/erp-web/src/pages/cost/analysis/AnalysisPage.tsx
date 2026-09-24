import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, Col, DatePicker, Row, Space, Typography } from 'antd'
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import dayjs from 'dayjs'
import SummaryTable from '@/pages/cost/components/SummaryTable'
import { fetchMaterialSummary, fetchTrend, fmtAmount, fmtPeriod } from '@/api/cost'

const { Title } = Typography

/**
 * 成本分析页：月度趋势图（主营业务成本/生产领料/期末存货余额）+ 物料收发存汇总。
 * 图表配色与驾驶舱一致（主色 #1668dc 系），信息密度优先。
 */
export default function AnalysisPage() {
  const [from, setFrom] = useState(dayjs().subtract(5, 'month').startOf('month'))
  const [to, setTo] = useState(dayjs())
  const [period, setPeriod] = useState(dayjs().format('YYYYMM'))

  const { data: trend, isLoading: trendLoading } = useQuery({
    queryKey: ['cost', 'trend', from.format('YYYYMM'), to.format('YYYYMM')],
    queryFn: () => fetchTrend(from.format('YYYYMM'), to.format('YYYYMM')),
  })
  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['cost', 'material-summary', period],
    queryFn: () => fetchMaterialSummary(period),
  })

  const chartData = (trend ?? []).map((p) => ({
    period: fmtPeriod(p.period),
    主营业务成本: p.salOutCost,
    生产领料成本: p.prodUseCost,
    期末存货余额: p.stockBalance,
  }))

  return (
    <div className="erp-page">
      <Title level={4} style={{ marginTop: 0 }}>
        成本分析
      </Title>

      <Card
        title="月度成本趋势"
        size="small"
        extra={
          <Space>
            <DatePicker
              picker="month"
              value={from}
              onChange={(d) => d && setFrom(d)}
              allowClear={false}
              size="small"
            />
            <span>至</span>
            <DatePicker
              picker="month"
              value={to}
              onChange={(d) => d && setTo(d)}
              allowClear={false}
              size="small"
            />
          </Space>
        }
        styles={{ body: { minHeight: 200 } }}
      >
        {trendLoading ? (
          <div style={{ height: 320, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            图表加载中…
          </div>
        ) : chartData.length === 0 ? (
          <div
            style={{ height: 320, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}
          >
            所选区间暂无计价数据，请先在「存货计价」页执行期间重算
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={320}>
            <ComposedChart data={chartData} margin={{ top: 8, right: 16, bottom: 0, left: 8 }}>
              <defs>
                <linearGradient id="costBar" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#1668dc" stopOpacity={0.85} />
                  <stop offset="100%" stopColor="#1668dc" stopOpacity={0.45} />
                </linearGradient>
                <linearGradient id="prodBar" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#722ed1" stopOpacity={0.85} />
                  <stop offset="100%" stopColor="#722ed1" stopOpacity={0.45} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="period" tick={{ fontSize: 12 }} />
              <YAxis
                yAxisId="amount"
                tickFormatter={(v: number) => fmtAmount(v)}
                tick={{ fontSize: 12 }}
                width={100}
              />
              <Tooltip
                formatter={(value) => fmtAmount(Number(value))}
                labelStyle={{ fontWeight: 600 }}
              />
              <Legend />
              <Bar
                yAxisId="amount"
                dataKey="主营业务成本"
                fill="url(#costBar)"
                radius={[4, 4, 0, 0]}
                maxBarSize={36}
              />
              <Bar
                yAxisId="amount"
                dataKey="生产领料成本"
                fill="url(#prodBar)"
                radius={[4, 4, 0, 0]}
                maxBarSize={36}
              />
              <Line
                yAxisId="amount"
                type="monotone"
                dataKey="期末存货余额"
                stroke="#fa8c16"
                strokeWidth={2}
                dot={{ r: 3 }}
              />
            </ComposedChart>
          </ResponsiveContainer>
        )}
      </Card>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col span={24}>
          <Card
            title="物料收发存汇总"
            size="small"
            extra={
              <DatePicker
                picker="month"
                value={dayjs(period, 'YYYYMM')}
                onChange={(d) => d && setPeriod(d.format('YYYYMM'))}
                allowClear={false}
                size="small"
              />
            }
          >
            <SummaryTable summary={summary} loading={summaryLoading} />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
