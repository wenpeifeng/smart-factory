import { useState } from 'react'
import { Alert, Button, Card, DatePicker, Space, Table, Tag, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { downloadReportCsv } from '@/api/report'
import { getFinanceBookId, getIncomeStatement } from '@/api/finance'
import type { IncomeStatementRow } from '@/api/finance'
import { fmtMoney } from '@/pages/report/shared'

/**
 * 利润表：本月数 + 本年累计数（科目余额取数，映射驱动）。
 * 默认期间 = 所选自然月；展开「自定义区间」后本月列切换为 [from,to]。
 */
export default function IncomeStatementPane() {
  const [period, setPeriod] = useState<string>(dayjs().format('YYYY-MM'))
  const [range, setRange] = useState<[string, string] | null>(null)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['finance', 'income-statement', period, range, getFinanceBookId()],
    queryFn: () =>
      getIncomeStatement(
        range ? { period, from: range[0], to: range[1] } : { period },
      ),
  })

  const money = (v: number | null | undefined) => (v == null ? '-' : fmtMoney(v))
  const profitColor = (v: number | null | undefined) =>
    v == null || v >= 0 ? '#389e0d' : '#cf1322'

  const columns = [
    {
      title: '项目',
      dataIndex: 'itemName',
      render: (name: string, r: IncomeStatementRow) => (
        <span style={r.total ? { fontWeight: 700 } : undefined}>{name}</span>
      ),
    },
    {
      title: '本月数(元)',
      dataIndex: 'monthAmount',
      align: 'right' as const,
      render: (v: number, r: IncomeStatementRow) => (
        <span style={r.total ? { fontWeight: 700, color: profitColor(v) } : undefined}>{money(v)}</span>
      ),
    },
    {
      title: '本年累计数(元)',
      dataIndex: 'ytdAmount',
      align: 'right' as const,
      render: (v: number, r: IncomeStatementRow) => (
        <span style={r.total ? { fontWeight: 700, color: profitColor(v) } : undefined}>{money(v)}</span>
      ),
    },
    {
      title: '取数科目',
      dataIndex: 'subjectCodes',
      align: 'center' as const,
      render: (codes: string) =>
        codes ? <Typography.Text type="secondary">{codes}</Typography.Text> : null,
    },
  ]

  return (
    <Card
      title={`利润表 · ${data?.period ?? period}`}
      extra={
        <Space>
          <DatePicker
            picker="month"
            value={period ? dayjs(period) : null}
            onChange={(d) => setPeriod(d?.format('YYYY-MM') ?? dayjs().format('YYYY-MM'))}
            allowClear={false}
          />
          <DatePicker.RangePicker
            value={range ? [dayjs(range[0]), dayjs(range[1])] : null}
            onChange={(v) =>
              setRange(v && v[0] && v[1] ? [v[0].format('YYYY-MM-DD'), v[1].format('YYYY-MM-DD')] : null)
            }
            placeholder={['自定义起', '自定义止']}
          />
          <Button
            icon={<DownloadOutlined />}
            onClick={() =>
              downloadReportCsv(
                '/finance/reports/income-statement',
                {
                  period,
                  ...(range ? { from: range[0], to: range[1] } : {}),
                  bookId: getFinanceBookId(),
                },
                `income_statement_${period.replace('-', '')}.csv`,
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

      {data && (
        <Space size={12} style={{ marginBottom: 12 }} wrap>
          <Tag color={data.netProfitMonth >= 0 ? 'success' : 'error'}>
            本月净利润 {fmtMoney(data.netProfitMonth)}
          </Tag>
          <Tag color={data.netProfitYtd >= 0 ? 'success' : 'error'}>
            本年累计净利润 {fmtMoney(data.netProfitYtd)}
          </Tag>
          <Tag>
            本期 {data.from} ~ {data.to}
          </Tag>
          <Tag>
            累计 {data.ytdFrom} ~ {data.ytdTo}
          </Tag>
        </Space>
      )}

      {data?.warnings?.length ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="口径提示"
          description={
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {data.warnings.map((w, i) => (
                <li key={i}>{w}</li>
              ))}
            </ul>
          }
        />
      ) : null}

      <Table<IncomeStatementRow>
        size="small"
        loading={isLoading}
        rowKey="itemName"
        columns={columns}
        dataSource={data?.rows ?? []}
        pagination={false}
        bordered
      />

      <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
        取数：POSTED 凭证分录聚合（收入类 = 贷-借，成本费用类 = 借-贷）；营业利润/利润总额/净利润
        = 上方损益行净额的运行合计；未结转损益同时体现在资产负债表「未分配利润」。
      </Typography.Text>
    </Card>
  )
}
