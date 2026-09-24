import { useState } from 'react'
import { Alert, Button, Card, Col, DatePicker, Row, Space, Statistic } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { downloadReportCsv, getFinanceSummary } from '@/api/report'
import { fmtMoney, SourceTag } from '@/pages/report/shared'

/**
 * 财务汇总：期间营收/成本（总账凭证口径，期间无过账凭证时自动降级业务口径）+
 * 应收/应付余额（时点）+ 现金流净额。
 */
export default function FinancePane() {
  const [period, setPeriod] = useState<string>(dayjs().format('YYYY-MM'))

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'finance-summary', period],
    queryFn: () => getFinanceSummary(period),
  })

  const v = (n: number | undefined): number => n ?? 0

  return (
    <Card
      title="财务汇总"
      extra={
        <Space>
          <DatePicker
            picker="month"
            value={period ? dayjs(period) : null}
            onChange={(d) => setPeriod(d?.format('YYYY-MM') ?? dayjs().format('YYYY-MM'))}
            allowClear={false}
          />
          <Button
            icon={<DownloadOutlined />}
            onClick={() => downloadReportCsv('/report/finance/summary', { period }, `finance_summary_${period}.csv`)}
          >
            导出 CSV
          </Button>
        </Space>
      }
    >
      {isError && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`查询失败：${(error as Error)?.message ?? ''}`} />
      )}

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="营业收入(元)" value={fmtMoney(v(data?.revenue))} valueStyle={{ color: '#1668dc', fontWeight: 700 }} />
            <div style={{ marginTop: 4 }}>{data && <SourceTag source={data.revenueSource} />}</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="营业成本(元)" value={fmtMoney(v(data?.cost))} valueStyle={{ color: '#fa8c16', fontWeight: 700 }} />
            <div style={{ marginTop: 4 }}>{data && <SourceTag source={data.costSource} />}</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic
              title="毛利(元)"
              value={fmtMoney(v(data?.grossProfit))}
              valueStyle={{ color: v(data?.grossProfit) >= 0 ? '#52c41a' : '#ff4d4f', fontWeight: 700 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="毛利率" value={`${v(data?.grossMargin).toFixed(2)}%`} valueStyle={{ fontWeight: 700 }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="应收余额(元)" value={fmtMoney(v(data?.receivableBalance))} valueStyle={{ color: '#fa8c16' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="应付余额(元)" value={fmtMoney(v(data?.payableBalance))} valueStyle={{ color: '#13c2c2' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="现金流入(元)" value={fmtMoney(v(data?.cashIn))} valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="现金流出(元)" value={fmtMoney(v(data?.cashOut))} valueStyle={{ color: '#ff4d4f' }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col span={24}>
          <Card size="small" loading={isLoading}>
            <Statistic
              title={`现金流净额(元) · 期间 ${data?.period ?? period}`}
              value={fmtMoney(v(data?.cashNet))}
              valueStyle={{ fontSize: 24, fontWeight: 700, color: v(data?.cashNet) >= 0 ? '#52c41a' : '#ff4d4f' }}
            />
          </Card>
        </Col>
      </Row>
    </Card>
  )
}
