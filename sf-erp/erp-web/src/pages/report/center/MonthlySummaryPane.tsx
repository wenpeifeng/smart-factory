import { useState } from 'react'
import { Alert, Button, Card, Col, DatePicker, Row, Space, Statistic, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { downloadReportCsv, getMonthlySummary } from '@/api/report'
import { fmtMoney, fmtQty, SourceTag } from '@/pages/report/shared'

/**
 * 经营月报：一页纸指标 —— 完工工单数/产量/销量/销售额/采购额/毛利率/费用合计/
 * 应收应付/库存总值。口径沿用驾驶舱与财务汇总（凭证优先，业务降级）。
 */
export default function MonthlySummaryPane() {
  const [month, setMonth] = useState<string>(dayjs().format('YYYY-MM'))

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'monthly-summary', month],
    queryFn: () => getMonthlySummary(month),
  })

  const v = (n: number | undefined): number => n ?? 0

  return (
    <Card
      title={`经营月报 · ${data?.month ?? month}`}
      extra={
        <Space>
          <DatePicker
            picker="month"
            value={month ? dayjs(month) : null}
            onChange={(d) => setMonth(d?.format('YYYY-MM') ?? dayjs().format('YYYY-MM'))}
            allowClear={false}
          />
          <Button
            icon={<DownloadOutlined />}
            onClick={() => downloadReportCsv('/report/monthly-summary', { month }, `monthly_summary_${month}.csv`)}
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
            <Statistic title="完工工单数(张)" value={v(data?.finishedOrderCount)} valueStyle={{ color: '#1668dc', fontWeight: 700 }} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>本期有完工动作的工单</Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="产量(完工数量)" value={fmtQty(v(data?.productionQty))} valueStyle={{ color: '#1668dc', fontWeight: 700 }} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>工单 finish_qty 现值合计</Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="销量(出库数量)" value={fmtQty(v(data?.salesQty))} valueStyle={{ color: '#722ed1', fontWeight: 700 }} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>已审核出库明细数量</Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="销售额(元,不含税)" value={fmtMoney(v(data?.salesAmount))} valueStyle={{ color: '#52c41a', fontWeight: 700 }} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>已审核销售出库</Typography.Text>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="采购额(元,价税合计)" value={fmtMoney(v(data?.purchaseAmount))} valueStyle={{ color: '#fa8c16', fontWeight: 700 }} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>已审核采购入库</Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="毛利(元)" value={fmtMoney(v(data?.grossProfit))} valueStyle={{ color: v(data?.grossProfit) >= 0 ? '#52c41a' : '#ff4d4f', fontWeight: 700 }} />
            <div style={{ marginTop: 4 }}>{data && <SourceTag source={data.revenueSource} />}</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="毛利率" value={`${v(data?.grossMargin).toFixed(2)}%`} valueStyle={{ fontWeight: 700 }} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>毛利 / 营业收入</Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small" loading={isLoading}>
            <Statistic title="费用合计(元)" value={fmtMoney(v(data?.expenseTotal))} valueStyle={{ color: '#ff4d4f', fontWeight: 700 }} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>66xx 损益科目借方发生额</Typography.Text>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} sm={8}>
          <Card size="small" loading={isLoading}>
            <Statistic title="应收余额(元)" value={fmtMoney(v(data?.receivableBalance))} valueStyle={{ color: '#fa8c16' }} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>当前时点快照</Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small" loading={isLoading}>
            <Statistic title="应付余额(元)" value={fmtMoney(v(data?.payableBalance))} valueStyle={{ color: '#13c2c2' }} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>当前时点快照</Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small" loading={isLoading}>
            <Statistic title="库存总值(元)" value={fmtMoney(v(data?.inventoryValue))} valueStyle={{ color: '#1668dc' }} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>加权价/ref_cost 口径（时点）</Typography.Text>
          </Card>
        </Col>
      </Row>

      <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 12 }}>
        口径：期间 [{month} 月初, 次月初)；营收/成本优先取 POSTED 凭证 6001/6401 科目，无凭证降级业务口径；
        应收/应付/库存总值为当前时点快照（无期间余额表）。
      </Typography.Text>
    </Card>
  )
}
