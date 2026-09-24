import { useMemo, useState } from 'react'
import { Alert, Button, Card, DatePicker, Space, Table, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { downloadReportCsv, getMaterialLoss, type MaterialLossRow } from '@/api/report'
import { fmtQty } from '@/pages/report/shared'

/** 默认查询区间：近 180 天 */
function defaultRange(): [Dayjs, Dayjs] {
  const today = dayjs()
  return [today.subtract(180, 'day'), today]
}

/**
 * 材料损耗分析：工单 BOM 应耗 vs 实际领料（超耗量/超耗率）。
 */
export default function MaterialLossPane() {
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null]>(defaultRange())

  const params = useMemo(
    () => ({
      from: range[0]?.format('YYYY-MM-DD'),
      to: range[1]?.add(1, 'day').format('YYYY-MM-DD'),
    }),
    [range],
  )

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['report', 'material-loss', params],
    queryFn: () => getMaterialLoss(params),
  })

  const columns: ColumnsType<MaterialLossRow> = [
    { title: '工单号', dataIndex: 'orderNo', key: 'orderNo', width: 150 },
    { title: '物料编码', dataIndex: 'materialCode', key: 'materialCode', width: 120 },
    { title: '物料名称', dataIndex: 'materialName', key: 'materialName', ellipsis: true },
    { title: 'BOM 应耗', dataIndex: 'shouldQty', key: 'shouldQty', align: 'right', width: 110, render: (v: number) => fmtQty(v) },
    { title: '实际领料', dataIndex: 'actualQty', key: 'actualQty', align: 'right', width: 110, render: (v: number) => fmtQty(v) },
    {
      title: '超耗量',
      dataIndex: 'lossQty',
      key: 'lossQty',
      align: 'right',
      width: 110,
      render: (v: number) => (
        <span style={{ color: v > 0 ? '#ff4d4f' : v < 0 ? '#52c41a' : undefined }}>{fmtQty(v)}</span>
      ),
    },
    {
      title: '超耗率',
      dataIndex: 'lossRate',
      key: 'lossRate',
      align: 'right',
      width: 100,
      render: (v: number) => (
        <span style={{ color: v > 0 ? '#ff4d4f' : v < 0 ? '#52c41a' : undefined }}>{v.toFixed(2)}%</span>
      ),
    },
  ]

  const rows = data?.rows ?? []
  const total = data?.total

  return (
    <Card
      title="材料损耗分析（超耗红 / 节约绿）"
      extra={
        <Button
          icon={<DownloadOutlined />}
          onClick={() => downloadReportCsv('/report/production/material-loss', params, 'material_loss_all.csv')}
        >
          导出 CSV
        </Button>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <DatePicker.RangePicker value={range} onChange={(v) => setRange(v ?? [null, null])} allowEmpty={[true, true]} />
      </Space>

      {isError && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`查询失败：${(error as Error)?.message ?? ''}`} />
      )}

      <Table<MaterialLossRow>
        rowKey={(r) => `${r.workOrderId}-${r.materialId}`}
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
                <Table.Summary.Cell index={3} align="right"><strong>{fmtQty(total.shouldQty)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={4} align="right"><strong>{fmtQty(total.actualQty)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={5} align="right"><strong>{fmtQty(total.lossQty)}</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={6} align="right"><strong>{total.lossRate.toFixed(2)}%</strong></Table.Summary.Cell>
              </Table.Summary.Row>
            </Table.Summary>
          )
        }
      />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        口径：应耗 = 已审核领料单明细 should_qty（BOM 应耗 × 领料倍数）合计，实耗 = actual_qty 合计；
        超耗率 = (实耗 − 应耗) / 应耗 × 100%（负数为节约）；应耗为 0 / 无领料明细的工单不参与统计。
      </Typography.Text>
    </Card>
  )
}
