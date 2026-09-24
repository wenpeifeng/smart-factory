import { useMemo, useState } from 'react'
import { Alert, Button, Card, DatePicker, Select, Space, Table, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { downloadReportCsv, getSalesMargin, type SalesMarginRow } from '@/api/report'
import { fmtMoney, fmtQty } from '@/pages/report/shared'

/** 聚合维度选项 */
const GROUP_OPTIONS = [
  { value: 'material', label: '按物料' },
  { value: 'customer', label: '按客户' },
]

type GroupBy = 'material' | 'customer'

/** 默认查询区间：当月 1 日 ~ 今天 */
function defaultRange(): [Dayjs, Dayjs] {
  const today = dayjs()
  return [today.startOf('month'), today]
}

/**
 * 销售毛利分析：销售额 / 销售成本（actual_cost，历史行回退 ref_cost）/ 毛利 / 毛利率。
 */
export default function MarginPane() {
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null]>(defaultRange())
  const [groupBy, setGroupBy] = useState<GroupBy>('material')

  // 后端 to 为开区间（不含当日）→ 传 end+1 天
  const params = useMemo(
    () => ({
      from: range[0]?.format('YYYY-MM-DD'),
      to: range[1]?.add(1, 'day').format('YYYY-MM-DD'),
      groupBy,
    }),
    [range, groupBy],
  )

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'sales-margin', params],
    queryFn: () => getSalesMargin(params),
  })

  const columns: ColumnsType<SalesMarginRow> = useMemo(
    () => [
      { title: groupBy === 'customer' ? '客户编码' : '物料编码', dataIndex: 'dimCode', key: 'dimCode', width: 140 },
      { title: groupBy === 'customer' ? '客户名称' : '物料名称', dataIndex: 'dimName', key: 'dimName', ellipsis: true },
      { title: '数量', dataIndex: 'qty', key: 'qty', align: 'right', width: 100, render: (v: number) => fmtQty(v) },
      { title: '销售额(元)', dataIndex: 'amount', key: 'amount', align: 'right', width: 120, render: (v: number) => fmtMoney(v) },
      { title: '销售成本(元)', dataIndex: 'costAmount', key: 'costAmount', align: 'right', width: 120, render: (v: number) => fmtMoney(v) },
      {
        title: '毛利(元)',
        dataIndex: 'profitAmount',
        key: 'profitAmount',
        align: 'right',
        width: 120,
        render: (v: number) => <span style={{ color: v >= 0 ? '#52c41a' : '#ff4d4f' }}>{fmtMoney(v)}</span>,
      },
      {
        title: '毛利率',
        dataIndex: 'marginPct',
        key: 'marginPct',
        align: 'right',
        width: 100,
        render: (v: number) => `${v.toFixed(2)}%`,
      },
    ],
    [groupBy],
  )

  const rows = data?.rows ?? []
  const total = data?.total

  return (
    <Card title="销售毛利分析（已审核出库，不含税口径）">
      <Space wrap style={{ marginBottom: 16 }}>
        <DatePicker.RangePicker value={range} onChange={(v) => setRange(v ?? [null, null])} allowEmpty={[true, true]} />
        <Select style={{ width: 120 }} value={groupBy} onChange={(v) => setGroupBy(v as GroupBy)} options={GROUP_OPTIONS} />
        <Button
          icon={<DownloadOutlined />}
          onClick={() => downloadReportCsv('/report/sales/margin', params, `sales_margin_${groupBy}.csv`)}
        >
          导出 CSV
        </Button>
      </Space>

      {isError && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`查询失败：${(error as Error)?.message ?? ''}`} />
      )}

      <Table<SalesMarginRow>
        rowKey="dimCode"
        size="middle"
        loading={isLoading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (t) => `共 ${t} 行` }}
        summary={() =>
          !total ? null : (
            <Table.Summary fixed>
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={2}>
                  <strong>合计</strong>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={2} align="right"><strong>{fmtQty(total.qty)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={3} align="right"><strong>{fmtMoney(total.amount)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={4} align="right"><strong>{fmtMoney(total.costAmount)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={5} align="right"><strong>{fmtMoney(total.profitAmount)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={6} align="right"><strong>{total.marginPct.toFixed(2)}%</strong></Table.Summary.Cell>
              </Table.Summary.Row>
            </Table.Summary>
          )
        }
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        口径：仅统计「已审核」销售出库；销售成本优先取出库明细 actual_cost（CostService 记账值），
        历史行回退 数量 × 物料参考成本(ref_cost)；毛利率 = 毛利 / 销售额 × 100%。
      </Typography.Text>
    </Card>
  )
}
