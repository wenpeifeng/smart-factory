import { useState } from 'react'
import { Alert, Button, Card, DatePicker, Space, Tag, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { downloadReportCsv } from '@/api/report'
import { getApAging, getFinanceBookId } from '@/api/finance'
import { fmtMoney } from '@/pages/report/shared'
import { AgingBucketStrip, AgingPartnerTable, agingBucketSumText } from './FinAgingShared'

/**
 * 应付账龄分析（供应商 × 账龄分桶，与应收对称）。
 * 口径同应收：未结算余额 = amount − settled_amount，账龄锚点 = 单据日期。
 */
export default function ApAgingPane() {
  const [asOf, setAsOf] = useState<string>(dayjs().format('YYYY-MM-DD'))

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['finance', 'ap-aging', asOf, getFinanceBookId()],
    queryFn: () => getApAging(asOf),
  })

  return (
    <Card
      title="应付账龄分析"
      extra={
        <Space>
          <DatePicker
            value={asOf ? dayjs(asOf) : null}
            onChange={(d) => setAsOf(d?.format('YYYY-MM-DD') ?? dayjs().format('YYYY-MM-DD'))}
            allowClear={false}
          />
          <Button
            icon={<DownloadOutlined />}
            onClick={() =>
              downloadReportCsv(
                '/finance/reports/ap-aging',
                { asOf, bookId: getFinanceBookId() },
                `ap_aging_${asOf.replace(/-/g, '')}.csv`,
              )
            }
          >
            导出 CSV
          </Button>
        </Space>
      }
    >
      {isError && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`查询失败：${(error as Error)?.message ?? ''}`} />
      )}
      {isLoading && <Typography.Text type="secondary">账龄计算中...</Typography.Text>}

      {data && (
        <Space size={12} style={{ marginBottom: 12 }} wrap>
          <Tag color="cyan">应付未结算总计 {fmtMoney(data.totalAmount)} 元</Tag>
          <Tag>未结清单据 {data.totalDocCount} 张</Tag>
          <Tag color={data.rows?.length ? 'blue' : 'default'}>供应商 {data.rows?.length ?? 0} 家</Tag>
          <Typography.Text type="secondary">{agingBucketSumText(data)}</Typography.Text>
        </Space>
      )}

      {data && <AgingBucketStrip data={data} />}
      {data && <AgingPartnerTable data={data} partnerTitle="供应商" />}

      <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
        口径：未结算余额 = 单据金额 − 已核销；账龄锚点 = 单据日期（缺失回退创建日），asOf
        之后立据的单据不计入；首桶含当日（0-30 天）。
      </Typography.Text>
    </Card>
  )
}
