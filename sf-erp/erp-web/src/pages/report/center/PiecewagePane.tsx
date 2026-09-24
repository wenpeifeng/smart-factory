import { useState } from 'react'
import { Alert, Button, Card, DatePicker, Space, Table, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { downloadReportCsv, getPiecewage, type PiecewageRow } from '@/api/report'
import { fmtMoney, fmtQty } from '@/pages/report/shared'

/**
 * 计件工资汇总：员工 × 工序 的计件产量 / 工资 / 工单数（hr_piece_wage_detail）。
 */
export default function PiecewagePane() {
  const [period, setPeriod] = useState<string>(dayjs().format('YYYY-MM'))

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'piecewage', period],
    queryFn: () => getPiecewage(period),
  })

  const columns: ColumnsType<PiecewageRow> = [
    { title: '工号', dataIndex: 'empNo', key: 'empNo', width: 140 },
    { title: '员工', dataIndex: 'employeeName', key: 'employeeName', width: 120 },
    { title: '工序', dataIndex: 'processName', key: 'processName', ellipsis: true },
    { title: '计件产量', dataIndex: 'qty', key: 'qty', align: 'right', width: 110, render: (v: number) => fmtQty(v) },
    {
      title: '计件工资(元)',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right',
      width: 130,
      render: (v: number) => <strong>{fmtMoney(v)}</strong>,
    },
    { title: '工单数', dataIndex: 'woCount', key: 'woCount', align: 'right', width: 90 },
  ]

  const rows = data?.rows ?? []
  const total = data?.total

  return (
    <Card
      title={`计件工资汇总 · ${data?.period ?? period}`}
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
            onClick={() => downloadReportCsv('/report/hr/piecewage', { period }, `piecewage_${period}.csv`)}
          >
            导出 CSV
          </Button>
        </Space>
      }
    >
      {isError && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`查询失败：${(error as Error)?.message ?? ''}`} />
      )}

      <Table<PiecewageRow>
        rowKey={(r) => `${r.employeeId}-${r.processName}`}
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
                <Table.Summary.Cell index={3} align="right"><strong>{fmtQty(total.qty)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={4} align="right"><strong>{fmtMoney(total.amount)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={5} align="right"><strong>-</strong></Table.Summary.Cell>
              </Table.Summary.Row>
            </Table.Summary>
          )
        }
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        口径：计件工资表（hr_piece_wage）期间明细按 员工 × 工序 汇总；期间无工资表数据返回空列表
        （需先在人事模块生成该期间计件工资）。
      </Typography.Text>
    </Card>
  )
}
