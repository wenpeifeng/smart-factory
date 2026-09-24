import { useState } from 'react'
import { Alert, Button, Card, DatePicker, Space, Table, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { downloadReportCsv, getInventoryMonthly, type InventoryMonthlyRow } from '@/api/report'
import { fmtMoney, fmtQty } from '@/pages/report/shared'

/**
 * 收发存月报：物料 × 期初/入库/出库/期末（数量 + 金额，金额取 cost_flow 期间汇总）。
 * 守恒关系：期初 + 入库 − 出库 = 期末（全行成立，后端构造保证）。
 */
export default function InventoryMonthlyPane() {
  const [month, setMonth] = useState<string>(dayjs().format('YYYY-MM'))

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'inventory-monthly', month],
    queryFn: () => getInventoryMonthly(month),
  })

  const columns: ColumnsType<InventoryMonthlyRow> = [
    { title: '物料编码', dataIndex: 'materialCode', key: 'materialCode', width: 130 },
    { title: '物料名称', dataIndex: 'materialName', key: 'materialName', ellipsis: true },
    { title: '单位', dataIndex: 'unitName', key: 'unitName', width: 70 },
    { title: '期初数量', dataIndex: 'beginQty', key: 'beginQty', align: 'right', width: 100, render: (v: number) => fmtQty(v) },
    { title: '期初金额(元)', dataIndex: 'beginAmount', key: 'beginAmount', align: 'right', width: 120, render: (v: number) => fmtMoney(v) },
    { title: '入库数量', dataIndex: 'inQty', key: 'inQty', align: 'right', width: 100, render: (v: number) => fmtQty(v) },
    { title: '入库金额(元)', dataIndex: 'inAmount', key: 'inAmount', align: 'right', width: 120, render: (v: number) => fmtMoney(v) },
    { title: '出库数量', dataIndex: 'outQty', key: 'outQty', align: 'right', width: 100, render: (v: number) => fmtQty(v) },
    { title: '出库金额(元)', dataIndex: 'outAmount', key: 'outAmount', align: 'right', width: 120, render: (v: number) => fmtMoney(v) },
    {
      title: '期末数量',
      dataIndex: 'endQty',
      key: 'endQty',
      align: 'right',
      width: 100,
      render: (v: number) => <strong>{fmtQty(v)}</strong>,
    },
    {
      title: '期末金额(元)',
      dataIndex: 'endAmount',
      key: 'endAmount',
      align: 'right',
      width: 120,
      render: (v: number) => <strong>{fmtMoney(v)}</strong>,
    },
  ]

  const rows = data?.rows ?? []
  const total = data?.total

  return (
    <Card
      title={`收发存月报 · ${data?.month ?? month}`}
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
            onClick={() => downloadReportCsv('/report/inventory/monthly', { month }, `inventory_monthly_${month}.csv`)}
          >
            导出 CSV
          </Button>
        </Space>
      }
    >
      {isError && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`查询失败：${(error as Error)?.message ?? ''}`} />
      )}

      <Table<InventoryMonthlyRow>
        rowKey="materialId"
        size="middle"
        loading={isLoading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (t) => `共 ${t} 行` }}
        summary={() =>
          !total ? null : (
            <Table.Summary fixed>
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={3}><strong>合计</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={3} align="right"><strong>{fmtQty(total.beginQty)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={4} align="right"><strong>{fmtMoney(total.beginAmount)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={5} align="right"><strong>{fmtQty(total.inQty)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={6} align="right"><strong>{fmtMoney(total.inAmount)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={7} align="right"><strong>{fmtQty(total.outQty)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={8} align="right"><strong>{fmtMoney(total.outAmount)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={9} align="right"><strong>{fmtQty(total.endQty)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={10} align="right"><strong>{fmtMoney(total.endAmount)}</strong></Table.Summary.Cell>
              </Table.Summary.Row>
            </Table.Summary>
          )
        }
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        口径：数量与金额均取存货成本明细流水（cost_flow）—— 期初 = 期间前累计，入库/出库 = 当月正/负方向流水，
        期末 = 期初 + 入库 − 出库（守恒关系全行成立）；仅列出有流水记录的物料。
      </Typography.Text>
    </Card>
  )
}
