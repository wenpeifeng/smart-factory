import { useMemo, useState } from 'react'
import { Alert, Button, Card, DatePicker, Space, Table, Tag, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { downloadReportCsv } from '@/api/report'
import { getBalanceSheet, getFinanceBookId } from '@/api/finance'
import type { BalanceSheetRow } from '@/api/finance'
import { fmtMoney } from '@/pages/report/shared'

/** 左右对照行：资产侧行 + 负债权益侧行按序配对（缺位补 null） */
interface PairedRow {
  key: number
  left: BalanceSheetRow | null
  right: BalanceSheetRow | null
}

/**
 * 资产负债表：资产/负债权益 两列对照表（期初+期末两栏）
 * + 勾稽校验标签（资产总计 = 负债合计 + 所有者权益合计，容差 0.01）。
 * 取数：期末科目余额（POSTED 凭证分录聚合），行项目映射 fin_report_mapping 驱动。
 */
export default function BalanceSheetPane() {
  const [period, setPeriod] = useState<string>(dayjs().format('YYYY-MM'))

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['finance', 'balance-sheet', period, getFinanceBookId()],
    queryFn: () => getBalanceSheet(period),
  })

  const pairedRows: PairedRow[] = useMemo(() => {
    const rows = data?.rows ?? []
    const left = rows.filter((r) => r.side === 'ASSET')
    const right = rows.filter((r) => r.side === 'LIABILITY' || r.side === 'EQUITY')
    const size = Math.max(left.length, right.length)
    return Array.from({ length: size }, (_, i) => ({
      key: i,
      left: left[i] ?? null,
      right: right[i] ?? null,
    }))
  }, [data])

  const money = (v: number | null | undefined) => (v == null ? '-' : fmtMoney(v))
  const cellStyle = (row: BalanceSheetRow | null) =>
    row?.total ? { background: '#fafafa', fontWeight: 700 } : undefined

  const columns = [
    {
      title: '资产',
      dataIndex: ['left', 'itemName'],
      render: (_: unknown, r: PairedRow) => (
        <span style={cellStyle(r.left)}>{r.left?.itemName ?? ''}</span>
      ),
    },
    {
      title: '期初余额',
      align: 'right' as const,
      render: (_: unknown, r: PairedRow) => (
        <span style={cellStyle(r.left)}>{r.left ? money(r.left.opening) : ''}</span>
      ),
    },
    {
      title: '期末余额',
      align: 'right' as const,
      render: (_: unknown, r: PairedRow) => (
        <span style={cellStyle(r.left)}>{r.left ? money(r.left.closing) : ''}</span>
      ),
    },
    {
      title: '负债和所有者权益',
      render: (_: unknown, r: PairedRow) => (
        <span style={cellStyle(r.right)}>{r.right?.itemName ?? ''}</span>
      ),
    },
    {
      title: '期初余额 ',
      align: 'right' as const,
      render: (_: unknown, r: PairedRow) => (
        <span style={cellStyle(r.right)}>{r.right ? money(r.right.opening) : ''}</span>
      ),
    },
    {
      title: '期末余额 ',
      align: 'right' as const,
      render: (_: unknown, r: PairedRow) => (
        <span style={cellStyle(r.right)}>{r.right ? money(r.right.closing) : ''}</span>
      ),
    },
  ]

  return (
    <Card
      title={`资产负债表 · ${data?.period ?? period}`}
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
            onClick={() =>
              downloadReportCsv(
                '/finance/reports/balance-sheet',
                { period, bookId: getFinanceBookId() },
                `balance_sheet_${period.replace('-', '')}.csv`,
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
          <Tag color={data.balanced ? 'success' : 'error'}>
            {data.balanced
              ? `勾稽平衡：资产总计 = 负债 + 所有者权益（期末差额 ${fmtMoney(data.diff)}）`
              : `勾稽不平：期末差额 ${fmtMoney(data.diff)}（容差 0.01）`}
          </Tag>
          <Tag color={Math.abs(data.openingDiff) < 0.01 ? 'success' : 'error'}>
            期初差额 {fmtMoney(data.openingDiff)}
          </Tag>
          {data.profitLossNotCarried && (
            <Tag color="orange">损益未结转：当期净额已按现行余额并入未分配利润</Tag>
          )}
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

      <Table<PairedRow>
        size="small"
        loading={isLoading}
        rowKey="key"
        columns={columns}
        dataSource={pairedRows}
        pagination={false}
        bordered
      />

      <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
        取数：POSTED 凭证分录聚合的科目期初/期末余额（期初 = 期间前累计，期末 = 期初 +
        本期净发生）；行项目映射 fin_report_mapping（前缀匹配，如应交税费 2221 含 222101）；存货含生产成本/制造费用（在产品）；减项科目（累计折旧/坏账准备）以负数列示。
      </Typography.Text>
    </Card>
  )
}
