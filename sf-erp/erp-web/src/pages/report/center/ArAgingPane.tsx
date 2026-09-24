import { useState } from 'react'
import { Alert, Button, Card, DatePicker, Space, Tag, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { downloadReportCsv } from '@/api/report'
import { getArAging, getFinanceBookId } from '@/api/finance'
import { fmtMoney } from '@/pages/report/shared'
import { AgingBucketStrip, AgingPartnerTable, agingBucketSumText } from './FinAgingShared'

/**
 * 应收账龄分析（客户 × 账龄分桶）。
 * 口径：未结算余额 = amount − settled_amount；账龄锚点 = 单据日期
 * （biz_date，缺失回退创建日）；桶 1-30 / 31-60 / 61-90 / 91-180 / 180+ 天。
 */
export default function ArAgingPane() {
  const [asOf, setAsOf] = useState<string>(dayjs().format('YYYY-MM-DD'))

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['finance', 'ar-aging', asOf, getFinanceBookId()],
    queryFn: () => getArAging(asOf),
  })

  return (
    <Card
      title="应收账龄分析"
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
                '/finance/reports/ar-aging',
                { asOf, bookId: getFinanceBookId() },
                `ar_aging_${asOf.replace(/-/g, '')}.csv`,
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
          <Tag color="orange">应收未结算总计 {fmtMoney(data.totalAmount)} 元</Tag>
          <Tag>未结清单据 {data.totalDocCount} 张</Tag>
          <Tag color={data.rows?.length ? 'blue' : 'default'}>客户 {data.rows?.length ?? 0} 家</Tag>
          <Typography.Text type="secondary">{agingBucketSumText(data)}</Typography.Text>
        </Space>
      )}

      {data && <AgingBucketStrip data={data} />}
      {data && <AgingPartnerTable data={data} partnerTitle="客户" />}

      <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
        口径：未结算余额 = 单据金额 − 已核销；账龄锚点 = 单据日期（缺失回退创建日），asOf
        之后立据的单据不计入；首桶含当日（0-30 天）。分桶色阶随金额加深，桶越老基色越红。
      </Typography.Text>
    </Card>
  )
}
